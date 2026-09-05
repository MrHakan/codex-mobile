# Codex cloud agent + Android client

Codex runs on a GitHub Actions runner; the phone only drives it through the
GitHub REST API. Nothing in this feature compiles or executes Codex on Android.

```
Android app  ──POST /actions/workflows/codex-cloud-agent.yml/dispatches──▶  GitHub Actions
     ▲                                                                          │
     │                                                                   checkout repo
     └──GET /actions/runs/… , GET /pulls?head=…──────────────────────────  codex exec
                                                                           commit + PR
```

Two pieces:

- [`.github/workflows/codex-cloud-agent.yml`](../.github/workflows/codex-cloud-agent.yml) —
  the cloud runner. Checks out a repository, runs Codex non-interactively
  against a prompt, and opens a pull request with whatever changed.
- [`android/`](../android) — a Kotlin/Compose client with no native code. It
  triggers that workflow, follows the run, and links to the resulting pull
  request.

## The Codex CLI in non-interactive mode

`codex exec` is the scriptable entry point. The flags the workflow relies on
(verified against `@openai/codex` 0.153.4):

| Flag                                         | Why the runner uses it                                                                                        |
| -------------------------------------------- | ------------------------------------------------------------------------------------------------------------- |
| `-` (positional prompt)                      | Reads the prompt from stdin, so it never passes through shell quoting or the process argument list.           |
| `--dangerously-bypass-approvals-and-sandbox` | The runner VM is already isolated and disposable. `codex exec` never prompts for approvals in any case.       |
| `--json`                                     | Emits JSONL events on stdout; the run archives them as `codex-events.jsonl`.                                  |
| `-o, --output-last-message <FILE>`           | Writes the agent's closing message, which becomes the "Agent summary" in the job summary and the PR body.     |
| `--skip-git-repo-check`                      | Belt and braces: the checkout is a git repo, but this keeps the step from failing if that ever stops holding. |
| `-m, --model <MODEL>`                        | Optional, forwarded from the app's advanced field.                                                            |

Other flags worth knowing: `-s, --sandbox <read-only|workspace-write|danger-full-access>`
selects Codex's own sandbox (superfluous once approvals and sandboxing are
bypassed), `-c key=value` overrides any config value, and `--output-schema`
constrains the final message to a JSON Schema.

`codex exec` exits non-zero when the turn fails, which is what makes the run
status alone a reliable success signal.

## Model authentication

The workflow pipes the `OPENAI_API_KEY` secret into
`codex login --with-api-key`, which reads the key from stdin. An API key beats
ChatGPT OAuth here because there is no browser on a headless runner.

## GitHub authentication

The workflow writes back with `GITHUB_TOKEN` by default. Set a `CODEX_GH_TOKEN`
secret (a GitHub App installation token or a fine-grained PAT) when you want to
operate on a **different** repository than the one hosting the workflow, or want
least-privilege, per-repo access. A GitHub App is the better fit as soon as more
than one repository is involved: installation tokens are short-lived and scoped
per repository.

## Repository setup

1. Add the `OPENAI_API_KEY` secret to the repository that hosts the workflow.
2. Optionally add `CODEX_GH_TOKEN` for cross-repository work.
3. In **Settings → Actions → General**, enable "Allow GitHub Actions to create
   and approve pull requests" — otherwise `gh pr create` fails with the default
   token.
4. Merge the workflow to the default branch. `workflow_dispatch` through the API
   only works once the workflow exists there.

## Triggering

`workflow_dispatch` (what the app uses):

```bash
gh workflow run codex-cloud-agent.yml \
  --repo <owner>/<repo> \
  --ref main \
  -f prompt='Add a "Getting started" section to README.md' \
  -f base_branch=main \
  -f task_id=manual-001
```

`repository_dispatch`, for clients that cannot use `workflow_dispatch`:

```bash
gh api repos/<owner>/<repo>/dispatches \
  -f event_type=codex-task \
  -F 'client_payload[prompt]=Add a "Getting started" section to README.md' \
  -F 'client_payload[base_branch]=main' \
  -F 'client_payload[task_id]=manual-002'
```

Inputs: `prompt` (required), `base_branch`, `task_id`, `target_repo`, `model`,
`codex_version`. `task_id` must match `^[A-Za-z0-9._-]{1,64}$` because it ends up
in the branch name `codex/<task_id>`; the workflow rejects anything else.

## How the app finds its run

Neither trigger returns the id of the run it created. The workflow therefore
stamps the task id into `run-name:` ("Codex task &lt;id&gt;"), and the app matches
`GET /actions/workflows/codex-cloud-agent.yml/runs` on that exact name. Once
matched it stores the `run_id` and polls it directly. The pull request is found
by head branch (`GET /pulls?head=<owner>:codex/<task_id>`), which is immediate,
unlike a search-index query for the `codex-task-id:` marker in the PR body.

## Outcomes

- **Files changed** — the run pushes `codex/<task_id>` and opens a pull request.
- **No files changed** — the run succeeds and says so in the job summary; no
  branch, no pull request.
- **Codex failed** — the run fails, and the app shows the task as failed with a
  link to the run.

Every run uploads `codex-events.jsonl`, the last agent message, the prompt, and
the diff stat as an artifact.
