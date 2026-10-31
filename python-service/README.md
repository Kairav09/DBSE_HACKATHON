# Python Prediction Service — Phase 4

A **supporting component only**, per the CODESPARK rules. It never replaces Java
backend functionality, owns no application data, and performs no writes.
The Spring Boot backend calls it; the browser never does.

- **Framework:** FastAPI + uvicorn (the framework named in
  [`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md); installs cleanly and
  gives typed/validated query params for free).
- **No pandas:** the occupancy math is simple grouping/averaging, implemented
  in pure stdlib ([`prediction.py`](prediction.py)) so installs stay light.
- **DB driver:** PyMySQL (read-only queries against the backend's MySQL).

## How the Java backend reaches it

Backend config (`backend/src/main/resources/application.yml`):

```yaml
prediction:
  service:
    url: ${PREDICTION_SERVICE_URL:http://localhost:8000}
```

So with no configuration, the backend already expects this service at
**`http://localhost:8000`** — just run it on port 8000. To point the backend
elsewhere, set the `PREDICTION_SERVICE_URL` env var when starting the backend.

Flow: `GET /api/predictions/occupancy` (backend, :8080) →
`GET /predict/occupancy?locationId={id}&date={YYYY-MM-DD}` (this service, :8000).
The backend reads only `hourlyPredictions`, `peakHour` and
`peakOccupancyPercent` from the response and fills in `locationId` /
`locationName` / `date` itself. If this service is down or answers non-200,
the backend falls back to its own in-Java calculation — the formula here
mirrors that fallback exactly, so both agree on the same data.

## Endpoints

| Method | Path | Notes |
|---|---|---|
| GET | `/health` | Returns `{"status": "ok"}` |
| GET | `/predict/occupancy?locationId={id}&date={YYYY-MM-DD}` | `date` optional (defaults to today). 404 for an unknown location. |

Response shape — field-for-field the Java `PredictionDtos.PredictionResponse`
(24 hourly entries, hours 0–23; no extra fields):

```json
{
  "locationId": 1,
  "locationName": "KL University Main Gate Parking",
  "date": "2026-10-30",
  "peakHour": 18,
  "peakOccupancyPercent": 88.8,
  "hourlyPredictions": [
    { "hour": 8,  "predictedOccupancyPercent": 55.8 },
    { "hour": 12, "predictedOccupancyPercent": 68.3 },
    { "hour": 18, "predictedOccupancyPercent": 88.8 }
  ]
}
```
(hourly array abbreviated above — the real response carries all 24 hours.)

## How the prediction is computed

Same definition as the Java fallback:

1. Load the location's historical reservations, **excluding `CANCELLED`**.
2. `distinctDays` = distinct reservation start dates in that history.
3. For each hour `h`: `active(h)` = reservations overlapping hour `h` on
   their own start date; `pct(h) = (active(h) / distinctDays) / totalSlots × 100`,
   capped at 100, rounded to 1 decimal.
4. `peakHour` / `peakOccupancyPercent` = first hour with the maximum pct.
5. No history (or 0 slots) → flat 10.0% for every hour, rather than failing.

## Where the data comes from

1. **MySQL first (read-only):** `parking_locations` (name),
   `parking_slots` (count for the location), and `reservations` joined via
   `parking_slots` for that location with `status <> 'CANCELLED'`.
2. **Sample CSV fallback:** if MySQL is unreachable, the service computes
   from the bundled [`sample_history.csv`](sample_history.csv) instead, so
   it works standalone for demo/testing. The sample follows the
   `database/seed.sql` pattern over 14 days — morning rush 08:00–09:00,
   midday peak 12:00, evening peak ~85% at 18:00, quiet nights — and
   includes some `CANCELLED` rows to prove they are excluded.
   Regenerate it with `python generate_sample_data.py`.
   Which source served a request is printed to stdout, e.g.
   `[prediction] source=sample-csv locationId=1 reservations=1881 totalSlots=16`
   (never added to the response body — the Java DTO has no such field).

## Install + run

```bash
cd python-service
python3 -m venv .venv
source .venv/bin/activate        # Windows: .venv\Scripts\activate
pip install -r requirements.txt
uvicorn main:app --port 8000
```

## Environment variables

| Var | Default | Meaning |
|---|---|---|
| `DB_HOST` | `localhost` | MySQL host |
| `DB_PORT` | `3306` | MySQL port |
| `DB_NAME` | `smart_parking` | MySQL database |
| `DB_USERNAME` | `root` | MySQL user (read-only access is enough) |
| `DB_PASSWORD` | *(empty)* | MySQL password |
| `SAMPLE_CSV` | `./sample_history.csv` | Fallback dataset path |

## Try it

```bash
curl http://localhost:8000/health
curl "http://localhost:8000/predict/occupancy?locationId=1&date=2026-10-30"
```

Verified sample response (MySQL not running, so served from the sample CSV):

```json
{"locationId":1,"locationName":"KL University Main Gate Parking","date":"2026-10-30","peakHour":18,"peakOccupancyPercent":88.8,"hourlyPredictions":[{"hour":0,"predictedOccupancyPercent":1.8},{"hour":1,"predictedOccupancyPercent":0.4},{"hour":2,"predictedOccupancyPercent":0.4},{"hour":3,"predictedOccupancyPercent":1.3},{"hour":4,"predictedOccupancyPercent":0.4},{"hour":5,"predictedOccupancyPercent":7.6},{"hour":6,"predictedOccupancyPercent":12.5},{"hour":7,"predictedOccupancyPercent":22.8},{"hour":8,"predictedOccupancyPercent":55.8},{"hour":9,"predictedOccupancyPercent":62.9},{"hour":10,"predictedOccupancyPercent":50.4},{"hour":11,"predictedOccupancyPercent":43.3},{"hour":12,"predictedOccupancyPercent":68.3},{"hour":13,"predictedOccupancyPercent":55.4},{"hour":14,"predictedOccupancyPercent":35.7},{"hour":15,"predictedOccupancyPercent":37.5},{"hour":16,"predictedOccupancyPercent":52.2},{"hour":17,"predictedOccupancyPercent":75.0},{"hour":18,"predictedOccupancyPercent":88.8},{"hour":19,"predictedOccupancyPercent":77.7},{"hour":20,"predictedOccupancyPercent":42.9},{"hour":21,"predictedOccupancyPercent":25.0},{"hour":22,"predictedOccupancyPercent":13.4},{"hour":23,"predictedOccupancyPercent":8.0}]}
```

## Tests

```bash
python test_prediction.py            # plain script, no extra deps
pip install pytest httpx             # optional, for the pytest/TestClient run
pytest test_prediction.py -q         # 4 tests
```

Covers: hand-computed core math (incl. the empty-history flat profile),
evening-peak assertion on the sample data, exact response-shape match
against the Java DTO, and the HTTP endpoints.
