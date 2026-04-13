# Split deployment: public read-only and authoring instances

Some deployments run OSMT twice: a **public read-only** instance for browsing and
shareable URLs, and a separate **authoring** instance with full sign-in and
write access. This is optional; single-instance installs are unchanged.

## Profiles and meta-configuration

- **`readonly`**: Public instance. Disables Flyway, disables server sessions
  (`spring.session.store-type=none`), sets `app.instanceType=read-only`, and
  uses `ReadOnlySecurityConfig` so only safe, public routes are allowed; other
  API calls return **403** with a
  JSON error when blocked.

- **Authoring instance**: Use your normal stack (e.g. `oauth2` or
  `oauth2,single-auth`). Set `app.instanceType=writable` (default) and use
  different branding (color, logo) plus optional login copy so authors can see
  they are on the editing instance.

Do **not** combine `readonly` with `oauth2` or `single-auth` on the same
process; the `oauth2` and `single-auth` security configs are disabled when
`readonly` is active (`oauth2 & !readonly`, `single-auth & !oauth2 & !readonly`).

## Environment variables

| Variable | Purpose |
|----------|--------|
| `OSMT_READ_ONLY_MODE` | `true`/`false`; read-only profile also sets this via properties |
| `OSMT_INSTANCE_TYPE` | `read-only` or `writable` (exposed in whitelabel JSON) |
| `OSMT_WRITABLE_INSTANCE_URL` | Base URL of the authoring instance (403 message and login link) |
| `OSMT_WRITABLE_INSTANCE_NAME` | Label for the link to the authoring login page |
| `OSMT_READ_ONLY_MESSAGE` | Custom copy on the read-only `/login` page |
| `OSMT_AUTHORING_WELCOME_MESSAGE` | Extra copy on the authoring login page (writable only) |

Whitelabel env vars (`OSMT_BRAND_COLOR`, `OSMT_LOGO_URL`, etc.) can differ per
instance so the authoring UI is visually distinct.

## Frontend behavior

- **Read-only**: No Login entry in the desktop or mobile nav. The `/login` page
  shows an explanation and an optional link to `{writableInstanceUrl}/login`.
- **Writable**: Standard OAuth/single-auth login; optional
  `authoringWelcomeMessage` below the title.

## Database

Use a DB user with **SELECT-only** privileges for the read-only deployment; the
authoring instance uses a user that can run migrations and writes.

## Related

- [Authentication](2026-02-28-auth.md)
- [Whitelabel & theming](2026-03-19-whitelabel-theming.md)
