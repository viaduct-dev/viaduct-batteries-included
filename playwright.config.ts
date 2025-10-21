import fs from 'node:fs';
import path from 'node:path';
import { defineConfig, devices } from '@playwright/test';

const isCI = !!process.env.CI;
const shouldStartWebServer = isCI;
const localChromeExecutable = isCI
  ? undefined
  : '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const localHomeDir = isCI ? undefined : path.join(process.cwd(), '.playwright-home');

if (localHomeDir) {
  process.env.HOME = localHomeDir;
  if (!fs.existsSync(localHomeDir)) {
    fs.mkdirSync(localHomeDir, { recursive: true });
  }
}

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: isCI,
  retries: isCI ? 2 : 0,
  workers: 1,
  reporter: shouldStartWebServer ? [['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: 'http://127.0.0.1:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },

  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        launchOptions: {
          args: [
            '--disable-web-security',
            '--disable-features=IsolateOrigins,site-per-process',
          ],
          ...(localChromeExecutable
            ? { executablePath: localChromeExecutable }
            : {}),
        },
      },
    },
  ],

  webServer: shouldStartWebServer
    ? {
        command: 'npm run dev -- --host 127.0.0.1',
        url: 'http://127.0.0.1:5173',
        reuseExistingServer: true,
        timeout: 120000,
      }
    : undefined,
});
