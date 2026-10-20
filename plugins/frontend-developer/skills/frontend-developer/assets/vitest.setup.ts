import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { server } from './mocks/server';

// Every variable app/lib/env.ts requires must be set here, or importing any
// server module fails at module load with a ZodError.
process.env.API_BASE_URL = 'http://api.test';
process.env.AUTH0_AUDIENCE = 'https://api.test';

beforeAll(() => {
  // Any request without a handler is a test writing against the real network.
  // msw v3 option name; on msw v2 it is `onUnhandledRequest`.
  server.listen({ onUnhandledFrame: 'error' });
});

afterEach(() => {
  cleanup();
  server.resetHandlers();
});

afterAll(() => {
  server.close();
});
