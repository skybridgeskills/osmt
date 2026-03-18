# Expand/collapse content with Show All/Fewer button

Ensure that clicking 'Show All/Fewer' expands or collapses the relevant content as indicated.

[ClickUp: 868h2319d](https://app.clickup.com/t/868h2319d)

## Analysis

**Status: Implementation exists; verify behavior**

- `PillGroupComponent` has `collapsed = true` and `toggleCollapse()` toggles it.
- The button only appears when `pillControls.length > 20`; label is "Show all N" / "Show fewer".
- Template uses `[ngClass]="{ collapsed: collapsed }"` on the wrapper. Pills are always rendered: `*ngFor="let c of pillControls"` with no `*ngIf` limiting visibility.
- **Likely issue**: The `collapsed` class may control CSS (e.g. max-height, overflow) but the component doesn't conditionally render pills. If CSS isn't restricting height when collapsed, the expand/collapse won't change what's shown.

**Fix area**: Confirm `m-pill-group-collapsible.collapsed` applies `max-height`/`overflow: hidden` and that toggling correctly shows/hides pills. Component logic is present; styling/visibility may need adjustment.
