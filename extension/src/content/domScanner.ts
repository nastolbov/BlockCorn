import { ALL_TRIGGER_WORDS } from './triggerWords'

export interface ScanResult {
  triggered: boolean
  matchCount: number
  matches: string[]
}

/**
 * Extracts visible text from the DOM and counts trigger-word matches.
 * Returns the result; does NOT redirect — caller decides what to do.
 */
export function scanDom(threshold: number): ScanResult {
  const text = extractText().toLowerCase()
  const matches: string[] = []

  for (const word of ALL_TRIGGER_WORDS) {
    const lc = word.toLowerCase()
    // Word-boundary aware match (handles Cyrillic with a simple prefix check)
    const regex = buildWordRegex(lc)
    if (regex.test(text)) {
      matches.push(word)
    }
  }

  return {
    triggered: matches.length >= threshold,
    matchCount: matches.length,
    matches,
  }
}

/** Collect visible text from body, skipping scripts/styles/meta. */
function extractText(): string {
  const skip = new Set(['SCRIPT', 'STYLE', 'NOSCRIPT', 'META', 'LINK', 'HEAD'])
  const walker = document.createTreeWalker(
    document.body,
    NodeFilter.SHOW_TEXT,
    {
      acceptNode(node) {
        const parent = node.parentElement
        if (!parent) return NodeFilter.FILTER_REJECT
        if (skip.has(parent.tagName)) return NodeFilter.FILTER_REJECT
        // Skip hidden elements
        const style = getComputedStyle(parent)
        if (style.display === 'none' || style.visibility === 'hidden') {
          return NodeFilter.FILTER_REJECT
        }
        return NodeFilter.FILTER_ACCEPT
      },
    }
  )

  const chunks: string[] = []
  let node: Node | null
  while ((node = walker.nextNode())) {
    const t = node.textContent?.trim()
    if (t && t.length > 1) chunks.push(t)
  }
  return chunks.join(' ')
}

function buildWordRegex(word: string): RegExp {
  // Use \b for ASCII; for Cyrillic, match as substring (Cyrillic has no \b support in JS)
  const isCyrillic = /[\u0400-\u04FF]/.test(word)
  const escaped = word.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return isCyrillic
    ? new RegExp(escaped, 'i')
    : new RegExp(`\\b${escaped}`, 'i')
}
