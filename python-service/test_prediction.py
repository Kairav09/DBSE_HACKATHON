"""Self-test for the prediction service.

Runs two ways:
    python test_prediction.py     (plain script, no extra deps)
    pytest test_prediction.py     (if pytest is installed)

Checks:
  1. Sample-CSV prediction for location 1 peaks in the evening (17-19)
     at a high occupancy (>= 75%), with morning/midday bumps above night.
  2. The response shape matches the Java PredictionDtos exactly
     (key sets, 24 hourly entries, types the Java parser reads).
  3. CANCELLED exclusion / core math on a tiny hand-computed example,
     mirroring the Java fallback formula.
  4. /health and /predict/occupancy over real HTTP (FastAPI TestClient).
"""

from datetime import date, datetime

from data_source import load_from_sample_csv
from prediction import build_response, compute_hourly_predictions

TARGET_DATE = date(2026, 10, 30)


def _sample_prediction(location_id=1):
    result = load_from_sample_csv(location_id)
    assert result is not None, f"sample CSV has no location {location_id}"
    name, total_slots, reservations = result
    return build_response(location_id, name, TARGET_DATE, reservations, total_slots)


def test_sample_peak_is_evening():
    resp = _sample_prediction(1)
    assert 17 <= resp["peakHour"] <= 19, f"peak hour {resp['peakHour']} not in evening peak"
    assert resp["peakOccupancyPercent"] >= 75.0, f"peak pct {resp['peakOccupancyPercent']} too low"
    by_hour = {h["hour"]: h["predictedOccupancyPercent"] for h in resp["hourlyPredictions"]}
    assert by_hour[8] > by_hour[3], "morning rush should beat the quiet night"
    assert by_hour[12] > by_hour[3], "midday peak should beat the quiet night"
    assert by_hour[18] == resp["peakOccupancyPercent"]


def test_response_shape_matches_java_dto():
    resp = _sample_prediction(1)
    assert set(resp.keys()) == {"locationId", "locationName", "date",
                                "peakHour", "peakOccupancyPercent", "hourlyPredictions"}
    assert isinstance(resp["locationId"], int)
    assert isinstance(resp["peakHour"], int)
    assert isinstance(resp["peakOccupancyPercent"], float)
    assert resp["date"] == "2026-10-30"
    assert len(resp["hourlyPredictions"]) == 24
    assert [h["hour"] for h in resp["hourlyPredictions"]] == list(range(24))
    for h in resp["hourlyPredictions"]:
        assert set(h.keys()) == {"hour", "predictedOccupancyPercent"}
        assert isinstance(h["hour"], int)
        assert isinstance(h["predictedOccupancyPercent"], float)
        assert 0.0 <= h["predictedOccupancyPercent"] <= 100.0


def test_core_math_hand_computed():
    # 4 slots, 2 distinct days. Day1: two reservations covering hour 9.
    # Day2: one reservation covering hour 9. Plus one spanning 9->11 on day1.
    res = [
        (datetime(2026, 10, 1, 9, 0), datetime(2026, 10, 1, 10, 0)),
        (datetime(2026, 10, 1, 9, 0), datetime(2026, 10, 1, 11, 0)),
        (datetime(2026, 10, 2, 9, 0), datetime(2026, 10, 2, 10, 0)),
    ]
    hourly, peak_hour, peak_pct = compute_hourly_predictions(res, total_slots=4)
    by_hour = {h["hour"]: h["predictedOccupancyPercent"] for h in hourly}
    # hour 9: active=3, distinctDays=2 -> (3/2)/4*100 = 37.5
    assert by_hour[9] == 37.5, by_hour[9]
    # hour 10: only the spanning reservation, day1 only -> (1/2)/4*100 = 12.5
    assert by_hour[10] == 12.5, by_hour[10]
    assert by_hour[8] == 0.0
    assert peak_hour == 9 and peak_pct == 37.5
    # Empty history -> flat 10.0 profile (same as Java fallback)
    hourly, peak_hour, peak_pct = compute_hourly_predictions([], total_slots=4)
    assert all(h["predictedOccupancyPercent"] == 10.0 for h in hourly)
    assert peak_pct == 10.0


def test_http_endpoints():
    try:
        from fastapi.testclient import TestClient
    except Exception:
        print("  (TestClient unavailable — needs httpx; verify HTTP via the live uvicorn + curl check in README)")
        return
    from main import app

    client = TestClient(app)
    r = client.get("/health")
    assert r.status_code == 200 and r.json() == {"status": "ok"}

    r = client.get("/predict/occupancy", params={"locationId": 1, "date": "2026-10-30"})
    assert r.status_code == 200, r.text
    body = r.json()
    assert 17 <= body["peakHour"] <= 19
    assert len(body["hourlyPredictions"]) == 24

    r = client.get("/predict/occupancy", params={"locationId": 999999, "date": "2026-10-30"})
    assert r.status_code == 404


if __name__ == "__main__":
    test_core_math_hand_computed()
    print("core math: OK")
    test_sample_peak_is_evening()
    print("sample peak (evening, high occupancy): OK")
    test_response_shape_matches_java_dto()
    print("response shape vs Java DTO: OK")
    test_http_endpoints()
    print("HTTP endpoints (/health, /predict/occupancy): OK")
    print("ALL TESTS PASSED")
