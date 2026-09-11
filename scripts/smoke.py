import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from contract_check import validate_chat_completion, validate_ledger_event


def main() -> int:
    required_files = (
        ROOT / "contracts" / "openapi.yaml",
        ROOT / "schemas" / "ledger-event.schema.json",
    )
    missing = [str(path.relative_to(ROOT)) for path in required_files if not path.is_file()]
    if missing:
        print(f"SMOKE_FAIL missing contract files: {', '.join(missing)}")
        return 1

    schema = json.loads((ROOT / "schemas" / "ledger-event.schema.json").read_text(encoding="utf-8"))
    required = set(schema.get("required", []))
    expected = {"request_id", "ts", "tenant_id", "route", "cache", "tokens", "timing", "gpu", "cost", "quality"}
    if required != expected:
        print(f"SMOKE_FAIL ledger required fields differ: {sorted(required)}")
        return 1

    validate_chat_completion({
        "model": "auto",
        "messages": [{"role": "user", "content": "Smoke test"}],
    })
    validate_ledger_event({
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
    })

    print("SMOKE_OK contracts present and sample request/event validate")
    return 0


if __name__ == "__main__":
    sys.exit(main())
