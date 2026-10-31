"""Smart Parking — Python Occupancy Prediction Service (Phase 4).

Supporting component only: it is called exclusively by the Java Spring
Boot backend (PredictionService, prediction.service.url, default
http://localhost:8000) — the browser never calls it directly.

    GET /health
    GET /predict/occupancy?locationId={id}&date={YYYY-MM-DD}

The /predict/occupancy response matches the Java
PredictionDtos.PredictionResponse field-for-field:
    locationId, locationName, date, peakHour, peakOccupancyPercent,
    hourlyPredictions[{hour, predictedOccupancyPercent}]   (24 entries, 0..23)

Run:  uvicorn main:app --port 8000
"""

from datetime import date as date_type
from typing import Optional

from fastapi import FastAPI, HTTPException, Query

from data_source import load_history
from prediction import build_response

app = FastAPI(title="Smart Parking Prediction Service", version="1.0.0")


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/predict/occupancy")
def predict_occupancy(locationId: int = Query(...),
                      date: Optional[date_type] = Query(None)):
    target_date = date or date_type.today()
    _source, location_name, total_slots, reservations = load_history(locationId)
    if location_name is None:
        # Non-200 on purpose: the Java side treats it as "Python unavailable"
        # and falls back to its own in-Java calculation.
        raise HTTPException(status_code=404, detail=f"Location {locationId} not found")
    return build_response(locationId, location_name, target_date, reservations, total_slots)
