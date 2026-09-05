# Codex Mobile: a Codex Cloud client for Android

The Android app in [`android/`](../android) is a client for **Codex Cloud** —
the same backend `codex cloud` and chatgpt.com/codex talk to. Tasks run in
OpenAI's cloud environments on the signed-in **ChatGPT account**. There is no
API key, no GitHub Actions runner, and no server to operate.

```
Android app ──POST /backend-api/wham/tasks──▶ Codex Cloud ──▶ your repo
     ▲                                             │
     └──GET /wham/tasks/list, /wham/tasks/{id}─────┘   PR opened by Codex
```

## Sign-in: the ChatGPT device flow

Identical to `codex login --device-auth`, against `https://auth.openai.com`
with the Codex CLI's OAuth client id (`app_EMoamEEZ73f0CkXaXp7hrann`):

1. `POST /api/accounts/deviceauth/usercode` with `{"client_id": "…"}` returns
   `{device_auth_id, user_code, interval}` — note `interval` arrives as a
   _string_.
2. The user opens `https://auth.openai.com/codex/device` and types the code.
3. `POST /api/accounts/deviceauth/token` with `{device_auth_id, user_code}` is
   polled every `interval` seconds. It answers **403**
   (`deviceauth_authorization_pending`) until approval, then returns
   `{authorization_code, code_challenge, code_verifier}` — the server keeps the
   PKCE pair, so the client never generates one.
4. `POST /oauth/token` (form-encoded, `grant_type=authorization_code`,
   `redirect_uri=https://auth.openai.com/deviceauth/callback`) returns
   `id_token`, `access_token` and `refresh_token`.

The `id_token` is a JWT whose `https://api.openai.com/auth` claim carries
`chatgpt_account_id`, needed as a header on every backend call, plus the plan
type and email shown in the UI.

Access tokens are short-lived. On a 401 the app posts
`{client_id, grant_type: "refresh_token", refresh_token}` to
`https://auth.openai.com/oauth/token` and retries once; a rejected refresh token
means signing in again.

Steps 1 and 3 were verified live against auth.openai.com while building this;
the rest is mirrored from `codex-rs/login`.

## Talking to Codex Cloud

Base URL `https://chatgpt.com/backend-api/`. Every request carries
`Authorization: Bearer <access_token>` and `ChatGPT-Account-Id: <account id>`.

| Call                                          | Used for                                                   |
| --------------------------------------------- | ---------------------------------------------------------- |
| `GET /wham/environments`                      | The environments (connected repos) in the task composer.   |
| `GET /wham/tasks/list?limit=&environment_id=` | The task list. Rows carry status, diff stats and PR links. |
| `GET /wham/tasks/{id}`                        | The thread: prompt, assistant messages, unified diff.      |
| `POST /wham/tasks`                            | Start a task.                                              |

The create body is exactly what `codex cloud exec` sends:

```json
{
  "new_task": {
    "environment_id": "env_…",
    "branch": "main",
    "run_environment_in_qa_mode": false
  },
  "input_items": [
    {
      "type": "message",
      "role": "user",
      "content": [{ "content_type": "text", "text": "…" }]
    }
  ]
}
```

Reading a task is the fiddly part, because the response is loosely typed.
`TaskMapper` mirrors `codex-rs/backend-client` field for field:

- **Status** — `task_status_display.latest_turn_status_display.turn_status`
  (`completed` → Ready, `failed`/`cancelled` → Failed, anything else → Working),
  falling back to `task_status_display.state`.
- **Diff** — `current_diff_task_turn` wins over `current_assistant_turn`; within
  a turn, `output_items[type=output_diff].diff`, else
  `output_items[type=pr].output_diff.diff`.
- **Assistant messages** — `output_items[type=message].content[].text`, plus
  `worklog.messages[]` whose `author.role` is `assistant`. Content parts arrive
  as bare strings _or_ as `{content_type, text}`, so both decode.
- **Prompt** — `current_user_turn.input_items[type=message]` with role `user` or
  no role, parts joined by a blank line.
- **Diff stats** — `latest_turn_status_display.diff_stats`, falling back to
  counting the unified diff.

This is a private, undocumented API. It can change without notice; when it does,
`codex-rs/cloud-tasks-client` is the reference to re-read.

## What the app does not do

- **Follow-up messages on a task.** The Codex CLI has no endpoint for it either,
  so the thread view links out to chatgpt.com rather than guessing at one.
- **Create PR / apply diff.** Same reason. Existing PRs are linked from the task.
- **Approvals and live streaming.** Cloud tasks run unattended; the app polls
  (4s backing off to 20s) while a task is working.

Both would come from a different architecture — a persistent host running
`codex app-server` that the app talks to over a socket — not from this API.

## Requirements

A ChatGPT plan with Codex access, and at least one environment connected at
chatgpt.com/codex/settings. Without an environment the composer has nothing to
run against.
