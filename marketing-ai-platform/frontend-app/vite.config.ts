import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 3000,
    proxy: {
      '/strategy': 'http://localhost:8080',
      '/creative': 'http://localhost:8080',
      '/analytics': 'http://localhost:8080',
      '/generate': 'http://localhost:8080',
      '/trends': 'http://localhost:8080',
    },
  },
});
