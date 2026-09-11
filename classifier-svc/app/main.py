from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
import hashlib
import math

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


class EmbedRequest(BaseModel):
    text: str = Field(min_length=1)


class EmbedResponse(BaseModel):
    embedding: list[float]
    embedding_version: str


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


@app.post("/embed", response_model=EmbedResponse)
def embed(request: EmbedRequest) -> EmbedResponse:
    # Deterministic demo vector only; production semantic caching requires a measured embedding model.
    values = [0.0] * 64
    for token in request.text.lower().split():
        digest = hashlib.sha256(token.encode("utf-8")).digest()
        index = int.from_bytes(digest[:2], "big") % len(values)
        values[index] += 1.0
    norm = math.sqrt(sum(value * value for value in values))
    if norm:
        values = [value / norm for value in values]
    return EmbedResponse(embedding=values, embedding_version="demo-hash-64-2026.09.11")
