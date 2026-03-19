# Report total RSD counts above 10,000 accurately

Ensure that total RSD counts above 10,000 are accurately reported.

[ClickUp: 868h23196](https://app.clickup.com/t/868h23196)

## Analysis

**Status: Bug likely (Elasticsearch default)**

- Elasticsearch defaults `track_total_hits` to 10,000. Above that, `total.relation` becomes `"gte"` and the value is a lower bound.
- OSMT uses `searchHits.totalHits.toInt()` in SearchController, RichSkillRepository, CollectionRepository, KeywordController without `track_total_hits`.
- No `track_total_hits` found in the codebase. Queries therefore use the ES default.

**Root cause**: ES stops counting accurately above 10k unless `track_total_hits: true` (or a higher integer) is set in the query.

**Fix**: Add `track_total_hits: true` (or a suitable upper bound) to ES search requests where accurate total count is needed, e.g. in `RichSkillEsRepo`, `SearchController`, and related search paths. Consider using `_count` for display-only totals if performance is a concern.
