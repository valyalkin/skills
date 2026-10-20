import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';
import { auth0 } from '@/app/lib/auth0';

const PROTECTED_PREFIXES = ['/dashboard'];

export async function proxy(request: NextRequest) {
  const authResponse = await auth0.middleware(request);
  const { pathname, search } = request.nextUrl;

  if (pathname.startsWith('/auth/')) {
    return authResponse;
  }

  if (!PROTECTED_PREFIXES.some((prefix) => pathname.startsWith(prefix))) {
    return authResponse;
  }

  const session = await auth0.getSession(request);

  if (!session) {
    if (request.headers.has('next-action')) {
      return authResponse;
    }

    const loginUrl = new URL('/auth/login', request.nextUrl.origin);
    loginUrl.searchParams.set('returnTo', `${pathname}${search}`);
    return NextResponse.redirect(loginUrl);
  }

  return authResponse;
}

export const config = {
  matcher: [
    '/((?!_next/static|_next/image|favicon.ico|sitemap.xml|robots.txt).*)',
  ],
};
