# Codex Mobile

An Android client for [Codex Cloud](../docs/codex-cloud-client.md) — the same
backend `codex cloud` and chatgpt.com/codex use. Sign in with your **ChatGPT
account**, start a task on a connected repository, and watch it work. Tasks run
in OpenAI's cloud, not on the phone: no API key, no runner, no server.

## Requirements

- Android 8.0 (API 26) or newer.
- A ChatGPT plan that includes Codex, with at least one environment connected at
  chatgpt.com/codex/settings.

## Building

```bash
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/`. `assembleRelease` builds
the minified variant; it is signed only when `ANDROID_KEYSTORE_PATH`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` are set in the environment
(the release workflow does that from repository secrets), and unsigned
otherwise. `-PversionName=0.2.0 -PversionCode=200` set the version; the release
workflow derives both from the `android-v*` tag.

## Releasing

The release workflow is driven by tags and needs the signing secrets set on the
repository first:

1. Create a keystore once and keep it somewhere safe:

   ```bash
   keytool -genkeypair -v -keystore release.jks -alias codexmobile \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Add four repository secrets (Settings -> Secrets and variables -> Actions):
   `ANDROID_KEYSTORE_BASE64` (`base64 -w0 release.jks`), `KEYSTORE_PASSWORD`,
   `KEY_ALIAS`, `KEY_PASSWORD`.

3. Tag and push. The tag must exist before the workflow runs, and its name is
   where the version comes from:

   ```bash
   git tag android-v0.1.0
   git push origin android-v0.1.0
   ```

Pushing the tag starts the build; running the workflow by hand takes the same
`android-v*` tag name as its input. Anything else fails immediately with a
message saying so.

Until you cut a release, the debug APK from the latest `android-ci` run on
`main` is installable: open the run on GitHub and download the
`codex-mobile-debug-apk` artifact.

## Signing in

Tap **Sign in with ChatGPT**. The app shows a short code, you approve it at
`auth.openai.com/codex/device` in a browser, and the app receives the tokens —
the same device flow as `codex login --device-auth`.

Tokens live in `EncryptedSharedPreferences` (Keystore-backed) and are excluded
from cloud backup and device transfer. They leave the device only as request
headers to `auth.openai.com` and `chatgpt.com`.

## Using it

1. **New task** — pick an environment, optionally a branch, describe the work.
2. **Runs** — the task list shows status, `+added −removed` and a PR marker.
3. **Thread** — your prompt, Codex's replies, the unified diff, and buttons for
   the pull request and the web view.

Polling runs while the app is in the foreground (4s backing off to 20s). Closing
the app does not stop the task; it keeps running in the cloud.

Follow-up messages and "Create PR" are not in the app — the Codex CLI has no
endpoint for them either, so the thread links out to chatgpt.com instead of
guessing at one.

## Layout

| Path            | What lives there                                                       |
| --------------- | ---------------------------------------------------------------------- |
| `auth/`         | ChatGPT device flow, token refresh, Keystore-backed storage.           |
| `data/`         | Codex Cloud wire models, Retrofit API, and `TaskMapper`.               |
| `ui/`           | `AppViewModel` plus the Compose screens and the diff view.             |
| `app/src/test/` | JVM tests for the device flow, refresh, payload shapes and the mapper. |

## Dependency versions

Versions are pinned in `gradle/libs.versions.toml` to a set this project builds
and tests against (AGP 8.7.2, Kotlin 2.0.21, Compose BOM 2024.10.01). Android
Lint reports newer releases as warnings; bump them deliberately rather than to
silence the warning.
