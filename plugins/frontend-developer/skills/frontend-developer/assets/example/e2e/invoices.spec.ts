import { expect, test } from '@playwright/test';

test('searching narrows the invoice list', async ({ page }) => {
  await page.goto('/dashboard/invoices');
  await page.getByRole('textbox', { name: /search/i }).fill('acme');
  await expect(page).toHaveURL(/query=acme/);
});
