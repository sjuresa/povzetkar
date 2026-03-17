/// <reference types="vitest" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
  ],
  server: {
    host: true,
    allowedHosts: ['ninki.tailed12a9.ts.net', 'localhost', '127.0.0.1'],
    proxy: Object.fromEntries(
      [
        '/api',
        '/auth/login',
        '/login',
        '/logout',
        '/static',
      ].map((path) => [
        path,
        {
          target: 'http://localhost:8080',
          changeOrigin: true,
          xfwd: true,
        },
      ])
    ),
  },
  // @ts-expect-error - ssgOptions is not in the base Vite config type but handled by vite-react-ssg
  ssgOptions: {
    script: 'async',
    formatting: 'minify',
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
  },
})
