import { fetchAndCacheBlocklist, getBlocklist } from './blocklist'
import { applyBlocklistRules, clearAllRules, isEnabled, setEnabled } from './blocker'

const ALARM_NAME = 'blocklist-update'

async function initBlocker() {
  const enabled = await isEnabled()
  if (!enabled) return

  const domains = await getBlocklist()
  if (domains.length > 0) {
    await applyBlocklistRules(domains)
  } else {
    // First install — fetch immediately
    const fresh = await fetchAndCacheBlocklist()
    await applyBlocklistRules(fresh)
  }
}

async function refreshBlocklist() {
  const enabled = await isEnabled()
  const domains = await fetchAndCacheBlocklist()
  if (enabled && domains.length > 0) {
    await applyBlocklistRules(domains)
  }
}

// ── Lifecycle ────────────────────────────────────────────────────────────────

chrome.runtime.onInstalled.addListener(async ({ reason }) => {
  if (reason === 'install' || reason === 'update') {
    await initBlocker()
  }

  // Schedule daily refresh
  chrome.alarms.create(ALARM_NAME, {
    periodInMinutes: 24 * 60,
    delayInMinutes: 24 * 60,
  })
})

// Restore rules after browser restart (service worker restarts lose dynamic rules)
chrome.runtime.onStartup.addListener(async () => {
  await initBlocker()
})

chrome.alarms.onAlarm.addListener(async (alarm) => {
  if (alarm.name === ALARM_NAME) {
    await refreshBlocklist()
  }
})

// ── Message API (from popup) ─────────────────────────────────────────────────

chrome.runtime.onMessage.addListener((msg, _sender, respond) => {
  if (msg.type === 'GET_STATUS') {
    isEnabled().then((enabled) => respond({ enabled }))
    return true
  }

  if (msg.type === 'SET_ENABLED') {
    const { enabled } = msg
    ;(async () => {
      await setEnabled(enabled)
      if (enabled) {
        const domains = await getBlocklist()
        await applyBlocklistRules(domains)
      } else {
        await clearAllRules()
      }
      respond({ ok: true })
    })()
    return true
  }

  if (msg.type === 'FORCE_REFRESH') {
    refreshBlocklist().then(() => respond({ ok: true }))
    return true
  }

  if (msg.type === 'GET_SETTINGS') {
    ;(async () => {
      const result = await chrome.storage.local.get([
        'blocker_enabled',
        'trigger_threshold',
        'image_classification',
      ])
      respond({
        enabled:             result['blocker_enabled'] !== false,
        triggerThreshold:    result['trigger_threshold'] ?? 5,
        imageClassification: result['image_classification'] === true,
      })
    })()
    return true
  }

  if (msg.type === 'SET_SETTINGS') {
    const { triggerThreshold, imageClassification } = msg
    chrome.storage.local
      .set({ trigger_threshold: triggerThreshold, image_classification: imageClassification })
      .then(() => respond({ ok: true }))
    return true
  }

  if (msg.type === 'REQUEST_UNLOCK') {
    // Log the request; a future build can email the accountability contact
    console.log('[BlockCorn] Unlock requested for domain:', msg.domain)
    respond({ ok: true })
    return true
  }
})
