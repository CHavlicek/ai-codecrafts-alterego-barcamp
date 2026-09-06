/**
 * T050 — `aiavatar-state/no-reducer-outside-state` rule contract:
 *   - `useReducer` outside `src/features/alterego/state/` → error
 *   - `useReducer` inside that folder → accepted
 *   - test/fixture files → accepted regardless
 */
import { RuleTester } from 'eslint'
import { describe, it } from 'vitest'
import { noReducerOutsideState } from './no-reducer-outside-state.js'

const tester = new RuleTester({
  languageOptions: {
    ecmaVersion: 'latest',
    sourceType: 'module',
    parserOptions: { ecmaFeatures: { jsx: true } },
  },
})

describe('no-reducer-outside-state', () => {
  it('runs ESLint RuleTester suite', () => {
    tester.run('no-reducer-outside-state', noReducerOutsideState, {
      valid: [
        {
          name: 'useReducer is allowed inside features/alterego/state/',
          filename: '/repo/frontend/src/features/alterego/state/reducer.ts',
          code: "import { useReducer } from 'react'; const x = useReducer(() => null, null);",
        },
        {
          name: 'useReducer in a .test.ts file is allowed',
          filename: '/repo/frontend/src/lib/foo.test.ts',
          code: "import { useReducer } from 'react'; const x = useReducer(() => null, null);",
        },
        {
          name: 'no useReducer in a regular component file is allowed',
          filename: '/repo/frontend/src/components/Foo.tsx',
          code: "import { useState } from 'react'; export const Foo = () => useState(0);",
        },
      ],
      invalid: [
        {
          name: 'useReducer imported in a component file fails',
          filename: '/repo/frontend/src/components/Foo.tsx',
          code: "import { useReducer } from 'react'; export const Foo = () => useReducer(() => null, null);",
          errors: [{ messageId: 'forbidden' }, { messageId: 'forbidden' }],
        },
        {
          name: 'React.useReducer member-call in a hook file fails',
          filename: '/repo/frontend/src/features/alterego/hooks/useFoo.ts',
          code: "import React from 'react'; export const useFoo = () => React.useReducer(() => null, null);",
          errors: [{ messageId: 'forbidden' }],
        },
      ],
    })
  })
})
