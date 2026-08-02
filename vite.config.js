import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { execSync } from 'child_process'

const gitCommit = (() => {
  try { return execSync('git rev-parse --short HEAD').toString().trim(); } catch { return 'unknown'; }
})();
const buildTime = new Date().toISOString();

export default defineConfig({
  define: {
    __BUILD_TIME__: JSON.stringify(buildTime),
    __GIT_COMMIT__: JSON.stringify(gitCommit),
  },
  plugins: [react()],
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'react-vendor': ['react', 'react-dom', 'react-dom/client'],
          'supabase': ['@supabase/supabase-js'],
        }
      }
    },
    chunkSizeWarningLimit: 1000,
  }
})
