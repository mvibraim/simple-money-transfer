---
description: Scaffold a docs/features/NN-slug.md spec and register it in 00-index.md
argument-hint: "[NN] [slug]"
disable-model-invocation: true
---

Scaffold a new feature spec for this project's PR-per-feature workflow, given
`$1` (two-digit feature number, e.g. `21`) and `$2` (a short kebab-case slug,
e.g. `rate-limiting`).

## What "feature spec" means here

`docs/features/00-index.md` is the roadmap: a table of every feature branch in
dependency order, plus a `## Status` section. Each `docs/features/NN-slug.md`
is that feature's design doc, written *before* the branch, reviewed alongside
the PR. Read `docs/features/20-docker-compose-stack.md` as the reference shape
before writing a new one — the section order below is load-bearing, match it
exactly.

## Steps

1. Read `docs/features/00-index.md` and the two or three most recent
   `docs/features/NN-*.md` files to confirm the current numbering, the
   previous feature's branch name, and house style.
2. Create `docs/features/$1-$2.md` with this exact section order:

   ```markdown
   # F$1 — <Title Case description>

   **Branch:** `feat/$1-$2` · **Depends on:** F<NN-1>

   ## Goal

   ## Scope

   ## Explicitly not in this feature

   ## Design notes

   ## Verification

   ## Review focus
   ```

   If this feature reverses or replaces a design decision from an earlier
   spec, add a blockquote right after the branch/depends line — see the top
   of `docs/features/17-app-container-stack.md` for the exact shape (bold
   "Supersedes FNN." or "Partially superseded.", then what changed and why),
   and add the same marker to the *old* spec pointing forward.
3. In `docs/features/00-index.md`:
   - Add a row to the roadmap table: `| $1 | \`feat/$1-$2\` | <one-line scope> |`.
   - Update `## Status` — this project's convention is a single collapsed
     range (`| F00–F20 | shipped |`), so extend the range's upper bound
     rather than adding a new row, unless the new feature isn't shipped yet.
4. Don't invent scope. Ask the user for the Goal/Scope content if it wasn't
   given — this skill scaffolds the shape, not the design.
