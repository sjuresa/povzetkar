import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'

export default tseslint.config(
  [
    {
      ignores: ['dist', 'src/api/generated', 'eslint_error.txt'],
    },
    {
      files: ['src/**/*.{ts,tsx}'],
      extends: [
        js.configs.recommended,
        ...tseslint.configs.recommendedTypeChecked,
        reactRefresh.configs.vite,
      ],
      plugins: {
        'react-hooks': reactHooks,
      },
      languageOptions: {
        ecmaVersion: 2023,
        globals: globals.browser,
        parserOptions: {
          project: ['./tsconfig.app.json', './tsconfig.node.json'],
          tsconfigRootDir: import.meta.dirname,
        },
      },
      rules: {
        ...reactHooks.configs.recommended.rules,
      },
    },
    {
      files: ['e2e/**/*.ts'],
      extends: [
        js.configs.recommended,
        ...tseslint.configs.recommended,
      ],
      languageOptions: {
        ecmaVersion: 2023,
        globals: globals.node,
      },
    },
  ],
)
