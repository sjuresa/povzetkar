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
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            configure: (proxy: any) => {
              // Quarkus OIDC returns 302 → Google OAuth for unauthenticated XHR requests.
              // The browser follows this cross-origin redirect causing a CORS failure before
              // our 401 interceptor can fire. Rewrite OIDC redirects to 401 at the proxy level
              // so client.ts handles them cleanly.
              // eslint-disable-next-line @typescript-eslint/no-explicit-any
              proxy.on('proxyRes', (proxyRes: any, req: any) => {
                const location = proxyRes.headers['location'] ?? '';
                const isXhr = (req.headers['x-requested-with'] ?? '') === 'XMLHttpRequest';
                const isOidcRedirect = typeof location === 'string' && location.includes('accounts.google.com');
                if (proxyRes.statusCode === 302 && isXhr && isOidcRedirect) {
                  proxyRes.statusCode = 401;
                  delete proxyRes.headers['location'];
                }
              });
            },
          },
        ])
      ),
    },
    test: {
      globals: true,
      environment: 'jsdom',
      setupFiles: './src/test/setup.ts',
      include: ['src/**/*.{test,spec}.{ts,tsx}'],
    },
  }
})
