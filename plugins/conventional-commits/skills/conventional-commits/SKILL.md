---
name: conventional-commits
description: This skill should be used when the user asks to "commit", "write a commit message", "fix up this commit message", "squash these commits", "name this PR", or when a repository follows the Conventional Commits convention and a commit or pull request title must be produced from the staged changes.
version: 0.1.0
---

# Conventional Commits

Produce commit messages and pull request titles in the form
`<type>(<optional scope>): <description>`, with the subject derived from what the diff
actually does rather than from the prompt that led to the change.

## When to use

- A commit or amend is about to be made in a repository whose history uses Conventional Commits.
- A pull request title or a squash-merge subject needs to be written.
- An existing message needs to be rewritten to fit the convention.

Do not use this skill when the repository's own history clearly uses a different convention
(check `git log --oneline -20` first) — match the repository, not this skill.

## Workflow

1. **Read the change, not the request.** `git diff --staged` (or `git diff` plus
   `git status --short` when nothing is staged yet). The subject describes the effect of the
   diff on the codebase.
2. **Confirm the local convention.** `git log --oneline -20` shows whether scopes are used,
   whether the subject is capitalized, and which types appear in practice.
3. **Pick one type.** `feat`, `fix`, `docs`, `refactor`, `perf`, `test`, `build`, `ci`,
   `chore`, `revert`. If a change honestly needs two types, it should usually be two commits.
4. **Pick a scope** only if the repository uses them — the package, module, or directory the
   change lives in (`feat(parser):`).
5. **Write the subject**: imperative mood, no trailing period, under ~72 characters.
   `add retry to upload client`, not `added retries` or `changes to the client`.
6. **Add a body** only when the *why* is not obvious from the diff: what was wrong, what
   constraint forced this approach. Wrap at 72 columns, blank line after the subject.
7. **Mark breaking changes** with `!` after the type/scope and a `BREAKING CHANGE:` footer
   explaining the migration.
8. **Add footers** for issue references (`Refs: #123`, `Closes: #123`) when the repository
   does so.

## Quick reference

```
feat(auth): support refresh-token rotation
fix: avoid panic on empty header list
refactor(store)!: replace positional Put args with an options struct

BREAKING CHANGE: Put now takes PutOptions; callers passing positional
args must migrate to the struct form.
```

See `references/types.md` for the full type list with selection rules and worked
before/after examples.
