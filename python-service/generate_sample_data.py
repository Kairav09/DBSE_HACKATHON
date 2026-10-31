"""Generate sample_history.csv — bundled sample reservation history.

Pattern derived from database/seed.sql: the seed spreads COMPLETED
reservations over the past 7 days at 08:00, 09:00, 12:00, 17:00 and
18:00 starts, with the evening block the busiest. This sample expands
that pattern to 14 days of 1-hour reservation blocks whose per-hour
active counts follow a realistic parking day:

  quiet night -> morning rush (08:00-09:00) -> midday peak (12:00)
  -> evening peak, the highest of the day (~85% at 18:00) -> wind-down.

The prediction code reads this CSV only when MySQL is unreachable.
A few CANCELLED rows are included on purpose: the occupancy math must
exclude them (same rule as the Java fallback / DB query).

Run:  python generate_sample_data.py
Output: sample_history.csv next to this file.
"""

import csv
import random
from datetime import date, timedelta
from pathlib import Path

OUT = Path(__file__).with_name("sample_history.csv")

DAYS = 14
FIRST_DAY = date(2026, 10, 16)  # 14 consecutive days: 2026-10-16 .. 2026-10-29

# Target average occupancy % per hour of day (index = hour 0..23).
LOC1_PROFILE = [2, 1, 1, 1, 2, 5, 12, 28, 55, 62, 48, 42,
                68, 58, 40, 38, 52, 78, 85, 72, 45, 28, 15, 6]
LOC2_PROFILE = [round(p * 0.85, 1) for p in LOC1_PROFILE]  # mall lot: same shape, quieter

LOCATIONS = [
    (1, "KL University Main Gate Parking", 16, LOC1_PROFILE),
    (2, "City Center Mall Parking", 16, LOC2_PROFILE),
]

STATUSES = ["COMPLETED"] * 8 + ["OCCUPIED", "RESERVED"]


def counts_for_day(profile, total_slots, rng):
    """Per-hour active-reservation counts for one day, jittered around the profile."""
    counts = []
    for pct in profile:
        base = pct / 100.0 * total_slots
        count = int(round(base + rng.choice([-1, 0, 0, 0, 1])))
        counts.append(max(0, min(total_slots, count)))
    # Guarantee the evening peak shape every day: 18:00 is the max.
    counts[18] = max(counts[18], counts[17] + 1, counts[19] + 1)
    counts[18] = min(total_slots, counts[18])
    return counts


def main():
    rng = random.Random(42)
    rows = []
    for loc_id, loc_name, total_slots, profile in LOCATIONS:
        for day_offset in range(DAYS):
            day = FIRST_DAY + timedelta(days=day_offset)
            for hour, count in enumerate(counts_for_day(profile, total_slots, rng)):
                for i in range(count):
                    start = f"{day.isoformat()} {hour:02d}:00:00"
                    end_hour = hour + 1
                    end = f"{day.isoformat()} {end_hour:02d}:00:00" if end_hour < 24 \
                        else f"{(day + timedelta(days=1)).isoformat()} 00:00:00"
                    rows.append({
                        "location_id": loc_id,
                        "location_name": loc_name,
                        "total_slots": total_slots,
                        "start_time": start,
                        "end_time": end,
                        "status": rng.choice(STATUSES),
                    })
            # A couple of CANCELLED bookings at peak hours — must be ignored by the math.
            for hour in (12, 18):
                rows.append({
                    "location_id": loc_id,
                    "location_name": loc_name,
                    "total_slots": total_slots,
                    "start_time": f"{day.isoformat()} {hour:02d}:00:00",
                    "end_time": f"{day.isoformat()} {hour + 1:02d}:00:00",
                    "status": "CANCELLED",
                })
    with OUT.open("w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["location_id", "location_name", "total_slots",
                                               "start_time", "end_time", "status"])
        writer.writeheader()
        writer.writerows(rows)
    print(f"Wrote {len(rows)} rows to {OUT}")


if __name__ == "__main__":
    main()
