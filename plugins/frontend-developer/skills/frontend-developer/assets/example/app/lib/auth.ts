import 'server-only';
import type { Route } from 'next';
import { redirect } from 'next/navigation';
import { apiFetch, ApiError } from '@/app/lib/api';
import { auth0 } from '@/app/lib/auth0';

/** The current session, or a redirect to Auth0 Universal Login. */
export async function requireSession() {
  const session = await auth0.getSession();

  if (!session) {
    redirect('/auth/login' as Route);
  }

  return session;
}

/**
 * apiFetch with the caller's Auth0 access token attached. Reads the session
 * cookie, so it must never be called from a 'use cache' scope.
 */
export async function authedFetch<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const { token } = await auth0.getAccessToken();

  try {
    return await apiFetch<T>(path, {
      ...init,
      headers: { ...init.headers, Authorization: `Bearer ${token}` },
    });
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      redirect('/auth/login' as Route);
    }
    throw error;
  }
}
