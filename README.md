# Sidekick Monorepo

A single repository for Sidekick apps.

## What Is Here

- `apps/android` - Android project (Wear app + phone app)
- `apps/web-landing` - static landing page built/served with Bun

## Repo Shape

```text
sidekick/
|-- apps/
|   |-- android/
|   |   |-- mobile/
|   |   `-- wear/
|   `-- web-landing/
|-- package.json
|-- bun.lock
`-- README.md
```

## Platform Relationship

```text
+-------------------+              +----------------------+
| apps/android/wear | <----------> | apps/android/mobile  |
| primary app       |              | companion (minimal)  |
+-------------------+              +----------------------+

+-------------------+
| apps/web-landing  |
| static website    |
+-------------------+
```

## Quick Start

### Web landing

```bash
cd apps/web-landing
bun install
bun run build
bun run serve
```

### Android

```bash
cd apps/android
./gradlew :wear:assembleDebug
./gradlew :mobile:assembleDebug
```

## Root Commands

```bash
bun run web:build
bun run web:serve
bun run android:wear:build
bun run android:mobile:build
```

## Deploy Web Landing to GitHub Pages

The repo includes a workflow at `.github/workflows/deploy-web-landing.yml` that:
- builds `apps/web-landing/dist`
- deploys to GitHub Pages on pushes to `main`
- writes a `CNAME` file for `sidekick.watch`

### One-time GitHub setup

1. Push this repo to GitHub.
2. In GitHub: `Settings -> Pages`
3. Under `Source`, select `GitHub Actions`.
4. In `Settings -> Environments -> github-pages`, approve deploy protections if required.
