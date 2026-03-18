# Fix viewer role blank screen due to missing public home/search page

Prevent blank screen for viewer role users by ensuring a public home/search page exists. Users should not be redirected to authenticate without the ability to browse public collections.

[ClickUp: 868h23198](https://app.clickup.com/t/868h23198)

## Analysis

**Status: Likely partially addressed, needs verification**

- Default route `/` redirects to `/skills`, which has no AuthGuard (public).
- `RichSkillsLibraryComponent` and `RichSkillSearchResultsComponent` both handle unauthenticated users via `isPublicView = true`, filtering to Published/Archived only.
- `/skills/search` (search results) has no AuthGuard; `/search` (advanced search) requires AuthGuard.
- API permits public access to search/filter endpoints (SecurityConfigHelper).
- **Potential gap**: Authenticated "viewer" role users may hit edge cases. AuthGuard only redirects to login when `!isAuthenticated()`; viewers pass. If the bug persists, check: OAuth callback flow, initial app load before auth resolves, or role-specific routing.

**Suggested fix areas**: Ensure `/skills` and `/skills/search` remain public landing points; add a dedicated public home if needed.
