# frontend-developer

Builds Next.js App Router frontends in TypeScript, structured like the
[next-learn dashboard example](https://github.com/vercel/next-learn/tree/main/dashboard/final-example):
`app/` for routes, `app/ui/` for components, `app/lib/` for data access and types.

Assumes the backend is a **separate service**: a typed `fetch` wrapper against that API called
from the server, Auth0 for the user session following the BFF token-handler pattern (tokens
stay in a server-side cookie, protected segments gated in `proxy.ts`), and no database clients
or ORMs. Targets Next 16
Cache Components — `'use cache'` with `cacheTag`/`cacheLife`, `updateTag` after writes, and
partially prerendered pages. TypeScript strict mode, tests (Vitest + React Testing Library +
Playwright) and linting (ESLint flat config + Prettier) are treated as gates, not options.

## Skills

| Skill | Triggers on |
| --- | --- |
| `frontend-developer` | "build a Next.js app", "add a page/route/component", "fetch data in a server component", "add a form", "write a server action", frontend scaffolding, Tailwind/test/lint setup |

The skill body is a lean workflow; the detail lives in `references/` (structure, data layer,
forms and actions, auth, metadata, components, testing, tooling) and copyable config templates
in `assets/`.

## Install

```
/plugin marketplace add valyalkin/skills
/plugin install frontend-developer@alex-skills
```
