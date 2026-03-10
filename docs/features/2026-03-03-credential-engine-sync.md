# Credential Engine Sync

OSMT can publish Rich Skill Descriptors (RSDs) and Collections to the Credential
Engine Registry via the Registry Assistant API. See also [Implementation Notes (WGU)](2026-03-03-credential-engine-sync-implementation-notes.md) for WGU use-case mapping and API references.

This document describes the OSMT → Credential Engine translation mapping and how to use the feature.

## OSMT → Credential Engine Translation

### Skill (RSD) → Competency

| OSMT Field          | CE/CTDL Field           | Notes                           |
| ------------------- | ----------------------- | ------------------------------- |
| `uuid`              | `CTID`                  | Hash-based; see [CTID Generation](#ctid-generation) |
| `name`              | `CompetencyLabel`       | Skill name                      |
| `statement`         | `CompetencyText`        | Skill statement                 |
| (org CTID)          | `Creator`               | List with org CTID from config  |
| `authors` (first)   | `Author`                | First author keyword value      |
| `categories`        | `CompetencyCategory`    | Category keyword values, max 10 |
| `searchingKeywords` | `ConceptKeyword`        | Keyword values, max 20          |
| (status)            | `PublicationStatusType` | `"Published"` or `"Deprecated"` |
| canonical URL       | `ExactAlignment`        | `{baseUrl}/api/skills/{uuid}`   |

**Not yet mapped:** Job codes (SOC/ONET) → `OccupationType`/`CredentialAlignmentObject`

### Collection → Collection

| OSMT Field    | CE/CTDL Field         | Notes                          |
| ------------- | --------------------- | ------------------------------ |
| `uuid`        | `CTID`                | Hash-based; see [CTID Generation](#ctid-generation) |
| `name`        | `Name`                | Collection name                |
| `description` | `Description`         | Collection description         |
| (derived)     | `HasMember`           | Skill CTIDs (hash-based)        |
| (org CTID)    | `OwnedBy`             | List with org CTID from config |
| (status)      | `LifeCycleStatusType` | `"Active"` or `"Ceased"`       |

### CTID Generation

CTIDs are deterministically derived using UUIDv5 (RFC 4122, SHA-1):

    CTID = "ce-" + UUIDv5(namespaceUuid, entityUuid)

Where `namespaceUuid = UUIDv5(OSMT_NAMESPACE, credential-engine.org-ctid)`.
This ensures:

- **Determinism:** Same record always produces the same CTID.
- **Deployment isolation:** Different `credential-engine.org-ctid` values
  produce different CTIDs, preventing collisions across instances.
- **Reverse correlation:** Skills include `ExactAlignment` with the OSMT URL
  containing the original UUID.

### Status and Deprecation

- **Published skill** → `PublicationStatusType: "Published"`
- **Archived skill** → Re-publish with `PublicationStatusType: "Deprecated"`
- **Published collection** → `LifeCycleStatusType: "Active"`
- **Archived collection** → Re-publish with `LifeCycleStatusType: "Ceased"`

### API Documentation

- **Registry Assistant Handbook**: <https://credreg.net/registry/assistant>
- Competency Frameworks: <https://credreg.net/registry/assistant#publishcompetencyframework>
- Publishing Competencies and Concepts: <https://credreg.net/registry/competencies>

### API Endpoints

- Competency: `POST {registryUrl}/assistant/competency/publish`
- Collection: `POST {registryUrl}/assistant/Collection/publish`
- Auth: `Authorization: ApiToken {apiKey}`

---

## How to Use

### Prerequisites

1. **Environment variables** (required for live CE sync):
   - `CREDENTIAL_ENGINE_API_KEY` – API key from Credential Engine
   - `CREDENTIAL_ENGINE_ORG_CTID` – Your organization CTID (e.g. `ce-...`)
   - `CREDENTIAL_ENGINE_REGISTRY_URL` – Registry URL (default:
     `https://sandbox.credentialengine.org`)

2. **Admin role** – Sync endpoints and UI require `ROLE_Osmt_Admin`.

### Configuration

When API key and org CTID are set, OSMT uses `CredentialEngineSyncTarget` and
publishes to the configured registry. When both are empty:

- **Dev profile:** `MockSyncTarget` – records sync locally, no HTTP calls
- **Other profiles:** Sync disabled (503 from sync endpoints)

### UI: Admin Sync Page

1. Log in as an admin.
2. Open **Sync** in the nav (or `/admin/sync`).
3. View integrations table (sync keys, record types, watermarks).
4. Click **Sync Now** to run incremental sync for all records since last watermark.

On 503 or “not configured,” the page shows instructions to set
`CREDENTIAL_ENGINE_*` environment variables.

### API Endpoints

| Method | Path                          | Description                      |
| ------ | ----------------------------- | -------------------------------- |
| GET    | `/api/sync/state`             | List integrations and watermarks |
| POST   | `/api/sync/skill/{uuid}`      | Sync one skill                   |
| POST   | `/api/sync/collection/{uuid}` | Sync one collection              |
| POST   | `/api/sync/all`               | Sync all (async, returns 202)    |

All require admin authentication.

### Test Script: Mock Skill Publish

Before running a full sync, you can verify CE connectivity by publishing a
single mock skill:

```bash
./bin/test-credential-engine-sync.sh
```

Uses the same `CREDENTIAL_ENGINE_*` env vars. Sources `api/osmt-dev-stack.env`
and `api/osmt-staging.env` if present. Requires `curl` and `jq`.

### Sync Behavior

- **Single sync:** Publishes or deprecates based on RSD/Collection status.
- **Sync all:** Runs skills first, then collections; updates watermarks after
  each record.
- **Watermarks:** Track last-synced timestamp per integration (syncKey +
  recordType) to support incremental sync.

### Known Limitations

- **Single-instance only:** The sync-all lock (`AtomicBoolean`) is in-process.
  Running multiple API instances concurrently could start duplicate syncs.
  If multi-instance deployment is needed, replace with a distributed lock
  (e.g. database row lock or Redis).
