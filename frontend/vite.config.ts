import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const extraHosts = (env.VITE_ALLOWED_HOSTS || '').split(',').map(h => h.trim()).filter(Boolean);

  return {
    plugins: [
      react(),
      tailwindcss(),
    ],
    server: {
      host: true,
      allowedHosts: [...extraHosts, 'localhost', '127.0.0.1'],
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
  }
})
