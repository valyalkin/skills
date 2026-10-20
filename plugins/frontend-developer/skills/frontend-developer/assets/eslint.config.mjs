import { defineConfig, globalIgnores } from 'eslint/config';
import nextVitals from 'eslint-config-next/core-web-vitals';
import nextTs from 'eslint-config-next/typescript';
import testingLibrary from 'eslint-plugin-testing-library';

// Next 16+ ships flat configs as direct imports. On Next 15, import FlatCompat from
// '@eslint/eslintrc' and use compat.extends('next/core-web-vitals', 'next/typescript').
const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  {
    name: 'project/type-aware',
    files: ['**/*.ts', '**/*.tsx'],
    languageOptions: {
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    rules: {
      // An unawaited apiFetch is a silent data bug.
      '@typescript-eslint/no-floating-promises': 'error',
      '@typescript-eslint/no-misused-promises': 'error',
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/consistent-type-imports': [
        'error',
        { fixStyle: 'inline-type-imports' },
      ],
    },
  },
  {
    name: 'project/imports',
    files: ['**/*.ts', '**/*.tsx'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['../../*'],
              message: 'Use the @/ alias instead of deep relative imports.',
            },
          ],
        },
      ],
    },
  },
  {
    name: 'project/tests',
    files: ['**/*.test.ts', '**/*.test.tsx'],
    extends: [testingLibrary.configs['flat/react']],
  },
  globalIgnores([
    '.next/**',
    'out/**',
    'build/**',
    'next-env.d.ts',
    'coverage/**',
    'playwright-report/**',
    'test-results/**',
  ]),
]);

export default eslintConfig;
