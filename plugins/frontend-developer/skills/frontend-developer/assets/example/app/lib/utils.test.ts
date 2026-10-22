import { describe, expect, it } from 'vitest';
import { formatCurrency, generatePagination } from '@/app/lib/utils';

describe('generatePagination', () => {
  it('lists every page when there are seven or fewer', () => {
    expect(generatePagination(1, 5)).toEqual([1, 2, 3, 4, 5]);
  });

  it('collapses the middle when the current page is in the first three', () => {
    expect(generatePagination(2, 10)).toEqual([1, 2, 3, '...', 9, 10]);
  });

  it('collapses both sides when the current page is in the middle', () => {
    expect(generatePagination(6, 10)).toEqual([1, '...', 5, 6, 7, '...', 10]);
  });
});

it('formats cents as USD', () => {
  expect(formatCurrency(2500)).toBe('$25.00');
});
