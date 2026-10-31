"""Occupancy prediction core — pure stdlib, no web/DB code in here.

The formula deliberately mirrors the Java fallback in
backend/.../service/PredictionService.java (javaFallback) line for line,
so the Python service and the fallback agree on the same data:

  * CANCELLED reservations are excluded before this module is called.
  * distinctDays  = number of distinct start dates in the history.
  * active(h)     = reservations whose [start, end) overlaps hour h on
                    the reservation's OWN start date
                    (start < dayHourEnd and end > dayHourStart).
  * pct(h)        = min(100, (active(h) / distinctDays) / totalSlots * 100),
                    rounded half-up to 1 decimal (Java Math.round style).
  * No history or zero slots -> flat 10.0% for every hour.
  * Peak          = first hour with the maximum pct.
"""

import math
from datetime import datetime, timedelta

FMT = "%Y-%m-%d %H:%M:%S"


def _round_half_up_1dp(x: float) -> float:
    return math.floor(x * 10.0 + 0.5) / 10.0


def compute_hourly_predictions(reservations, total_slots):
    """reservations: list of (start: datetime, end: datetime), CANCELLED already removed.

    Returns (hourly, peak_hour, peak_pct) where hourly is a list of 24 dicts
    {"hour": int, "predictedOccupancyPercent": float} for hours 0..23.
    """
    hourly = []
    peak_hour, peak_pct = 0, 0.0

    if total_slots == 0 or not reservations:
        hourly = [{"hour": h, "predictedOccupancyPercent": 10.0} for h in range(24)]
        return hourly, 0, 10.0

    distinct_days = len({r[0].date() for r in reservations}) or 1

    for hour in range(24):
        active = 0
        for start, end in reservations:
            day_hour_start = start.replace(hour=hour, minute=0, second=0, microsecond=0)
            day_hour_end = day_hour_start + timedelta(hours=1)
            if start < day_hour_end and end > day_hour_start:
                active += 1
        pct = (active / distinct_days) / total_slots * 100.0
        pct = float(min(100.0, _round_half_up_1dp(pct)))
        hourly.append({"hour": hour, "predictedOccupancyPercent": pct})
        if pct > peak_pct:
            peak_pct, peak_hour = pct, hour

    return hourly, peak_hour, float(peak_pct)


def build_response(location_id, location_name, target_date, reservations, total_slots):
    """Assemble the exact JSON shape of the Java PredictionDtos.PredictionResponse."""
    hourly, peak_hour, peak_pct = compute_hourly_predictions(reservations, total_slots)
    return {
        "locationId": int(location_id),
        "locationName": location_name,
        "date": target_date.isoformat(),
        "peakHour": int(peak_hour),
        "peakOccupancyPercent": float(peak_pct),
        "hourlyPredictions": hourly,
    }


def parse_dt(value):
    """Parse 'YYYY-MM-DD HH:MM:SS' (CSV / MySQL DATETIME) into a datetime."""
    if isinstance(value, datetime):
        return value
    return datetime.strptime(str(value), FMT)
