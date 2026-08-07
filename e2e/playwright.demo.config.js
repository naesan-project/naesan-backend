import {defineConfig, devices} from '@playwright/test';

process.env.NAESAN_E2E_PROOF_TIMEOUT_MS ??= '120000';
process.env.NAESAN_E2E_READY_TIMEOUT_MS ??= '240000';

const BASE_URL = process.env.NAESAN_DEMO_BASE_URL ??
  'https://naesan-frontend.onrender.com';

export default defineConfig({
  testDir: './tests',
  outputDir: './demo-artifacts',
  fullyParallel: false,
  workers: 1,
  timeout: 420_000,
  expect: {
    timeout: 30_000,
  },
  reporter: [['list']],
  use: {
    baseURL: BASE_URL,
    screenshot: 'only-on-failure',
    trace: 'off',
    video: {
      mode: 'on',
      size: {
        width: 1440,
        height: 900,
      },
    },
    launchOptions: {
      slowMo: 500,
    },
  },
  projects: [
    {
      name: 'demo-chromium',
      use: {
        ...devices['Desktop Chrome'],
        viewport: {
          width: 1440,
          height: 900,
        },
      },
    },
  ],
});
