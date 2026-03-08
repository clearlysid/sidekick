# Sidekick Monorepo

A single repository for Sidekick apps.

## What Is Here

- `apps/android` - Android project (Wear OS app)
- `apps/web` - static landing page built/served with Bun

## Repo Shape

```text
sidekick/
|-- apps/
|   |-- android/
|   |   └── wear/
|   └── web/
|-- package.json
|-- bun.lock
└── README.md
```

## Quick Start

### Web

```bash
cd apps/web
bun install
bun run build
bun run serve
```

### Android

```bash
cd apps/android
./gradlew :wear:assembleDebug
```

## Root Commands

```bash
bun run web:build
bun run web:serve
bun run android:wear:build
```

## Deploy Web to GitHub Pages

The repo includes a workflow at `.github/workflows/deploy-web.yml` that:
- builds `apps/web/dist`
- deploys to GitHub Pages on pushes to `main`
- writes a `CNAME` file for `sidekick.watch`

### One-time GitHub setup

1. Push this repo to GitHub.
2. In GitHub: `Settings -> Pages`
3. Under `Source`, select `GitHub Actions`.
4. In `Settings -> Environments -> github-pages`, approve deploy protections if required.
