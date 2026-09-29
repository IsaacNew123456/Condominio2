import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  define: {
    global: 'window',
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://3.143.250.168:8080',
        changeOrigin: true,
        secure: false,
      },
      '/ws': {
        target: 'http://3.143.250.168:8080',
        changeOrigin: true,
        ws: true,
      },
    },
  },
});
