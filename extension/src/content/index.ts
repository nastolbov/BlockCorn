/**
 * BlockCorn content script — Этап 4.
 *
 * 1. DOM text scan: count trigger words → redirect if above threshold.
 * 2. Image classification: nsfwjs hides explicit images (optional, heavy).
 *
 * The script is injected at document_idle so the DOM is ready.
 * It re-runs on SPA navigations via a popstate/pushstate listener.
 */

import { scanDom } from './domScanner'

const BLOCK_PAGE = chrome.runtime.getURL('blocked/index.html')

async function getSettings(): Promise<{
  enabled: boolean
  triggerThreshold: number
  imageClassification: boolean
}> {
  return new Promise((resolve) => {
    chrome.runtime.sendMessage({ type: 'GET_SETTINGS' }, (resp) => {
      resolve(
        resp ?? { enabled: true, triggerThreshold: 5, imageClassification: false }
      )
    })
  })
}

function redirectToBlockPage(reason: string): void {
  const url = `${BLOCK_PAGE}?domain=${encodeURIComponent(location.hostname)}&reason=${encodeURIComponent(reason)}`
  window.location.replace(url)
}

async function runChecks(): Promise<void> {
  const settings = await getSettings()
  if (!settings.enabled) return

  // ── 1. DOM text scan ─────────────────────────────────────────────────────
  const result = scanDom(settings.triggerThreshold)
  if (result.triggered) {
    redirectToBlockPage(`dom:${result.matchCount} trigger words`)
    return
  }

  // ── 2. Image classification (loaded lazily — ~3MB TF.js bundle) ──────────
  if (settings.imageClassification) {
    const { classifyImages } = await import('./imageClassifier')
    classifyImages()
  }
}

// Run on initial page load
runChecks()

// Re-run on SPA client-side navigations
let lastHref = location.href
const navObserver = new MutationObserver(() => {
  if (location.href !== lastHref) {
    lastHref = location.href
    // Brief delay to let the SPA render new content
    setTimeout(runChecks, 800)
  }
})
navObserver.observe(document.documentElement, {
  subtree: true,
  childList: true,
})
