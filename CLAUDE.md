# Sidekick

AI assistant app. Monorepo with `android` (Wear OS) and `web`.

## Android / Wear OS

- Gradle wrapper: `android/gradlew` (not project root)
- JAVA_HOME: `/Applications/Android Studio.app/Contents/jbr/Contents/Home` (no system JDK installed)
- adb: `~/Library/Android/sdk/platform-tools/adb`
- Build release APK: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" android/gradlew -p android :wear:assembleRelease`
- APK output: `android/wear/build/outputs/apk/release/wear-release.apk`
- Install on watch: `~/Library/Android/sdk/platform-tools/adb -s <device> install -r <apk>`
- Signing config is in `android/wear/build.gradle.kts` (hardcoded keystore, not committed)

### Architecture

- OkHttp for HTTP, no Retrofit
- Two backends: Spacebot (`SpacebotRepository`) and OpenAI-compatible (`OpenAIRepository`)
- OpenAI backend uses SSE streaming (`sendMessageStreaming`) with `Flow<String>`
- ViewModel: `ChatViewModel` — manages conversations, collects streaming chunks into UI state
- Settings (base URL, model, auth token) stored via `SettingsRepository` (DataStore)
- Entry point: `MainActivity` — HorizontalPager with home (chat) + settings pages
