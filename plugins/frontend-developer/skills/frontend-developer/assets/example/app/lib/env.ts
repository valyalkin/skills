import 'server-only';

import { z } from 'zod';

const serverEnvSchema = z.object({
  API_BASE_URL: z.url(),
  API_TOKEN: z.string().min(1).optional(),
  AUTH0_AUDIENCE: z.string().min(1),
});

export const env = serverEnvSchema.parse({
  API_BASE_URL: process.env.API_BASE_URL,
  API_TOKEN: process.env.API_TOKEN,
  AUTH0_AUDIENCE: process.env.AUTH0_AUDIENCE,
});
