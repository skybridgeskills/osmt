# Display categories in a collection with limited height container

Ensure categories in a collection are displayed in a container with limited height.

[ClickUp: 868h2319e](https://app.clickup.com/t/868h2319e)

## Analysis

**Status: Needs UI fix**

- Categories are shown in `collection-public.component.html` and `manage-collection.component.html` via `<app-pill-group [pillControls]="skillCategories">`.
- `pill-group.component` has a "Show all / Show fewer" toggle when `pillControls.length > 20`, but the pill list itself (`m-pill-group`) has no max-height or overflow.
- No `pill-group` SCSS file found; styles may be in global SCSS or BEM modules.

**Root cause**: The categories list can grow and push layout down with no scroll/constraint.

**Fix**: Add `max-height` and `overflow-y: auto` (or similar) to the pill group container in collection views so categories scroll instead of expanding the page.
