import 'server-only';
import { Auth0Client } from '@auth0/nextjs-auth0/server';
import { env } from '@/app/lib/env';

export const auth0 = new Auth0Client({
  authorizationParameters: {
    scope: 'openid profile email offline_access',
    audience: env.AUTH0_AUDIENCE,
  },
});
