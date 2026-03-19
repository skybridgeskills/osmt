# OSMT Bug Index

| Bug | Status | Notes |
|-----|--------|-------|
| [868h23198](868h23198-viewer-role-blank-screen.md) (viewer blank screen) | Partially addressed | `/skills` is public; possible edge cases remain |
| [868h2319f](868h2319f-csv-download-rsd-json.md) (CSV downloads JSON) | Confirmed | `getSkillCsvByUuid` sends `Accept: application/json` and hits the JSON endpoint |
| [868h23197](868h23197-typeahead-search-categories-dropdown.md) (type-ahead categories) | Confirmed | KeywordEsRepo limits type-ahead to 20 results when searchStr is not empty |
| [868h2319e](868h2319e-categories-limited-height-container.md) (categories limited height) | Confirmed | Pill group container has no max-height or scroll |
| [868h2319d](868h2319d-expand-collapse-show-all-fewer.md) (Show All/Fewer) | Needs check | Toggle exists; collapsed class may not be styling visibility correctly |
| [868h23196](868h23196-rsd-counts-above-10000.md) (RSD counts >10k) | Confirmed | Elasticsearch default track_total_hits caps counts at 10,000 |
| [868h2319a](868h2319a-fix-xlsx-download-rsd-collection.md) (XLSX download) | Needs check | Code paths look correct; manual test recommended |
| [868h2319c](868h2319c-preserve-collection-description-workspace-conversion.md) (preserve description) | Confirmed | ConvertToCollectionComponent.updateObject() omits description; workspace data not passed |

Resolved bugs live under [`bugs-done/`](../bugs-done/).
