# Classifier service

FastAPI sidecar for the first-stage Arbiter complexity cascade.

The current implementation is a deterministic heuristic baseline. It records the classifier version, feature values, task class, complexity score, and uncertainty-band route-up decision. It does not claim ONNX model quality or replace the planned capability-gap training pipeline.

Run locally:

```powershell
python -m pip install -r requirements.txt
uvicorn app.main:app --reload --port 8001
```

Endpoints:

- `GET /health`
- `POST /classify` with `{ "prompt": "..." }`
