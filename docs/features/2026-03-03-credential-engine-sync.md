# Credential Engine Sync

OSMT can publish Rich Skill Descriptors (RSDs) and Collections to the Credential
Engine Registry via the Registry Assistant API. This document describes the
OSMT → Credential Engine translation mapping and how to use the feature.

## OSMT → Credential Engine Translation

### Skill (RSD) → Competency

| OSMT Field          | CE/CTDL Field           | Notes                           |
|---------------------|-------------------------|---------------------------------|
| `uuid`              | `CTID`                  | Prefixed as `ce-{uuid}`         |
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
|---------------|-----------------------|--------------------------------|
| `uuid`        | `CTID`                | Prefixed as `ce-{uuid}`        |
| `name`        | `Name`                | Collection name                |
| `description` | `Description`         | Collection description         |
| (derived)     | `HasMember`           | Skill CTIDs (`ce-{skillUuid}`) |
| (org CTID)    | `OwnedBy`             | List with org CTID from config |
| (status)      | `LifeCycleStatusType` | `"Active"` or `"Ceased"`       |

### Status and Deprecation

- **Published skill** → `PublicationStatusType: "Published"`
- **Archived skill** → Re-publish with `PublicationStatusType: "Deprecated"`
- **Published collection** → `LifeCycleStatusType: "Active"`
- **Archived collection** → Re-publish with `LifeCycleStatusType: "Ceased"`

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
|--------|-------------------------------|----------------------------------|
| GET    | `/api/sync/state`             | List integrations and watermarks |
| POST   | `/api/sync/skill/{uuid}`      | Sync one skill                   |
| POST   | `/api/sync/collection/{uuid}` | Sync one collection              |
| POST   | `/api/sync/all`               | Sync all (async, returns 202)    |

All require admin authentication.

### Sync Behavior

- **Single sync:** Publishes or deprecates based on RSD/Collection status.
- **Sync all:** Runs skills first, then collections; updates watermarks after
  each record.
- **Watermarks:** Track last-synced timestamp per integration (syncKey +
  recordType) to support incremental sync.
