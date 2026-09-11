# Semantic cache boundary

The semantic cache is opt-in under the `semantic` Spring profile. Its default threshold is cosine similarity `0.95`, configurable with `arbiter.semantic-threshold`.

The cache is checked only after the exact cache misses and only when `SemanticCachePolicy` marks the request eligible. Denied requests never reach embedding or semantic lookup.

The current adapter is an in-memory reference implementation for threshold and task-class behavior. The classifier sidecar exposes a deterministic demo embedding endpoint so the `semantic` profile can run locally, but it is not a quality claim and does not establish cache precision/recall. The next implementation must replace it with a measured embedding model, pgvector, and the required near-miss poisoning evaluation before enabling semantic caching for real traffic.
