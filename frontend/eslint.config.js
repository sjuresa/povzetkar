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
      files: ['**/*.{ts,tsx}'],
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
          projectService: true,
          tsconfigRootDir: import.meta.dirname,
        },
      },
      rules: {
        ...reactHooks.configs.recommended.rules,
      },
    },
  ],
)
