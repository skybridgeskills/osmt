# Show all relevant results in type-ahead search for categories dropdown

Type-ahead search in categories dropdown should display all relevant results.

[ClickUp: 868h23197](https://app.clickup.com/t/868h23197)

## Analysis

**Status: Bug confirmed**

- `KeywordEsRepo.typeAheadSearch` in `api/src/main/kotlin/edu/wgu/osmt/keyword/KeywordEsRepo.kt` limits results: when `searchStr.isNotEmpty()`, it uses `OffsetPageable(0, 20, null)` (line 57).
- When empty, it returns up to 10,000. With a query, only 20 results are returned.
- `JobCodeEsRepo` uses the same pattern: `limit = if (searchStr.isEmpty()) 10000 else 20`.
- UI uses `KeywordSearchService.searchKeywords()` → `search/jobcodes` or `search/keywords` with `query` and `type` params.

**Root cause**: Hard limit of 20 when user types in the type-ahead, so relevant results beyond the first 20 are hidden.

**Fix**: Increase the limit for type-ahead (e.g. 50–100) or make it configurable; ensure sort orders match user expectations (e.g. relevance, then name).
