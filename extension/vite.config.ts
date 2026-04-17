import { defineConfig } from 'vite'
import { resolve } from 'path'
import { copyFileSync, existsSync, mkdirSync, renameSync, rmSync } from 'fs'

const src = resolve(__dirname, 'src')

export default defineConfig({
  // Set root to src/ so HTML paths output as blocked/ and popup/ (not src/blocked/)
  root: src,
  build: {
    outDir: resolve(__dirname, 'dist'),
    emptyOutDir: true,
    minify: false,
    rollupOptions: {
      input: {
        background: resolve(src, 'background/index.ts'),
        popup: resolve(src, 'popup/index.html'),
        blocked: resolve(src, 'blocked/index.html'),
      },
      output: {
        entryFileNames: (chunk) => {
          if (chunk.name === 'background') return 'background/index.js'
          return 'assets/[name]-[hash].js'
        },
        chunkFileNames: 'chunks/[name]-[hash].js',
        assetFileNames: 'assets/[name].[ext]',
      },
    },
  },
  plugins: [
    {
      name: 'copy-manifest',
      closeBundle() {
        const dist = resolve(__dirname, 'dist')
        if (!existsSync(dist)) mkdirSync(dist, { recursive: true })
        copyFileSync(
          resolve(__dirname, 'public/manifest.json'),
          resolve(dist, 'manifest.json')
        )
      },
    },
  ],
})
