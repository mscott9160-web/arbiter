import re
from dataclasses import dataclass

CLASSIFIER_VERSION = "heuristic-2026.09.11"

_CODE_PATTERN = re.compile(r"```|\b(?:python|java|javascript|typescript|sql|bash)\b|\b(?:function|class|def|compile|debug)\b", re.IGNORECASE)
_MATH_PATTERN = re.compile(r"[=+*/^]|\b(?:integral|derivative|equation|solve|calculate)\b", re.IGNORECASE)
_STRUCTURED_PATTERN = re.compile(r"\b(?:json|yaml|schema|structured output|table)\b", re.IGNORECASE)
_SUMMARY_PATTERN = re.compile(r"\b(?:summarize|summary|tl;dr|key points)\b", re.IGNORECASE)


@dataclass(frozen=True)
class HeuristicResult:
    complexity: float
    task_class: str
    uncertainty_band: bool
    features: dict[str, float]


def classify_prompt(prompt: str) -> HeuristicResult:
    if not isinstance(prompt, str) or not prompt.strip():
        raise ValueError("prompt must be a non-empty string")

    token_count = max(1, len(prompt.split()))
    question_count = prompt.count("?")
    code_signal = bool(_CODE_PATTERN.search(prompt))
    math_signal = bool(_MATH_PATTERN.search(prompt))
    structured_signal = bool(_STRUCTURED_PATTERN.search(prompt))
    imperative_signal = bool(re.search(r"\b(?:write|build|implement|design|debug|explain|compare)\b", prompt, re.IGNORECASE))

    score = 0.05
    score += min(token_count / 1_000, 0.35)
    score += min(question_count * 0.04, 0.12)
    score += 0.18 if code_signal else 0.0
    score += 0.14 if math_signal else 0.0
    score += 0.08 if structured_signal else 0.0
    score += 0.06 if imperative_signal else 0.0
    complexity = round(min(score, 1.0), 4)

    if code_signal:
        task_class = "code_execution"
    elif math_signal:
        task_class = "math"
    elif _SUMMARY_PATTERN.search(prompt):
        task_class = "summarize"
    elif structured_signal:
        task_class = "structured_output"
    else:
        task_class = "general"

    return HeuristicResult(
        complexity=complexity,
        task_class=task_class,
        uncertainty_band=0.45 < complexity < 0.60,
        features={
            "token_count": float(token_count),
            "question_count": float(question_count),
            "code_signal": float(code_signal),
            "math_signal": float(math_signal),
            "structured_signal": float(structured_signal),
            "imperative_signal": float(imperative_signal),
        },
    )
