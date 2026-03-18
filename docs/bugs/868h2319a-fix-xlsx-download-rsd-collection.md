# Fix XLSX download for RSD or Collection

Ensure that downloading an RSD or a Collection as XLSX works properly.

[ClickUp: 868h2319a](https://app.clickup.com/t/868h2319a)

## Analysis

**Status: Needs manual verification**

- **RSD XLSX**: `ExportRsdComponent.getRsdXlsx()` uses `exportSearchXlsx` with `ApiSearch({ uuids: [uuid] })`, then polls `getResultExportedXlsxLibrary`. Uses task-based export flow.
- **Collection XLSX**: `ExportCollectionComponent.getCollectionXlsx()` uses `requestCollectionSkillsXlsx` and polls `getXlsxTaskResultsIfComplete`.
- API has `RichSkillXlsxExport`, task types `XlsxTask`/`XlsxTaskV2`, and `TASK_DETAIL_MEDIA` for binary results.
- Code paths for both RSD and collection XLSX exist. Without running the app, cannot confirm failure mode (e.g. 404, wrong content type, timeout, empty file).

**Suggested checks**: Run export for single RSD and for collection; confirm file downloads, opens in Excel, and contains expected data. Check network tab for errors. May share causes with CSV bug if task/result handling is broken.
