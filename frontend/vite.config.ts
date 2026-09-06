import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// Constitutional Principle I: React 18+ + Vite (current major). Strict TS.
// Dev proxy lets the browser hit /api same-origin in dev (research.md S3).
// `defineConfig` is imported from `vitest/config` so the `test` block
// type-checks alongside the regular Vite options.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: true,
    // Vitest picks up `*.test.*` files under src/ by default; exclude Playwright
    // specs which import from @playwright/test and can't run under Vitest.
    exclude: ['node_modules/**', 'dist/**', 'tests/e2e/**'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      thresholds: {
        // Principle III: ≥ 90% line coverage on every module.
        lines: 90,
        statements: 90,
        functions: 90,
        branches: 90,
      },
      exclude: [
        'node_modules/**',
        'dist/**',
        'tests/e2e/**',
        // Playwright artefacts — bundled JS from the HTML report's trace
        // viewer leaks into v8 coverage's default include set and reports
        // 0% across the board, tanking the global totals.
        'playwright-report/**',
        'test-results/**',
        '**/*.config.{js,ts}',
        'src/test/**',
        'src/vite-env.d.ts',
        // React bootstrap — not meaningfully unit-testable (only exercises
        // createRoot().render()). Covered end-to-end by Playwright.
        'src/main.tsx',
        // Pure-type files — no runtime lines.
        '**/types.ts',
      ],
    },
  },
})
