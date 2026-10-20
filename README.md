# alex-skills

A collection of agent skills for coding work, distributed as a
[Claude Code plugin marketplace](https://docs.claude.com/en/docs/claude-code/plugins). Each
plugin is small and single-purpose, so you install only the skills you want.

## Install

```
/plugin marketplace add valyalkin/skills
/plugin install conventional-commits@alex-skills
```

`valyalkin/skills` is this repository; a local path works too, which is useful while
developing:

```
/plugin marketplace add /Users/alex/Documents/Projects/skills
```

Then `/plugin` lists what is installed, and `/plugin marketplace update alex-skills` pulls new
versions.

## Plugins

| Plugin | Skills | What it does |
| --- | --- | --- |
| [`conventional-commits`](plugins/conventional-commits) | `conventional-commits` | Commit messages and PR titles in Conventional Commits form, derived from the staged diff |
| [`frontend-developer`](plugins/frontend-developer) | `frontend-developer` | Next.js App Router frontends in TypeScript, structured like the next-learn dashboard example, with mandatory tests and linting |

## How skills get used

A skill is a directory with a `SKILL.md`: YAML frontmatter (`name`, `description`) plus
instructions. Claude sees only the `description` until it decides the skill is relevant, then
loads the body — and loads files under `references/` only when the body points at them. So the
`description` carries the trigger phrases, `SKILL.md` stays lean, and long material lives in
`references/`.

Skills load automatically when the description matches the task. They can also be invoked by
name, as `/<skill-name>`.

## Repository layout

```
.claude-plugin/marketplace.json   # the marketplace manifest: lists every plugin
plugins/<plugin>/
  .claude-plugin/plugin.json      # the plugin manifest
  skills/<skill>/SKILL.md         # the skill itself
  skills/<skill>/references/      # loaded on demand
scripts/validate.py               # structural checks, run in CI
```

See [CONTRIBUTING.md](CONTRIBUTING.md) to add a plugin.

## License

MIT
