# frontend-developer

Builds Next.js App Router frontends in TypeScript, structured like the
[next-learn dashboard example](https://github.com/vercel/next-learn/tree/main/dashboard/final-example):
`app/` for routes, `app/ui/` for components, `app/lib/` for data access and types.

Assumes the backend is a **separate service** — the skill's data layer is a typed `fetch`
wrapper against that API, and database clients, ORMs and auth backends are explicitly out of
scope. TypeScript strict mode, tests (Vitest + React Testing Library + Playwright) and linting
(ESLint flat config + Prettier) are treated as gates, not options.

## Skills

| Skill | Triggers on |
| --- | --- |
| `frontend-developer` | "build a Next.js app", "add a page/route/component", "fetch data in a server component", "add a form", "write a server action", frontend scaffolding, Tailwind/test/lint setup |

The skill body is a lean workflow; the detail lives in `references/` (structure, data layer,
forms and actions, components, testing, tooling) and copyable config templates in `assets/`.

## Install

```
/plugin marketplace add <owner>/skills
/plugin install frontend-developer@alex-skills
```
