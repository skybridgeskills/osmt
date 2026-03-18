# Style category names as links

Update the UI so that category names are visually styled as links.

[ClickUp: 868h2319b](https://app.clickup.com/t/868h2319b)

## Analysis

**Status: Needs UI fix**

- Category names appear in several places: skill list row (`getFormattedCategories()`), collection pill group (`KeywordCountPillControl`), rich skill detail card, etc.
- In `skill-list-row.component.html`, categories are plain text in `m-inlineHeading-x-text`. Skill names use `<a class="t-focus t-type-bodyLink" [routerLink]="...">`.
- `KeywordCountPillControl.primaryLabel` returns `${this.keyword}` as string; pills render this as text.
- Category detail route exists: `/categories/:id`. Category names could link there.

**Root cause**: Category names are rendered as text; they should use `<a>` with `routerLink` to `/categories/:id` and link styling (e.g. `t-type-bodyLink`, underline, hover).

**Fix**: Where category names are displayed (skill rows, pills, detail cards), render them as `<a [routerLink]="['/categories', categoryId]">` (or equivalent) with link styling. May need to pass category id alongside name in `KeywordCount`/API models.
