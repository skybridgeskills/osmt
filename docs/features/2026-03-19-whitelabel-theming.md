# Whitelabel and theming (deployment)

OSMT serves branding and theme values from the API at
`/whitelabel/whitelabel.json`. The Angular app loads this when
`dynamicWhitelabel` is true (default in production).

You can customize the UI by setting environment variables on the **API**
container. The API merges them into the whitelabel JSON response.

## Merge order

Later layers override earlier ones:

1. Built-in defaults from `docker/whitelabel/whitelabel.json` in the API JAR
2. `OSMT_WHITELABEL_JSON` (JSON object as a string)
3. Individual `OSMT_*` environment variables
4. Dynamic auth fields (`loginUrl`, `authMode`, `authProviders`,
   `singleAuthEnabled`) from Spring configuration

## Environment variables

| Variable | JSON field | Default (built-in) | Description |
|----------|------------|--------------------|-------------|
| `OSMT_TOOL_NAME` | `toolName` | `OSMT` | Short name (tab title, header) |
| `OSMT_TOOL_NAME_LONG` | `toolNameLong` | `Open Skills Management Tool` | Tagline next to the logo |
| `OSMT_BRAND_COLOR` | `colorBrandAccent1` | `#1e40af` | Primary brand color (hex) |
| `OSMT_LOGO_URL` | `logoUrl` | `/assets/images/logo-dark.svg` | Logo `src` value |
| `OSMT_LICENSE_PRIMARY` | `licensePrimary` | `Copyright © OSMT Contributors` | Footer line 1 |
| `OSMT_LICENSE_SECONDARY` | `licenseSecondary` | `All rights reserved.` | Footer line 2 |
| `OSMT_WHITELABEL_JSON` | *(many)* | *(none)* | Full JSON overlay |

## Logo options

`OSMT_LOGO_URL` (or `logoUrl` inside `OSMT_WHITELABEL_JSON`) can be:

### Relative path (volume mount)

Mount a file where the browser can load it. With `WHITELABEL_PATH`, static
files under that directory are served at `/whitelabel/...`.

```yaml
services:
  api:
    volumes:
      - ./branding/my-logo.svg:/opt/osmt/whitelabel/my-logo.svg
    environment:
      WHITELABEL_PATH: /opt/osmt/whitelabel
      OSMT_LOGO_URL: /whitelabel/my-logo.svg
```

### Absolute URL

```yaml
environment:
  OSMT_LOGO_URL: "https://cdn.example.com/osmt-logo.svg"
```

### Data URI

Suitable for small SVGs; watch env size limits on your platform.

```yaml
environment:
  OSMT_LOGO_URL: "data:image/svg+xml;base64,PHN2Zy..."
```

## Full JSON override

Use `OSMT_WHITELABEL_JSON` when you need every field (including optional ones
like `poweredBy`):

```yaml
environment:
  OSMT_WHITELABEL_JSON: >
    {
      "toolName": "SkillsHub",
      "toolNameLong": "SkillsHub Skills Management",
      "colorBrandAccent1": "#059669",
      "logoUrl": "https://example.com/logo.svg",
      "licensePrimary": "© 2026 Example Org",
      "licenseSecondary": "All rights reserved.",
      "poweredBy": "Powered by",
      "poweredByUrl": "https://github.com/example/osmt",
      "poweredByLabel": "OSMT"
    }
```

Individual `OSMT_*` variables still override keys present in
`OSMT_WHITELABEL_JSON` if both are set.

## Brand color and accessibility

The UI adjusts text on brand-colored surfaces for contrast. If white text on
your `colorBrandAccent1` is below roughly 4.5:1 contrast, the app switches to
dark text on that surface.

## Minimal example

```yaml
services:
  api:
    image: osmt-api:latest
    environment:
      OSMT_TOOL_NAME: "SkillsHub"
      OSMT_TOOL_NAME_LONG: "SkillsHub Skills Management"
      OSMT_BRAND_COLOR: "#059669"
      OSMT_LICENSE_PRIMARY: "© 2026 SkillsHub Inc."
```

The UI container does not need these variables; it reads branding from the API.
