'use client';

import clsx from 'clsx';
import type { Route } from 'next';
import Link from 'next/link';
import { usePathname, useSearchParams } from 'next/navigation';
import { generatePagination } from '@/app/lib/utils';

export default function Pagination({ totalPages }: { totalPages: number }) {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const currentPage = Number(searchParams.get('page')) || 1;

  const createPageURL = (page: number | string) => {
    const params = new URLSearchParams(searchParams);
    params.set('page', String(page));
    // typedRoutes cannot know a constructed query string.
    return `${pathname}?${params.toString()}` as Route;
  };

  return (
    <nav aria-label="Pagination" className="inline-flex -space-x-px">
      {generatePagination(currentPage, totalPages).map((page, index) => {
        const isActive = currentPage === page;
        const isEllipsis = page === '...';
        const className = clsx(
          'flex h-10 w-10 items-center justify-center border text-sm',
          {
            'z-10 border-blue-600 bg-blue-600 text-white': isActive,
            'text-gray-300': isEllipsis,
            'hover:bg-gray-100': !isActive && !isEllipsis,
          },
        );

        return isActive || isEllipsis ? (
          <div
            key={`${page}-${index}`}
            className={className}
            aria-current={isActive ? 'page' : undefined}
          >
            {page}
          </div>
        ) : (
          <Link
            key={`${page}-${index}`}
            href={createPageURL(page)}
            className={className}
            aria-label={`Go to page ${page}`}
          >
            {page}
          </Link>
        );
      })}
    </nav>
  );
}
