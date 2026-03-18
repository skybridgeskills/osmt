# Public Pages and API Access

This document describes what unauthenticated users can access in OSMT: public
API endpoints, UI routes, and how configuration flags control behavior.

## Overview

By default, OSMT allows unauthenticated users to browse published skills,
collections, and categories. This supports open libraries and public-facing
skill catalogs. Two configuration flags control what is accessible:

| Flag                 | Default | Effect                                                                 |
|----------------------|---------|------------------------------------------------------------------------|
| `app.allowPublicLists` | `true`  | When true, list and detail endpoints are public (skills, collections, categories). When false, they require authentication. |
| `app.allowPublicSearching` | `true` | When true, search and filter endpoints are public. When false, unauthenticated users get 401. |

Set these in `application.properties` or via environment variables.

## Public API Endpoints

When the flags are enabled, these endpoints allow unauthenticated access (no
`Authorization` header required):

### Skills

- `GET /api/{v2,v3,}/skills/{uuid}` — skill detail (canonical URL)
- `POST /api/{v2,v3,}/search/skills` — search skills
- `POST /api/{v2,v3,}/skills/filter` — filter skills (paginated list)
- `GET /api/{v2,v3,}/search/jobcodes` — job code type-ahead

### Collections

- `GET /api/{v2,v3,}/collections/{uuid}` — collection detail
- `POST /api/{v2,v3,}/search/collections` — search collections
- `POST /api/{v2,v3,}/collections/{uuid}/skills` — skills in a collection
- `GET /api/{v2,v3,}/collections/{uuid}/csv` — export collection as CSV
- `GET /api/{v2,v3,}/collections/{uuid}/xlsx` — export collection as XLSX

### Categories

- `GET /api/v3/categories` — category list (paginated)
- `GET /api/v3/categories/{id}` — category detail
- `GET /api/v3/categories/{id}/skills` — skills in a category (paginated)
- `POST /api/v3/categories/{id}/skills` — search skills in a category

### Search metadata

- `GET /api/{v2,v3,}/search/keywords` — keyword type-ahead (categories, keywords, standards, etc.)
- `GET /api/{v2,v3,}/search/jobcodes` — job code type-ahead

### Task results

- `GET /api/{v2,v3,}/results/text/{uuid}` — task result (e.g. CSV export)
- `GET /api/{v2,v3,}/results/media/{uuid}` — task result (e.g. XLSX export)

List endpoints (`GET /api/*/skills`, `GET /api/*/collections`) are also public
when `allowPublicLists` is true; they are configured separately from the above.

## Public UI Routes

When `allowPublicLists` and `allowPublicSearching` are enabled, unauthenticated
users can reach these Angular routes without being redirected to login:

| Route              | Page                         |
|--------------------|------------------------------|
| `/`                | Redirects to `/skills`       |
| `/skills`          | RSD library (published only) |
| `/skills/search`   | Search results               |
| `/skills/{uuid}`   | Public skill detail          |
| `/collections/{uuid}` | Public collection detail  |
| `/categories`      | Category library             |
| `/categories/{id}`| Category detail (skills list)|

## What Unauthenticated Users Can Do

- Browse the skills library (published and archived RSDs only; draft/deleted are hidden)
- Search and filter skills
- View individual skill and collection detail pages
- Browse the category library and view skills by category
- Use type-ahead in filter dropdowns (keywords, categories, job codes)
- Download collection CSV/XLSX exports (when task-based export completes)

Unauthenticated users cannot:

- Create, edit, publish, or delete skills or collections
- Access admin, sync, or workspace features
- View audit logs or draft content

## Related Configuration

- `app.publicKeywordLimit` (default: 1000) — max skills scanned when building
  keyword suggestions for unauthenticated users. Larger values allow more
  complete type-ahead at the cost of slower responses.
