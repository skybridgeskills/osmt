# Fix results per page dropdown UI and alignment

Ensure the results per page dropdown displays correctly in the UI and is properly aligned and visible.

[ClickUp: 868h23199](https://app.clickup.com/t/868h23199)

## Analysis

**Status: Needs visual verification**

- `SizePaginationComponent` (`size-pagination.component`) renders an `<option>` list with values [50, 100, 150].
- Styling in `size-pagination.component.scss` uses `display: flex` and `margin-bottom: 20px`.
- Placement varies: in `filter-controls`, `collection-public`, and `manage-collection` alongside filters.
- The `<label>` has no `for` attribute; the `<select>` has no `id` for association. Select uses `class="m-text select"`.

**Potential issues**: Layout/alignment with filter bar, visibility on smaller viewports, or z-index/overflow in sticky/filter containers. Manual check recommended.
