import fs from 'node:fs';
import path from 'node:path';
import { test as base, expect } from '@playwright/test';

const isCI = !!process.env.CI;
const chromeExecutable = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const userDataDir = path.join(process.cwd(), '.playwright-user-data');

function ensureDirExists(dir: string) {
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }
}

export const test = isCI
  ? base
  : base.extend({
      context: async ({ playwright, contextOptions }, use) => {
        ensureDirExists(userDataDir);
        ensureDirExists(path.join(userDataDir, 'xdg-config'));
        ensureDirExists(path.join(userDataDir, 'xdg-cache'));
        ensureDirExists(path.join(userDataDir, 'xdg-data'));
        ensureDirExists(path.join(userDataDir, 'crashpad'));

        const args = [
          '--disable-web-security',
          '--disable-features=IsolateOrigins,site-per-process',
          '--disable-crash-reporter',
          '--disable-breakpad',
          '--disable-crashpad',
          ...((contextOptions?.args as string[] | undefined) ?? []),
        ];

        const context = await playwright.chromium.launchPersistentContext(userDataDir, {
          ...contextOptions,
          args,
          executablePath: fs.existsSync(chromeExecutable)
            ? chromeExecutable
            : contextOptions?.executablePath,
          env: {
            ...process.env,
            HOME: userDataDir,
            XDG_CONFIG_HOME: path.join(userDataDir, 'xdg-config'),
            XDG_CACHE_HOME: path.join(userDataDir, 'xdg-cache'),
            XDG_DATA_HOME: path.join(userDataDir, 'xdg-data'),
            BREAKPAD_DUMP_LOCATION: path.join(userDataDir, 'crashpad'),
          },
        });

        try {
          await use(context);
        } finally {
          await context.close();
        }
      },
      page: async ({ context }, use) => {
        const existingPage = context.pages()[0] ?? (await context.newPage());
        await use(existingPage);
      },
    });

export { expect };
