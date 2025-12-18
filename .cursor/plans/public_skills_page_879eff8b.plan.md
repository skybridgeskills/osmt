---
name: Public Skills Page
overview: Create a publicly accessible skills list page that reuses the existing authenticated skills list component. Feature is always enabled, matching the public single skill page behavior.
todos:
  - id: component-updates
    content: Add isPublicView property to SkillsListComponent and update methods to respect public mode
    status: completed
  - id: library-component-updates
    content: Update RichSkillsLibraryComponent to detect auth status and set isPublicView accordingly, filtering to Published/Archived only
    status: completed
    dependencies:
      - component-updates
  - id: navigation
    content: Update header component to show public navbar and login button for unauthenticated users on /skills
    status: completed
    dependencies:
      - library-component-updates
  - id: routing
    content: Remove AuthGuard from /skills route to allow public access
    status: completed
    dependencies:
      - navigation
  - id: tests
    content: Write tests for public mode behavior in RichSkillsLibraryComponent
    status: completed
    dependencies:
      - library-component-updates
      - component-updates
---

# Public Skills List Page Implementation

## Overview

Add a public, searchable skills list page that reuses the existing authenticated skills list component. The feature is always enabled (no flag needed), matching the behavior of the existing public single skill page (`/skills/:uuid`).

## Architecture

The implementation reuses the existing `/skills` route and `RichSkillsLibraryComponent`:

- Remove `AuthGuard` from `/skills` route to allow public access
- Update `RichSkillsLibraryComponent` to detect authentication status and adjust behavior:
- If authenticated: show full features (current behavior)
- If not authenticated: show public view (only Published and Archived skills, no actions)
- Add `isPublicView` property to `SkillsListComponent` to control visibility of authenticated features
- Filtering rules match the public single skill page: API automatically filters out Draft and Deleted for unauthenticated users, so we only show Published and Archived
- This approach reuses the same route and component, making it cleaner than creating a separate route

## Implementation Steps

### 1. Component Updates for Public Mode

**File: `ui/src/app/richskill/list/skills-list.component.ts`**

- Add `isPublicView: boolean = false` property (set by parent component)
- Update ALL mutation action visibility methods to check `isPublicView` and return `false` when true:
- `publishVisible()` → return `false` if `isPublicView`
- `archiveVisible()` → return `false` if `isPublicView`
- `unarchiveVisible()` → return `false` if `isPublicView`
- `addToCollectionVisible()` → return `false` if `isPublicView`
- `addToWorkspaceVisible()` → return `false` if `isPublicView`
- `exportSearchVisible()` → return `false` if `isPublicView`
- Update flags to disable mutation features when `isPublicView`:
- `showAddToCollection` → set to `false` when `isPublicView`
- `showExportSelected` → set to `false` when `isPublicView`
- Update `actionsVisible()` to return `false` when `isPublicView` (hides entire action bar)
- Update `getSelectAllEnabled()` to return `false` when `isPublicView` (disables skill selection)

**File: `ui/src/app/richskill/list/skills-list.component.html`**

- Conditionally hide action bar and authenticated features when `isPublicView` is true
- Update empty message to check `isPublicView` and remove "Create an RSD" links when public

### 2. Update RichSkillsLibraryComponent

**File: `ui/src/app/richskill/library/rich-skills-library.component.ts`**

- In `ngOnInit()`, check `authService.isAuthenticated()`
- If not authenticated:
- Set `this.isPublicView = true`
- Initialize `selectedFilters` to only include `PublishStatus.Published` and `PublishStatus.Archived` (remove Draft)
- This matches the API behavior which automatically filters out Draft and Deleted for unauthenticated users
- If authenticated, keep current behavior (show all features including Draft)
- Update `loadNextPage()` to respect public mode filtering

### 3. Navigation Updates

**File: `ui/src/app/navigation/header.component.ts`**

- Update `showPublicNavbar()` to also return `true` when on `/skills` route and user is not authenticated
- This will show the public navbar (simplified) for unauthenticated users on the skills list page

**File: `ui/src/app/navigation/header.component.html`**

- Add a login button/link to the public navbar section (when `showPublicNavbar()` is true)
- Login button should link to `/login` with a return parameter to `/skills`
- Should only show when user is not authenticated

### 4. Routing

**File: `ui/src/app/app-routing.module.ts`**

- Remove `canActivate: [AuthGuard] `from `/skills` route
- Route will work for both authenticated and unauthenticated users
- Component will adjust behavior based on authentication status
- Root route (`/`) already redirects to `/skills` (no changes needed)

### 5. Service Updates

**File: `ui/src/app/richskill/service/rich-skill.service.ts`**

- Ensure `getSkillsFiltered()` works without authentication (should already work if API allows it)
- The service should handle unauthenticated requests gracefully
- API already filters out Draft and Deleted for unauthenticated users automatically

## Key Design Decisions

1. **Code Reuse**: Reuse existing `/skills` route and `RichSkillsLibraryComponent` - component detects authentication status and adjusts behavior
2. **Always Enabled**: Feature is always enabled (no flag needed), consistent with existing public single skill page
3. **Filtering**: Public view shows Published and Archived skills only (API automatically filters out Draft and Deleted for unauthenticated users, matching the public single skill page behavior)
4. **Security**: Backend already supports public lists via `allowPublicLists`; we're adding UI support

## Testing Considerations

- Test `/skills` route loads without authentication
- Test root route (`/`) redirects to `/skills` (already implemented)
- Test authenticated users see full features on `/skills` (including Draft)
- Test unauthenticated users only see Published and Archived skills (no Draft or Deleted)
- Test ALL mutation buttons/actions are hidden for unauthenticated users:
- Publish, Archive, Unarchive buttons
- Add to Collection, Add to Workspace buttons
- Remove from Collection/Workspace buttons
- Export Selected button
- Row-level actions (archive, unarchive, publish, add to collection)
- Skill selection/checkbox functionality disabled
- Action bar completely hidden
- Test public navbar shows login button for unauthenticated users on `/skills`
- Test login button redirects to login page with return parameter
- Test API endpoints work without authentication (API automatically filters Draft/Deleted)

## Files to Modify

**Backend:**

- `api/src/main/resources/config/application.properties`
- `api/src/main/kotlin/edu/wgu/osmt/config/AppConfig.kt`
- `api/src/main/kotlin/edu/wgu/osmt/ui/UiController.kt`
- `api/docker/bin/docker_entrypoint.sh` (optional)

**Frontend:**

- `ui/src/app/models/app-config.model.ts`
- `ui/src/app/app.config.ts`
- `ui/src/app/richskill/list/skills-list.component.ts`
- `ui/src/app/richskill/list/skills-list.component.html`
- `ui/src/app/richskill/library/rich-skills-library.component.ts`
- `ui/src/app/navigation/header.component.ts`
- `ui/src/app/navigation/header.component.html`
- `ui/src/app/app-routing.module.ts`