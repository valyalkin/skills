import { Suspense } from 'react';
import UserMenu from '@/app/ui/dashboard/user-menu';
import { UserMenuSkeleton } from '@/app/ui/skeletons';

export default function Layout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex h-screen flex-col md:flex-row md:overflow-hidden">
      <div className="w-full flex-none md:w-64">
        <nav className="flex items-center justify-between p-4">
          <span className="font-semibold">Acme</span>
          {/* Session read happens here, inside Suspense - never in the shell. */}
          <Suspense fallback={<UserMenuSkeleton />}>
            <UserMenu />
          </Suspense>
        </nav>
      </div>
      <div className="grow p-6 md:overflow-y-auto md:p-12">{children}</div>
    </div>
  );
}
