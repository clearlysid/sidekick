# Sidekick Monorepo

A single repository for Sidekick apps across platforms.

## What Is Here

- `apps/android` - Android project (Wear app + phone app)
- `apps/web-landing` - static landing page built/served with Bun
- `apps/apple` - placeholders for future iOS/watchOS apps
- `packages` - shared contracts/assets (placeholders)
- `tooling` - local scripts/utilities

## Repo Shape

```text
sidekick/
|-- apps/
|   |-- android/
|   |   |-- mobile/
|   |   `-- wear/
|   |-- web-landing/
|   `-- apple/
|       |-- ios/
|       `-- watchos/
|-- packages/
|   |-- shared-contracts/
|   `-- shared-assets/
|-- tooling/
|   `-- scripts/
|-- package.json
`-- README.md
```

## Platform Relationship

```text
                +-------------------+
                |  apps/web-landing |
                |   static website  |
                +---------+---------+
                          |
                          v
+-------------------+   Sidekick   +----------------------+
| apps/android/wear | <----------> | apps/android/mobile  |
| primary product   |              | companion (minimal)  |
+-------------------+              +----------------------+

+-------------------+
| apps/apple/*      |
| reserved/future   |
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
