# Commit types, with selection rules

Load this when the right type is genuinely unclear, or when rewriting a batch of messages.

| Type | Use for | Not for |
| --- | --- | --- |
| `feat` | New capability visible to a user or caller of the code | Internal refactors with no behavior change |
| `fix` | Correcting wrong behavior | Changing behavior that was never wrong (that is `feat` or `refactor`) |
| `perf` | Same behavior, measurably faster or lighter | Speedups that also change semantics (`refactor` or `feat`) |
| `refactor` | Restructuring with no behavior change | Anything that fixes a bug on the way (`fix`) |
| `docs` | Comments, README, docstrings, docs site | Code changes that happen to touch a comment |
| `test` | Adding or changing tests only | A fix plus its regression test (that is `fix`) |
| `build` | Build system, dependencies, packaging | CI configuration (`ci`) |
| `ci` | CI configuration and pipelines | Local tooling scripts (`chore` or `build`) |
| `chore` | Housekeeping with no production code effect | Anything users can observe |
| `revert` | Reverting an earlier commit; body names the reverted SHA | Fixing forward (`fix`) |

## Tie-breakers

- **A fix and its test in one commit** → `fix`. The test is part of the fix.
- **A refactor that enables a feature, committed together** → `feat`. The feature is the point.
- **A dependency bump that fixes a bug** → `fix` with a body naming the dependency, since the
  observable effect is the fix.
- **Mass formatting plus one real change** → split. If it truly cannot be split, use the type
  of the real change and say in the body that formatting is included.

## Before / after

| Weak | Better |
| --- | --- |
| `fix: bug fix` | `fix: reject negative page sizes in list handler` |
| `feat: changes per review feedback` | `refactor(api): collapse duplicate validation helpers` |
| `chore: updates` | `build: bump golang.org/x/net to 0.38.0` |
| `fix: Fixed the thing where uploads failed sometimes.` | `fix(upload): retry on 503 from object store` |

## Subject checklist

- Imperative mood (`add`, not `adds` or `added`).
- No trailing period.
- Under ~72 characters, ideally under 50.
- Names the thing changed, not the activity (`add retry to upload client`, not
  `work on upload client`).
