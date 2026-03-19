# Display categories in a collection with limited height container

Ensure categories in a collection are displayed in a container with limited height.

[ClickUp: 868h2319e](https://app.clickup.com/t/868h2319e)

## Analysis

**Status: Fixed**

- Categories use `<app-pill-group>` on public and manage collection pages only (single component).
- **Previous bug**: Collapsed styling (`max-height: 100px`) applied whenever `collapsed` defaulted to `true`, but **“Show all N”** only appeared for `pillControls.length > 20`. Fewer pills that wrapped to a tall block were **clipped with no control**; random-looking expansion came from `(click)="expand()"` on the wrapper.

## Fix (2026-03-19)

- **`hasOverflow`**: Measured with `ul.scrollHeight` vs `100px` (same as `.m-pill-group-collapsible.collapsed` in `styles.scss`). `ResizeObserver` on the host re-runs when width/height changes (wrapping).
- **Toggle UI**: Shown when `hasOverflow`, not when count > 20.
- **Classes**: `m-pill-group-collapsible` and `collapsed` apply only when `hasOverflow` (and collapsed), so short lists are not clipped.
- **Wrapper click**: `onWrapperClick` only expands when `hasOverflow && collapsed`, and ignores clicks on `button` elements (pills / toggle).

## Out of scope

- Expanded state still grows the page (no `overflow-y: auto` box); collapse/expand remains the primary pattern.
