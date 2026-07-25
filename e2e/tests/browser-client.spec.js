import {expect, test} from '@playwright/test';

test('별도 frontend origin에서 CSRF cookie를 발급받는다', async ({page}) => {
  await page.goto('/');

  const csrfToken = await page.evaluate(() => window.naesan.csrfToken());

  expect(csrfToken).not.toBe('');
  await expect(page.locator('#status')).toHaveText('ready');
});
