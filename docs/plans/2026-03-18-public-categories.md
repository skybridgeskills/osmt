# Public Categories

## Notes

### Scope of work

Make category list, category detail, and category skills endpoints and UI routes publicly accessible (no auth required), aligned with the existing public skills and collections pages.

**Include:** Update `CategoryLinksComponent` to always load and render category links (remove the auth gate). Currently it skips loading when `!isAuthenticated()`, so unauthenticated users see plain text. With public categories, links should work for everyone.

### Current state

**API (Kotlin)**

- `KeywordController` exposes:
  - `GET /api/v3/categories` — category list (paginated)
  - `GET /api/v3/categories/{identifier}` — category by id
  - `GET /api/v3/categories/{identifier}/skills` — skills in category (paginated)
  - `POST /api/v3/categories/{identifier}/skills` — search skills in category
- These endpoints are **not** in `SecurityConfigHelper.configurePublicEndpoints`; they fall under the catch-all `/api/**` which requires `admin`, `curator`, `view`, or `read`.
- `searchRelatedSkills` (used by category skills) already checks `allowPublicSearching` for unauthenticated access; `allPaginated` and `byId` have no such check.
- Skills and collections have explicit public entries for detail, search, filter, etc.

**UI (Angular)**

- `app-routing.module.ts`: `/categories` and `/categories/:id` both use `AuthGuard`.
- `CategoryLibraryComponent` and `CategoryDetailComponent` require auth to load.
- Skills and collections have public routes: `/skills/:uuid`, `/collections/:uuid` (no AuthGuard).
- `CategoryLinksComponent` (used in skill-list-row, public-table) loads category ids via `getAllPaginated` and renders names as `routerLink` when id is found. It gates on `isAuthenticated()` — when false, it never loads, so unauthenticated users see plain text. Once categories API is public, we should remove this gate so links work for everyone.

**Config**

- `allowPublicSearching` and `allowPublicLists` (AppConfig) control public access for skills/collections; KeywordController uses `allowPublicSearching` only in `searchRelatedSkills`.

### Questions

1. **allowPublicLists vs allowPublicSearching**  
   Category list and detail are “list”/metadata, not search. Use `allowPublicLists` (like skills/collections lists) or `allowPublicSearching` (like category skills)?  
   *Suggested: Use `allowPublicLists` for category list and detail to match skills/collections.*

2. **Unversioned and v2 category API paths**  
   KeywordController defines only v3 paths. Should we add public access for v2/unversioned as well for consistency, or restrict to v3?  
   *Suggested: Add all versions via `buildAllVersions(RoutePaths.CATEGORY_*)` for consistency; v2/unversioned may 404 but security config stays uniform.*

3. **CategoryLinksComponent auth gate**  
   **In scope (non-negotiable):** Remove the `isAuthenticated()` check in `CategoryLinksComponent.loadCategoryIds()` so category links load and render for unauthenticated users. This completes the 868h2319b fix — links will be present for all users once categories API is public.
