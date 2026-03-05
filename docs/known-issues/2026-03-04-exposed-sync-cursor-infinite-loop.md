# Exposed ORM Sync Cursor Infinite Loop

**Date:** 2026-03-04  
**Component:** Credential Engine sync (`SyncService`, `SyncQueryHelpers`)  
**Status:** Workaround in place; root cause unresolved

## Summary

The Credential Engine sync uses composite cursor pagination `(watermarkDate, watermarkId)` to fetch published skills/collections in batches. An Exposed ORM bug causes the query to return records that should be excluded—specifically, when the cursor is `(updateDate, id)` of the last processed record, the next fetch incorrectly returns that same record again. This leads to an infinite loop or circuit-breaker abort.

## Symptoms

- Sync repeatedly processes the same skill (e.g. id=780) batch after batch
- Logs show `watermark advanced to date=X id=780` followed by `Batch N raw from DB: size=1 ids=[780]` with the same record
- Workaround log: `excluded N records that were <= watermark (Exposed query bug workaround)`
- Circuit breaker eventually fires: `Sync cursor stuck at id=780 - Exposed query returns same record repeatedly`

## Root Cause

The pagination query in `SyncQueryHelpers.kt` uses Exposed DSL:

```kotlin
(RichSkillDescriptorTable.id greater EntityID(watermarkId, RichSkillDescriptorTable))
```

**Intended logic:** Return records where `(updateDate > watermarkDate) OR (updateDate = watermarkDate AND id > watermarkId)`. Thus when watermark is `(2023-03-30, 780)`, record 780 should be excluded.

**Observed behavior:** The query returns record 780 anyway. Raw SQL with the same conditions (`updateDate = ? AND id > ?`) returns no rows when executed directly against MySQL. The bug appears to be in how Exposed generates or binds the `EntityID` comparison for `LongIdTable.id` columns.

## Why Tests Don't Reproduce It

- Tests use Testcontainers MySQL with fresh, synthetic data
- Production/dev databases have specific data distributions (many records sharing the same `updateDate`, boundary ids like 780)
- EntityID serialization or JDBC binding may differ by driver/dialect
- No test asserts the actual SQL generated; only behavioral outcomes are validated

## Architectural Problems

1. **No abstraction over the cursor query** – The sync depends directly on Exposed DSL. There is no seam to use raw SQL for this critical path.
2. **ORM as single source of truth** – Business-critical pagination semantics are expressed only in the DSL; no explicit contract or SQL assertion.
3. **Framework-specific type at DB boundary** – `Column<EntityID<Long>>` comparison behavior is not fully specified and can vary across Exposed versions and JDBC drivers.
4. **Tests validate outcome, not mechanism** – Tests pass when the ORM behaves correctly but do not detect when the generated SQL diverges from the intended logic.

## Workarounds in Place

1. **Defensive Kotlin-side filter** – `SyncService` filters out any record where `(updateDate, id) <= (watermarkDate, watermarkId)` before processing. Records incorrectly returned by the query are dropped.
2. **Circuit breaker** – If the same max id repeats for 3+ consecutive batches, sync aborts with a clear error instead of looping indefinitely.
3. **Diagnostic doc** – `docs/plans/2026-03-02-credential-engine-sync/07-sync-duplicate-diagnostic.md` describes how to log Exposed SQL and verify the generated query.

## Proper Fix (Not Yet Implemented)

1. **Use raw SQL for the cursor query** – Bypass Exposed for `findSkillsUpdatedSince` / `findCollectionsUpdatedSince` composite-cursor branch. The query is small, critical, and stable.
2. **Introduce a SyncCursorQuery abstraction** – Allow swapping implementations (raw SQL vs Exposed) and test the raw SQL path independently.
3. **Add SQL assertion tests** – Verify the actual SQL (or raw implementation) matches the intended cursor semantics.

## References

- `api/src/main/kotlin/edu/wgu/osmt/credentialengine/SyncQueryHelpers.kt` – Query definitions
- `api/src/main/kotlin/edu/wgu/osmt/credentialengine/SyncService.kt` – Filter and circuit breaker logic
- `docs/plans/2026-03-02-credential-engine-sync/07-sync-duplicate-diagnostic.md` – Diagnostic steps
- Exposed `LongIdTable` / `EntityID`: JetBrains Exposed framework
- Related: Exposed entity cache issues (e.g. exposed/issues/653)
