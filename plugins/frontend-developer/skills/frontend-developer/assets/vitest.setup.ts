import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { server } from './mocks/server';

// The API base URL the MSW handlers in mocks/handlers.ts answer on.
process.env.API_BASE_URL = 'http://api.test';

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
