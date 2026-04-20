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
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
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


