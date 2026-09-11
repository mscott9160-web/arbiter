from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from app.heuristics import CLASSIFIER_VERSION, classify_prompt

app = FastAPI(title="Arbiter Classifier Service", version="0.1.0")


class ClassifyRequest(BaseModel):
    prompt: str = Field(min_length=1)


class ClassifyResponse(BaseModel):
    complexity: float
    task_class: str
    classifier_version: str
    uncertainty_band: bool
    route_up: bool
    features: dict[str, float]


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "classifier-svc"}


@app.post("/classify", response_model=ClassifyResponse)
def classify(request: ClassifyRequest) -> ClassifyResponse:
    try:
        result = classify_prompt(request.prompt)
    except ValueError as error:
        raise HTTPException(status_code=422, detail=str(error)) from error
    return ClassifyResponse(
        complexity=result.complexity,
        task_class=result.task_class,
        classifier_version=CLASSIFIER_VERSION,
        uncertainty_band=result.uncertainty_band,
        route_up=result.uncertainty_band,
        features=result.features,
    )
