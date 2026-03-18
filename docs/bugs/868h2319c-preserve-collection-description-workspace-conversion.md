# Preserve collection description when converting Workspace to Collection

When converting a Workspace to a Collection, ensure the collection description is preserved.

[ClickUp: 868h2319c](https://app.clickup.com/t/868h2319c)

## Analysis

**Status: Bug confirmed**

- `ConvertToCollectionComponent` overrides `updateObject()` and returns only `{ name, author, skills }` — **description is omitted** (line 33–37).
- Parent `CollectionFormComponent` includes `description` in `updateObject()` and in the form (`collectionForm` has `description` control).
- `convertToCollectionAction()` stores only skill UUIDs in localStorage; workspace name/description are not passed.
- The convert-to-collection form starts empty; workspace name and description are never pre-populated.

**Root cause**: (1) `updateObject()` override drops description; (2) workspace metadata (name, description) is not passed to the convert page.

**Fix**: (1) Include `description: formValues.description` in `ConvertToCollectionComponent.updateObject()`. (2) Pass workspace name and description when navigating (e.g. via router state or localStorage) and pre-populate the form in `ngOnInit` before the user submits.
