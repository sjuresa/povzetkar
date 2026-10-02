import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { strictCsp } from './csp-policy'

// https://vite.dev/config/
export default defineConfig(({ mode, isPreview }) => {
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
            // No 302 → 401 rewrite: the backend answers an unauthenticated XHR with 401 itself (POV-221)
          },
        ])
      ),
    },
    // The built SPA under the production CSP, for the Playwright project "csp" (POV-214).
    preview: {
      port: 4173,
      strictPort: true,
      headers: isPreview ? { 'Content-Security-Policy': strictCsp() } : {},
    },
    test: {
      globals: true,
      environment: 'jsdom',
      setupFiles: './src/test/setup.ts',
      include: ['src/**/*.{test,spec}.{ts,tsx}'],
    },
  }
})
