import {defineConfig, devices} from '@playwright/test';

const BACKEND_URL = 'http://localhost:8080';
const FRONTEND_URL = 'http://localhost:5173';

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  timeout: 120_000,
  expect: {
    timeout: 10_000,
  },
  reporter: [['list']],
  use: {
    baseURL: FRONTEND_URL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
      },
    },
  ],
  webServer: [
    {
      command: [
        '../gradlew -p .. bootTestRun',
        `--args="--server.port=8080`,
        `--naesan.security.frontend-origin=${FRONTEND_URL}`,
        '--naesan.proof.worker.initial-delay=0s"',
      ].join(' '),
      url: `${BACKEND_URL}/actuator/health`,
      timeout: 120_000,
      reuseExistingServer: false,
    },
    {
      command: 'node support/frontend-server.js',
      url: FRONTEND_URL,
      timeout: 10_000,
      reuseExistingServer: false,
    },
  ],
});
