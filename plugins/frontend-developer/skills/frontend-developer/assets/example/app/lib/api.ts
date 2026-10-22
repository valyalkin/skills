import 'server-only';

import { env } from '@/app/lib/env';

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly body: unknown,
  ) {
    super(`API request failed with ${status}`);
    this.name = 'ApiError';
  }
}

export async function apiFetch<T>(
  path: string,
  { headers, ...init }: RequestInit = {},
): Promise<T> {
  const response = await fetch(`${env.API_BASE_URL}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(env.API_TOKEN ? { Authorization: `Bearer ${env.API_TOKEN}` } : {}),
      ...headers,
    },
  });

  if (!response.ok) {
    throw new ApiError(
      response.status,
      await response.json().catch(() => null),
    );
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}
