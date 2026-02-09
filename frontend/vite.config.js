import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
    plugins: [react()],
    server: {
        port: 5173,
        strictPort: false,
        host: true,
        allowedHosts: ['.trycloudflare.com'],
    },
    build: {
        outDir: 'dist',
        sourcemap: false,
    },
    test: {
        globals: true,
        environment: 'happy-dom',
        setupFiles: './src/setupTests.js',
    }
})
