import json
import sys
from datetime import datetime
from pathlib import Path
from typing import Any
from uuid import UUID

ROOT = Path(__file__).resolve().parents[1]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def validate_chat_completion(payload: Any) -> None:
    require(isinstance(payload, dict), "request must be an object")
    require(isinstance(payload.get("model"), str) and bool(payload["model"]), "model is required")
    messages = payload.get("messages")
    require(isinstance(messages, list) and messages, "messages must be a non-empty array")
    for index, message in enumerate(messages):
        require(isinstance(message, dict), f"messages[{index}] must be an object")
        require(message.get("role") in {"system", "user", "assistant", "tool"}, f"messages[{index}].role is invalid")
        require(isinstance(message.get("content"), str), f"messages[{index}].content must be a string")

    extensions = payload.get("x_arbiter", {})
    require(isinstance(extensions, dict), "x_arbiter must be an object")
    if "max_cost_usd" in extensions:
        require(isinstance(extensions["max_cost_usd"], (int, float)) and extensions["max_cost_usd"] >= 0, "max_cost_usd must be non-negative")
    if "min_quality_tier" in extensions:
        require(extensions["min_quality_tier"] in {"standard", "high", "critical"}, "min_quality_tier is invalid")
    if "cache" in extensions:
        require(extensions["cache"] in {"allow", "bypass", "refresh"}, "cache is invalid")
    if "tenant_id" in extensions:
        require(isinstance(extensions["tenant_id"], str) and bool(extensions["tenant_id"]), "tenant_id must be non-empty")


def validate_ledger_event(event: Any) -> None:
    require(isinstance(event, dict), "ledger event must be an object")
    required = {"request_id", "ts", "tenant_id", "route", "cache", "tokens", "timing", "gpu", "cost", "quality"}
    require(set(event) == required, "ledger event fields must match the frozen contract")
    UUID(event["request_id"])
    datetime.fromisoformat(event["ts"].replace("Z", "+00:00"))
    require(isinstance(event["tenant_id"], str) and bool(event["tenant_id"]), "tenant_id must be non-empty")
    for field in ("route", "cache", "tokens", "timing", "cost", "quality"):
        require(isinstance(event[field], dict), f"{field} must be an object")
    require(event["gpu"] is None or isinstance(event["gpu"], dict), "gpu must be an object or null")


def main() -> int:
    valid_request = {
        "model": "auto",
        "messages": [{"role": "user", "content": "Summarize this document."}],
        "x_arbiter": {"cache": "allow", "min_quality_tier": "standard", "tenant_id": "demo"},
    }
    validate_chat_completion(valid_request)

    valid_event = {
        "request_id": "00000000-0000-4000-8000-000000000001",
        "ts": "2026-09-11T12:00:00Z",
        "tenant_id": "demo",
        "route": {},
        "cache": {},
        "tokens": {},
        "timing": {},
        "gpu": None,
        "cost": {},
        "quality": {},
    }
    validate_ledger_event(valid_event)
    print("CONTRACT_OK inbound request and ledger event validated")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (TypeError, ValueError, KeyError) as error:
        print(f"CONTRACT_FAIL {error}")
        sys.exit(1)
