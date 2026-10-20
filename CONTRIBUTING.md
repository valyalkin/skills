# Adding a plugin

1. **Copy the reference plugin.** `plugins/conventional-commits` is written to be a working
   template: a manifest, one skill with trigger-rich frontmatter, and one `references/` file.

   ```sh
   cp -R plugins/conventional-commits plugins/my-plugin
   ```

2. **Rewrite `plugins/my-plugin/.claude-plugin/plugin.json`.** `name` must equal the directory
   name. Keep `version` and bump it when behavior changes.

3. **Rename and rewrite the skill.** One directory per skill under `skills/`, and the
   frontmatter `name` must equal that directory name.

4. **Register it in `.claude-plugin/marketplace.json`** with `name`, `description`, `author`,
   `source` (`./plugins/my-plugin`) and a `category`. The `name` and `description` must match
   the plugin manifest.

5. **Validate and test.**

   ```sh
   python3 scripts/validate.py
   claude plugin validate .            # optional, checks the official schema
   ```

   Then install from the local path (`/plugin marketplace add .` in a session rooted here),
   start a fresh session, and check the skill both appears and triggers on one of its
   description phrases.

## Writing the description

The `description` is the whole basis on which the skill is chosen — the body is not visible
until after that decision. So:

- Write it in the third person: "This skill should be used when the user asks to ...".
- Quote the literal phrases a user would type. Those are the trigger surface.
- Say what the skill is *not* for when it sits next to a similar one.
- Keep it one sentence or two, specific rather than aspirational.

## Writing the body

- Lead with what the skill produces, then the workflow steps.
- Include a "when not to use" section whenever the skill could fire too eagerly.
- Move anything long — tables, full option lists, worked examples — into `references/` and
  point at it from the body by relative path. Keep `SKILL.md` roughly under 500 lines.
- Put deterministic work in `scripts/` rather than describing it in prose.
- Prefer imperative instructions over explanation: the reader is an agent doing the task.
