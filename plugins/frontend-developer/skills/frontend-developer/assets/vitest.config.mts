import { defineConfig, type Plugin } from 'vitest/config';
import react from '@vitejs/plugin-react';

// Server-only modules (app/lib/env.ts, api.ts, data.ts) import 'server-only',
// whose default build throws outside a Server Component. Replace it with an
// empty module so those modules can be imported by tests.
const stubServerOnly = (): Plugin => ({
  name: 'stub-server-only',
  enforce: 'pre',
  resolveId: (id) => (id === 'server-only' ? '\0server-only' : null),
  load: (id) => (id === '\0server-only' ? 'export {};' : null),
});

export default defineConfig({
  plugins: [stubServerOnly(), react()],
  resolve: {
    // Resolves the "@/*" alias from tsconfig.json.
    tsconfigPaths: true,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./vitest.setup.ts'],
    include: ['**/*.test.{ts,tsx}'],
    exclude: ['node_modules/**', '.next/**', 'e2e/**'],
    restoreMocks: true,
  },
});
