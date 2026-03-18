# Fix CSV download for RSD (should not download JSON)

Ensure that the 'Download RSD in CSV format' option results in a CSV file download, not a JSON file.

[ClickUp: 868h2319f](https://app.clickup.com/t/868h2319f)

## Analysis

**Status: Bug confirmed**

- `getSkillCsvByUuid` in `ui/src/app/richskill/service/rich-skill.service.ts` calls `skills/{uuid}` with **Accept: 'application/json'** (line 90).
- That hits the JSON skill-detail endpoint, which returns JSON. The response is saved with a `.csv` filename but contains JSON.
- API has `byUUIDCsvView` at `collections/{uuid}/updateSkills` (RichSkillController) — that path is for collection skills update, not single-skill CSV.
- There is no GET `/api/v3/skills/{uuid}` endpoint that produces `text/csv` for content negotiation.

**Root cause**: Frontend requests JSON and gets JSON; the filename extension is misleading.

**Fix**: Either (1) Add `Accept: 'text/csv'` and a skill-detail CSV endpoint on the API, or (2) Use the bulk export task flow (like XLSX) for single-skill CSV.
