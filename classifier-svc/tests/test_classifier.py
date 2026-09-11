from fastapi.testclient import TestClient

from app.heuristics import classify_prompt
from app.main import app


client = TestClient(app)


def test_code_prompt_is_classified_as_code_execution():
    result = classify_prompt("Implement and debug this Python function.")

    assert result.task_class == "code_execution"
    assert 0.0 <= result.complexity <= 1.0


def test_summary_prompt_is_classified_as_summarization():
    result = classify_prompt("Summarize the key points from this document.")

    assert result.task_class == "summarize"


def test_uncertainty_band_routes_up():
    result = classify_prompt("Explain and compare the design options? " + "word " * 370)

    assert 0.45 < result.complexity < 0.60
    assert result.uncertainty_band is True


def test_http_contract_includes_version_and_route_decision():
    response = client.post("/classify", json={"prompt": "Return JSON with the answer."})

    assert response.status_code == 200
    body = response.json()
    assert body["classifier_version"] == "heuristic-2026.09.11"
    assert body["route_up"] is False
    assert body["task_class"] == "structured_output"


def test_empty_prompt_is_rejected():
    response = client.post("/classify", json={"prompt": ""})

    assert response.status_code == 422


def test_demo_embedding_is_normalized_and_versioned():
    response = client.post("/embed", json={"text": "same text"})

    assert response.status_code == 200
    body = response.json()
    assert len(body["embedding"]) == 64
    assert body["embedding_version"] == "demo-hash-64-2026.09.11"
