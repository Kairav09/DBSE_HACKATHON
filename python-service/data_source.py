"""Data access for the prediction service.

Primary source: the same MySQL database the Spring Boot backend owns
(read-only queries; this service never writes). Connection comes from
env vars DB_HOST / DB_PORT / DB_NAME / DB_USERNAME / DB_PASSWORD.

Fallback source: bundled sample_history.csv (see generate_sample_data.py),
used whenever MySQL is unreachable so the service still works standalone
for demo/testing. Which source served a request is logged to stdout —
the response body never carries extra fields, because it must match the
Java PredictionDtos.PredictionResponse shape exactly.

Both sources return (location_name, total_slots, reservations) with
reservations = list of (start, end) datetimes, CANCELLED excluded —
the DB query filters in SQL (status <> 'CANCELLED', same as the Java
repository's findByLocationIdAndStatusNot), the CSV path filters here.
"""

import csv
import os
from pathlib import Path

from prediction import parse_dt

SAMPLE_CSV = Path(os.environ.get("SAMPLE_CSV", Path(__file__).with_name("sample_history.csv")))


def _db_config():
    return {
        "host": os.environ.get("DB_HOST", "localhost"),
        "port": int(os.environ.get("DB_PORT", "3306")),
        "database": os.environ.get("DB_NAME", "smart_parking"),
        "user": os.environ.get("DB_USERNAME", "root"),
        "password": os.environ.get("DB_PASSWORD", ""),
        "connect_timeout": 2,
        "read_timeout": 5,
    }


def load_from_mysql(location_id):
    """Returns (location_name, total_slots, reservations) or None if the
    location does not exist. Raises on connection/query failure."""
    import pymysql  # imported lazily so the CSV fallback works even without the driver

    conn = pymysql.connect(**_db_config())
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT name FROM parking_locations WHERE id = %s", (location_id,))
            row = cur.fetchone()
            if row is None:
                return None
            location_name = row[0]
            # Same count the Java fallback uses: ParkingSlotRepository.countByLocationId
            cur.execute("SELECT COUNT(*) FROM parking_slots WHERE location_id = %s", (location_id,))
            total_slots = int(cur.fetchone()[0])
            cur.execute(
                """
                SELECT r.start_time, r.end_time
                FROM reservations r
                JOIN parking_slots s ON r.slot_id = s.id
                WHERE s.location_id = %s AND r.status <> 'CANCELLED'
                """,
                (location_id,),
            )
            reservations = [(parse_dt(start), parse_dt(end)) for start, end in cur.fetchall()]
        return location_name, total_slots, reservations
    finally:
        conn.close()


def load_from_sample_csv(location_id):
    """Returns (location_name, total_slots, reservations) or None if the
    sample data has no such location."""
    if not SAMPLE_CSV.exists():
        return None
    location_name, total_slots, reservations = None, 0, []
    with SAMPLE_CSV.open(newline="") as f:
        for row in csv.DictReader(f):
            if int(row["location_id"]) != location_id:
                continue
            location_name = row["location_name"]
            total_slots = int(row["total_slots"])
            if row["status"] == "CANCELLED":
                continue  # excluded from occupancy math, same as the DB path
            reservations.append((parse_dt(row["start_time"]), parse_dt(row["end_time"])))
    if location_name is None:
        return None
    return location_name, total_slots, reservations


def load_history(location_id):
    """Try MySQL first, fall back to the sample CSV.

    Returns (source, location_name, total_slots, reservations);
    source is 'mysql' or 'sample-csv'. Returns (source, None, 0, []) when
    the location is unknown to the source that answered.
    """
    try:
        result = load_from_mysql(location_id)
        if result is None:
            print(f"[prediction] source=mysql locationId={location_id} -> location not found", flush=True)
            return "mysql", None, 0, []
        print(f"[prediction] source=mysql locationId={location_id} "
              f"reservations={len(result[2])} totalSlots={result[1]}", flush=True)
        return "mysql", *result
    except Exception as exc:  # DB unreachable / driver missing -> standalone fallback
        print(f"[prediction] MySQL unavailable ({exc}); falling back to sample CSV", flush=True)

    result = load_from_sample_csv(location_id)
    if result is None:
        print(f"[prediction] source=sample-csv locationId={location_id} -> location not found", flush=True)
        return "sample-csv", None, 0, []
    print(f"[prediction] source=sample-csv locationId={location_id} "
          f"reservations={len(result[2])} totalSlots={result[1]}", flush=True)
    return "sample-csv", *result
