# Codex Mobile

An Android client for the [Codex cloud agent](../docs/codex-cloud-agent.md). It
sends a prompt to `codex-cloud-agent.yml` on GitHub Actions, follows the run,
and links to the pull request Codex opens. Codex never runs on the phone — the
app is pure Kotlin and talks only to `api.github.com` over HTTPS. No NDK, no
bundled binaries.

## Requirements

- Android 8.0 (API 26) or newer.
- A GitHub token that can trigger the workflow and read the repositories you
  want to work on.

## Building

```bash
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/`. `assembleRelease` builds
the minified variant; it is signed only when `ANDROID_KEYSTORE_PATH`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` are set in the environment
(the release workflow does that from repository secrets), and unsigned
otherwise.

Optional build inputs:

- `-PversionName=0.2.0 -PversionCode=200` — set by the release workflow from the
  `android-v*` tag.
- `-Pcodexmobile.githubClientId=<id>` (or the same key in `gradle.properties`) —
  the client id of a GitHub OAuth app. Without it the app hides device-flow
  sign-in and only accepts a pasted token.

## Signing in

Two options on the sign-in screen:

- **Device flow** — shown only when the build carries an OAuth client id. The
  app displays a user code, you approve it on github.com, and it exchanges the
  code for a token with the `repo` and `workflow` scopes.
- **Personal access token** — a fine-grained token needs _Actions: read and
  write_ (to dispatch the workflow), _Contents: read_ and _Pull requests: read_
  on the repositories involved. A classic token needs `repo` and `workflow`.

Either way the token is stored with `EncryptedSharedPreferences` (Keystore-backed)
and is excluded from cloud backup and device transfer. It leaves the device only
as an `Authorization` header to `api.github.com`.

## Using it

1. **New task** — pick a repository, pick a base branch, describe the task.
2. **Run on GitHub Actions** — the app dispatches the workflow in the controller
   repository (Settings tab; `MrHakan/codex-mobile` by default) and starts
   polling.
3. **Runs** — every task with its live status. Open one to see the workflow
   steps, the prompt, a link to the run, and the pull request once it exists.

Polling runs while the app is in the foreground and backs off from 4s to 20s.
Closing the app stops it; reopening resumes polling for anything unfinished.

## Layout

| Path                | What lives there                                                             |
| ------------------- | ---------------------------------------------------------------------------- |
| `auth/`             | `SecureTokenStore` (Keystore-backed), device flow, sign-in.                   |
| `data/`             | GitHub REST models, the Retrofit interface, and `CodexTaskRepository`.        |
| `ui/`               | `AppViewModel` plus the Compose screens.                                      |
| `app/src/test/`     | JVM tests for dispatch, run matching, and pull-request lookup (MockWebServer).|

## Dependency versions

Versions are pinned in `gradle/libs.versions.toml` to a set this project builds
and tests against (AGP 8.7.2, Kotlin 2.0.21, Compose BOM 2024.10.01). Android
Lint reports newer releases as warnings; bump them deliberately rather than to
silence the warning.
