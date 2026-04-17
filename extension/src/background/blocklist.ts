// Fetches and caches the StevenBlack porn-only hosts list.
// Parsed into an array of unique base domains (no www. prefix duplication).

const STEVENBLACK_URL =
  'https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts'
const CACHE_KEY = 'blocklist_domains'
const CACHE_TS_KEY = 'blocklist_updated_at'
const TTL_MS = 24 * 60 * 60 * 1000 // 24 hours
const MAX_DOMAINS = 29_000 // declarativeNetRequestWithHostAccess allows up to 30k dynamic rules

function parseHosts(raw: string): string[] {
  const seen = new Set<string>()
  const domains: string[] = []

  for (const line of raw.split('\n')) {
    const trimmed = line.trim()
    if (!trimmed || trimmed.startsWith('#')) continue

    const parts = trimmed.split(/\s+/)
    // Format: "0.0.0.0 domain.com" or "127.0.0.1 domain.com"
    if (parts.length < 2) continue
    const ip = parts[0]
    if (ip !== '0.0.0.0' && ip !== '127.0.0.1') continue

    let domain = parts[1].toLowerCase()
    // Strip www. prefix so one rule covers both www + bare domain via ||domain
    if (domain.startsWith('www.')) domain = domain.slice(4)
    if (!domain || domain === 'localhost') continue

    if (!seen.has(domain)) {
      seen.add(domain)
      domains.push(domain)
      if (domains.length >= MAX_DOMAINS) break
    }
  }

  return domains
}

export async function getCachedDomains(): Promise<string[]> {
  const result = await chrome.storage.local.get([CACHE_KEY, CACHE_TS_KEY])
  const domains: string[] = result[CACHE_KEY] ?? []
  const updatedAt: number = result[CACHE_TS_KEY] ?? 0

  if (domains.length > 0 && Date.now() - updatedAt < TTL_MS) {
    return domains
  }
  return []
}

export async function fetchAndCacheBlocklist(): Promise<string[]> {
  try {
    const resp = await fetch(STEVENBLACK_URL)
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
    const text = await resp.text()
    const domains = parseHosts(text)

    await chrome.storage.local.set({
      [CACHE_KEY]: domains,
      [CACHE_TS_KEY]: Date.now(),
    })

    console.log(`[BlockCorn] Cached ${domains.length} domains`)
    return domains
  } catch (err) {
    console.error('[BlockCorn] Failed to fetch blocklist:', err)
    // Return whatever we have cached, even if stale
    const result = await chrome.storage.local.get(CACHE_KEY)
    return result[CACHE_KEY] ?? []
  }
}

export async function getBlocklist(): Promise<string[]> {
  const cached = await getCachedDomains()
  if (cached.length > 0) return cached
  return fetchAndCacheBlocklist()
}
