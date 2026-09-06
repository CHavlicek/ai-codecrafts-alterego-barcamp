import { defineConfig, devices } from '@playwright/test'

/**
 * Playwright config for the AI Alter Ego E2E + a11y suite (T044–T047).
 *
 * Auto-starts the Vite dev server on :5173. Backend is NOT required —
 * every spec intercepts `POST /api/v1/alter-egos` with
 * {@code page.route(...)} and fulfills it locally, so the test suite
 * is fast and self-contained (backend has its own JUnit coverage).
 *
 * Run with:   npm run test:e2e          (all specs)
 *             npm run test:a11y         (axe-scan + keyboard-walkthrough only)
 */
export default defineConfig({
  testDir: './tests/e2e',
  timeout: 30_000,
  expect: { timeout: 10_000 },
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],

  use: {
    baseURL: 'http://127.0.0.1:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },

  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        launchOptions: {
          // 004 camera-only capture: enable Chromium's fake MediaDevices so
          // camera-capture.spec.ts (and any helper driving the capture UI)
          // gets a deterministic green/red test-pattern stream without a
          // real webcam. Harmless to every other spec — these flags only
          // take effect when getUserMedia is actually called.
          args: ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream'],
        },
      },
    },
  ],

  webServer: {
    command: 'npm run dev -- --host 127.0.0.1',
    url: 'http://127.0.0.1:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
    stdout: 'ignore',
    stderr: 'pipe',
  },
})
