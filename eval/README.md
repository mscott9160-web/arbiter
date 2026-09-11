# Evaluation harness

`cache_poisoning.py` evaluates a held-out set of near-miss prompts against the deterministic demo embedding and generates `bench/cache_precision.json`.

Run:

```powershell
python eval/cache_poisoning.py
```

The artifact is evidence for the demo embedding only. It is not a production quality claim. The shipped semantic threshold must have zero false hits on the held-out near-miss set before this evaluation can pass.
