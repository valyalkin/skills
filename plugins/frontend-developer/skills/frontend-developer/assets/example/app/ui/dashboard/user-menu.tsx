import { auth0 } from '@/app/lib/auth0';

export default async function UserMenu() {
  const session = await auth0.getSession();

  if (!session) {
    return (
      <a href="/auth/login" className="text-sm font-medium text-blue-600">
        Log in
      </a>
    );
  }

  return (
    <div className="flex items-center gap-3 text-sm">
      <span className="font-medium">{session.user.name}</span>
      <a href="/auth/logout" className="text-gray-500 hover:text-gray-900">
        Log out
      </a>
    </div>
  );
}
