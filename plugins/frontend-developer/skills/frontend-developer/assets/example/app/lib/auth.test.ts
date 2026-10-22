import { http, HttpResponse } from 'msw';
import { expect, it, vi } from 'vitest';
import { server } from '@/mocks/server';
import { authedFetch } from '@/app/lib/auth';

// vi.mock is hoisted above the imports, so every spy a factory closes over
// must be created inside vi.hoisted - a plain top-level const throws
// "Cannot access '...' before initialization".
const { getAccessToken, redirect } = vi.hoisted(() => ({
  getAccessToken: vi.fn(async () => ({ token: 'test-token' })),
  redirect: vi.fn(() => {
    throw new Error('NEXT_REDIRECT');
  }),
}));

vi.mock('@/app/lib/auth0', () => ({ auth0: { getAccessToken } }));
vi.mock('next/navigation', () => ({ redirect }));

it('attaches the access token as a bearer header', async () => {
  let seen: string | null = null;
  server.use(
    http.get('http://api.test/me/summary', ({ request }) => {
      seen = request.headers.get('authorization');
      return HttpResponse.json({ outstanding: 1 });
    }),
  );

  await expect(authedFetch('/me/summary')).resolves.toMatchObject({
    outstanding: 1,
  });
  expect(seen).toBe('Bearer test-token');
});

it('redirects to login when the API rejects the token', async () => {
  server.use(
    http.get(
      'http://api.test/me/summary',
      () => new HttpResponse(null, { status: 401 }),
    ),
  );

  await expect(authedFetch('/me/summary')).rejects.toThrow('NEXT_REDIRECT');
  expect(redirect).toHaveBeenCalledWith('/auth/login');
});
