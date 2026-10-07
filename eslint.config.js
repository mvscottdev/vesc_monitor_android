// https://docs.expo.dev/guides/using-eslint/
const { defineConfig } = require('eslint/config');
const expoConfig = require('eslint-config-expo/flat');
const prettierConfig = require('eslint-config-prettier');

module.exports = defineConfig([
  expoConfig,
  prettierConfig,
  {
    ignores: ['node_modules/*', 'android/*', 'ios/*', 'core/*', 'dist/*', '.expo/*', 'docs/*'],
  },
  {
    // Playwright is an optional tool for the design kit, installed on demand.
    files: ['scripts/design.mjs'],
    rules: { 'import/no-unresolved': 'off' },
  },
]);
