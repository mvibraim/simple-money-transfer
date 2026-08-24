---
description: Commit staged/relevant changes with a descriptive message and open a PR
argument-hint: "[branch-name]"
disable-model-invocation: true
allowed-tools: Bash(git add:*) Bash(git status:*) Bash(git diff:*) Bash(git commit:*) Bash(git push:*) Bash(git branch:*) Bash(git log:*) Bash(gh pr create:*)
---

Commit the current changes and open a PR against `main`, following this
repo's actual conventions (see `git log --oneline --merges` for the pattern):

1. Never commit directly to `main` — if the current branch is `main`, create
   and switch to a new branch first. Use `$1` as the branch name if given;
   otherwise infer a short kebab-case name from the diff, prefixed by what
   kind of change it is: `feat/NN-slug` (numbered feature, matching
   `docs/features/`), `fix/slug`, `docs/slug`, `ci/slug`, `chore/slug`, or
   `style/slug`.
2. Review `git status` and `git diff` before staging — stage only the files
   that belong to this change, never a blanket `git add -A`. Flag anything
   that looks like a secret (`.env`, credentials) before adding it.
3. Write a commit message as an imperative one-line subject describing the
   *why*, not a changelog of the diff — match the tone of recent commits
   (`git log --oneline -10`).
4. Push with `-u` and open the PR with `gh pr create`. PR body: a short
   Summary section and, if there's anything worth manually verifying, a Test
   plan checklist — see recent PR bodies (`gh pr view <n>`) for the shape.
   Do not merge the PR yourself.
