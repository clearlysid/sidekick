# Sidekick

Sidekick brings a light, minimal AI Assistant to your wrist ⌚

It is a small app for WearOS that lets you interact with your Clawdbot/OpenClaw/etc. through voice or text. The app tracks your conversations and can notify you once the agent responds. It's really just meant to be a nice, clean, efficent way of summoning your clankers — with a UI that feels unobtrusive.

### Roadmap
- [x] Basic UI
- [x] Spacebot support
- [x] Openclaw support
- [ ] Haptics for replies
- [ ] Voice mode quick launch
- [ ] Tile + Complication
- [ ] Nicer landing page

## Developer Guide

```text
sidekick/
|── android/
|   └── wear/        | Wear OS app
|── web/             | Landing page
└── package.json
```

### Commands

```bash
# For landing page
cd web
bun install
bun run build
bun run serve

# For watch app
cd android
./gradlew :wear:assembleDebug

# For creating builds
bun run web:build
bun run android:wear:build
```

### Notes

1. Pushing to `main` deploys the latest landing page.
2. PRs for bugfixes, support for other agents are welcome.
3. It's my very first Kotlin project, be kind!
