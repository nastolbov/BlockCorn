import { defineConfig } from 'vite'
import { resolve } from 'path'
import { copyFileSync, existsSync, mkdirSync } from 'fs'

const src = resolve(__dirname, 'src')

export default defineConfig({
  root: src,
  build: {
    outDir: resolve(__dirname, 'dist'),
    emptyOutDir: true,
    minify: false,
    // Keep content script chunks small by not inlining dynamic imports
    rollupOptions: {
      input: {
        background: resolve(src, 'background/index.ts'),
        content:    resolve(src, 'content/index.ts'),
        popup:      resolve(src, 'popup/index.html'),
        blocked:    resolve(src, 'blocked/index.html'),
      },
      output: {
        entryFileNames: (chunk) => {
          if (chunk.name === 'background') return 'background/index.js'
          if (chunk.name === 'content')    return 'content/index.js'
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
