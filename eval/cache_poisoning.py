import hashlib
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "bench" / "cache_precision.json"
DIMENSIONS = 64
THRESHOLDS = [0.90, 0.93, 0.95, 0.97, 0.99]

# Held-out near misses deliberately share vocabulary but change the requested intent.
CASES = [
    ("how do I reverse a linked list", "how do I reverse a linked list in place", False),
    ("explain database indexes", "create database indexes in postgres", False),
    ("summarize this release note", "translate this release note", False),
    ("what is a mutex", "implement a mutex in Java", False),
    ("explain HTTP caching", "explain HTTP cache invalidation", False),
]


def embed(text: str) -> list[float]:
    values = [0.0] * DIMENSIONS
    for token in text.lower().split():
        digest = hashlib.sha256(token.encode("utf-8")).digest()
        values[int.from_bytes(digest[:2], "big") % DIMENSIONS] += 1.0
    norm = math.sqrt(sum(value * value for value in values))
    return [value / norm for value in values] if norm else values


def cosine(left: list[float], right: list[float]) -> float:
    return sum(a * b for a, b in zip(left, right))


def evaluate(threshold: float) -> dict[str, float | int]:
    scores = [cosine(embed(source), embed(candidate)) for source, candidate, _ in CASES]
    false_hits = sum(score >= threshold for score in scores)
    return {
        "threshold": threshold,
        "cases": len(CASES),
        "false_hits": false_hits,
        "precision": (len(CASES) - false_hits) / len(CASES),
        "recall": None,
    }


def main() -> None:
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    artifact = {
        "embedding_version": "demo-hash-64-2026.09.11",
        "dataset": "held-out-near-miss-v1",
        "methodology": "All pairs are labeled non-equivalent; false hit means cosine score meets threshold.",
        "results": [evaluate(threshold) for threshold in THRESHOLDS],
        "shipped_threshold": 0.95,
        "production_status": "demo embedding only; not a quality claim",
    }
    OUTPUT.write_text(json.dumps(artifact, indent=2) + "\n", encoding="utf-8")
    shipped = next(result for result in artifact["results"] if result["threshold"] == artifact["shipped_threshold"])
    if shipped["false_hits"] != 0:
        raise SystemExit("CACHE_POISONING_FAIL shipped threshold has false hits")
    print(f"CACHE_EVAL_OK shipped threshold false_hits={shipped['false_hits']}")


if __name__ == "__main__":
    main()
