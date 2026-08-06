import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    strictPort: true,
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1200,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('marketplaceSeed')) return 'marketplace-seeds';
          if (id.includes('node_modules/@monaco-editor') || id.includes('node_modules/monaco-editor')) {
            return 'monaco';
          }
          if (id.includes('node_modules/firebase')) return 'firebase';
          if (id.includes('node_modules/framer-motion')) return 'motion';
          if (id.includes('skills/builtinCatalog')) return 'skills-builtin';
          if (id.includes('extensions/builtinCatalog')) return 'extensions-builtin';
        },
      },
    },
  },
});
