/**
 * Custom ESLint rule: forbid `useReducer` outside src/features/alterego/state/.
 * Spec FR-2418 + STATE.md: session state has one designated home; new
 * reducers MUST land in that folder or this rule fails the build.
 *
 * Extracted from eslint.config.js so the rule object can be loaded by
 * RuleTester in a Vitest unit test (T050).
 */
export const noReducerOutsideState = {
  meta: {
    type: 'problem',
    docs: { description: 'Forbid useReducer outside features/alterego/state/' },
    schema: [],
    messages: {
      forbidden:
        'useReducer outside src/features/alterego/state/ is forbidden by FR-2418. ' +
        'Use the existing AlterEgoSessionState reducer + a new action, OR plain useState ' +
        'for component-local UI state. See frontend/STATE.md.',
    },
  },
  create(context) {
    const filename = context.filename || context.getFilename()
    const inStatePackage = filename.includes('/features/alterego/state/')
    const isTestOrFixture = /(\.test\.|__fixtures__|__mocks__)/.test(filename)
    if (inStatePackage || isTestOrFixture) return {}
    return {
      ImportSpecifier(node) {
        if (
          node.imported &&
          node.imported.name === 'useReducer' &&
          node.parent.source &&
          node.parent.source.value === 'react'
        ) {
          context.report({ node, messageId: 'forbidden' })
        }
      },
      CallExpression(node) {
        if (node.callee.type === 'Identifier' && node.callee.name === 'useReducer') {
          context.report({ node, messageId: 'forbidden' })
        }
        if (
          node.callee.type === 'MemberExpression' &&
          node.callee.property &&
          node.callee.property.name === 'useReducer'
        ) {
          context.report({ node, messageId: 'forbidden' })
        }
      },
    }
  },
}
