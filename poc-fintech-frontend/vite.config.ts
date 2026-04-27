/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * Vite + Vitest configuration for POC Fintech Frontend.
 *
 * - Proxies /api calls to the Spring Boot backend (avoids CORS in dev)
 * - Path alias: @/ → src/
 * - Vitest runs with jsdom for React component testing
 */

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': '/src',
    },
  },
  server: {
    port: 5173,
    // In BFF mode (VITE_AUTH_MODE=bff) everything auth/session-related must
    // traverse the same origin as /api (for the __Host- cookie contract to
    // hold). We proxy /bff, /oauth2, /login/oauth2, and /logout to the
    // backend on :8080 so the browser sees a single origin in dev.
    //
    // CRITICAL: changeOrigin MUST be false for the OAuth2-bearing proxies.
    // With changeOrigin:true Vite rewrites the Host header to
    // `localhost:8080`, so Spring computes `{baseUrl}` as
    // `http://localhost:8080` and emits an OAuth2 redirect_uri pointing at
    // the backend port. The browser then leaves :5173 entirely after the
    // Keycloak callback and ends up stuck at `:8080/bff/user?continue`.
    // Keeping the original Host=`localhost:5173` makes Spring build a
    // :5173-relative redirect_uri (which Keycloak's realm already lists),
    // so the OAuth2 dance stays on the SPA origin throughout.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: false,
      },
      '/bff': {
        target: 'http://localhost:8080',
        changeOrigin: false,
      },
      '/oauth2': {
        target: 'http://localhost:8080',
        changeOrigin: false,
      },
      '/login/oauth2': {
        target: 'http://localhost:8080',
        changeOrigin: false,
      },
      '/logout': {
        target: 'http://localhost:8080',
        changeOrigin: false,
      },
      '/realms': {
        target: 'http://localhost:8180',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
  // test config for Vitest should be in a separate vitest.config.ts if needed
});


