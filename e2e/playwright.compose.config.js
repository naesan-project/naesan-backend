import {defineConfig, devices} from '@playwright/test';

const BASE_URL = process.env.NAESAN_E2E_BASE_URL ?? 'http://localhost:18081';

export default defineConfig({
  testDir: './tests',
  testMatch: 'core-local.spec.js',
  fullyParallel: false,
  workers: 1,
  timeout: 120_000,
  expect: {
    timeout: 10_000,
  },
  reporter: [['list']],
  use: {
    baseURL: BASE_URL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'compose-chromium',
      use: {
        ...devices['Desktop Chrome'],
      },
    },
  ],
});
