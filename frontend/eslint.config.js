import js from '@eslint/js'
import globals from 'globals'
import tseslint from 'typescript-eslint'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import jsxA11y from 'eslint-plugin-jsx-a11y'
import { defineConfig, globalIgnores } from 'eslint/config'
import { noReducerOutsideState } from './eslint-rules/no-reducer-outside-state.js'

// Custom rule: forbid `useReducer` outside features/alterego/state/.
// Spec FR-2418 + STATE.md: session state has one designated home; new
// reducers MUST land in that folder or the rule fails the build.
// Rule body is extracted to ./eslint-rules/no-reducer-outside-state.js so
// it can be loaded by RuleTester in a Vitest unit test.
const noReducerOutsideStatePlugin = {
  rules: {
    'no-reducer-outside-state': noReducerOutsideState,
  },
}

// Constitutional Principle I: ESLint flat config + jsx-a11y per FR-020..FR-023.
export default defineConfig([
  globalIgnores([
    'dist',
    'coverage',
    'playwright-report',
    'test-results',
    'node_modules',
    'src/features/alterego/types.generated.ts',
  ]),
  {
    files: ['**/*.{js,jsx,ts,tsx}'],
    extends: [
      js.configs.recommended,
      ...tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
      jsxA11y.flatConfigs.recommended,
    ],
    languageOptions: {
      ecmaVersion: 2022,
      globals: globals.browser,
      parserOptions: {
        ecmaVersion: 'latest',
        ecmaFeatures: { jsx: true },
        sourceType: 'module',
      },
    },
    plugins: {
      'aiavatar-state': noReducerOutsideStatePlugin,
    },
    rules: {
      'no-unused-vars': 'off',
      '@typescript-eslint/no-unused-vars': [
        'error',
        { varsIgnorePattern: '^[A-Z_]', argsIgnorePattern: '^_' },
      ],
      'aiavatar-state/no-reducer-outside-state': 'error',
    },
  },
])
