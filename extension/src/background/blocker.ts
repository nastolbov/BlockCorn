// Manages declarativeNetRequest dynamic rules for domain blocking.
// One rule per unique base domain (||domain matches all subdomains).

const RULE_ID_OFFSET = 1000 // reserve 1–999 for other rule types

// URL keyword patterns blocked regardless of domain blocklist
const KEYWORD_REGEX_RULES: chrome.declarativeNetRequest.Rule[] = [
  {
    id: 1,
    priority: 2,
    action: { type: 'block' as chrome.declarativeNetRequest.RuleActionType },
    condition: {
      regexFilter: '(?i)(porn|xxx|hentai|nsfw|adult.?content|sex.?video)',
      resourceTypes: ['main_frame' as chrome.declarativeNetRequest.ResourceType],
    },
  },
  {
    id: 2,
    priority: 2,
    action: { type: 'block' as chrome.declarativeNetRequest.RuleActionType },
    condition: {
      // Russian keywords in URL paths
      regexFilter: '(?i)(порно|секс.видео|эротика)',
      resourceTypes: ['main_frame' as chrome.declarativeNetRequest.ResourceType],
    },
  },
]

function buildDomainRule(
  domain: string,
  id: number,
  blockPageUrl: string
): chrome.declarativeNetRequest.Rule {
  return {
    id,
    priority: 1,
    action: {
      type: 'redirect' as chrome.declarativeNetRequest.RuleActionType,
      redirect: {
        url: `${blockPageUrl}?domain=${encodeURIComponent(domain)}`,
      },
    },
    condition: {
      urlFilter: `||${domain}`,
      resourceTypes: ['main_frame' as chrome.declarativeNetRequest.ResourceType],
    },
  }
}

export async function applyBlocklistRules(domains: string[]): Promise<void> {
  const blockPageUrl = chrome.runtime.getURL('blocked/index.html')

  // Remove all existing dynamic rules first
  const existing = await chrome.declarativeNetRequest.getDynamicRules()
  const removeIds = existing.map((r) => r.id)

  const domainRules = domains.map((domain, i) =>
    buildDomainRule(domain, RULE_ID_OFFSET + i, blockPageUrl)
  )

  await chrome.declarativeNetRequest.updateDynamicRules({
    removeRuleIds: removeIds,
    addRules: [...KEYWORD_REGEX_RULES, ...domainRules],
  })

  console.log(
    `[BlockCorn] Applied ${domainRules.length} domain rules + ${KEYWORD_REGEX_RULES.length} regex rules`
  )
}

export async function clearAllRules(): Promise<void> {
  const existing = await chrome.declarativeNetRequest.getDynamicRules()
  await chrome.declarativeNetRequest.updateDynamicRules({
    removeRuleIds: existing.map((r) => r.id),
    addRules: [],
  })
}

export async function isEnabled(): Promise<boolean> {
  const result = await chrome.storage.local.get('blocker_enabled')
  return result['blocker_enabled'] !== false // default: enabled
}

export async function setEnabled(enabled: boolean): Promise<void> {
  await chrome.storage.local.set({ blocker_enabled: enabled })
}
