async function sendMsg(msg: object): Promise<Record<string, unknown>> {
  return new Promise((resolve) => {
    chrome.runtime.sendMessage(msg, (resp) => resolve(resp ?? {}))
  })
}

async function init() {
  const [statusResp, settingsResp] = await Promise.all([
    sendMsg({ type: 'GET_STATUS' }),
    sendMsg({ type: 'GET_SETTINGS' }),
  ])

  const enabled     = statusResp['enabled'] as boolean
  let   threshold   = (settingsResp['triggerThreshold'] as number) ?? 5
  let   imgClassify = (settingsResp['imageClassification'] as boolean) ?? false

  const indicator  = document.getElementById('indicator')!
  const statusText = document.getElementById('status-text')!
  const statusSub  = document.getElementById('status-sub')!
  const toggle     = document.getElementById('toggle-enabled') as HTMLInputElement
  const footer     = document.getElementById('footer')!

  function updateUI(active: boolean) {
    toggle.checked = active
    indicator.className = `indicator ${active ? 'on' : 'off'}`
    statusText.textContent = active ? 'Фильтр активен' : 'Фильтр отключён'
    statusSub.textContent  = active
      ? 'Блокировка контента включена'
      : 'Нажмите переключатель для включения'
  }

  updateUI(enabled)

  toggle.addEventListener('change', async () => {
    const next = toggle.checked
    updateUI(next)
    footer.textContent = next ? 'Включаем...' : 'Отключаем...'
    await sendMsg({ type: 'SET_ENABLED', enabled: next })
    footer.textContent = ''
  })

  document.getElementById('btn-refresh')!.addEventListener('click', async () => {
    const btn = document.getElementById('btn-refresh') as HTMLButtonElement
    btn.textContent = '↻ Обновляем...'
    btn.disabled = true
    await sendMsg({ type: 'FORCE_REFRESH' })
    btn.textContent = '↻ Обновить список'
    btn.disabled = false
    footer.textContent = 'Список обновлён!'
    setTimeout(() => { footer.textContent = '' }, 2000)
  })

  // ── Advanced settings panel ───────────────────────────────────────────────

  const advancedToggle = document.getElementById('btn-advanced') as HTMLButtonElement
  const advancedPanel  = document.getElementById('advanced-panel')!
  const thresholdInput = document.getElementById('threshold-input') as HTMLInputElement
  const imgToggle      = document.getElementById('toggle-img') as HTMLInputElement

  thresholdInput.value  = String(threshold)
  imgToggle.checked     = imgClassify

  advancedToggle.addEventListener('click', () => {
    const hidden = advancedPanel.style.display === 'none'
    advancedPanel.style.display = hidden ? 'block' : 'none'
    advancedToggle.textContent  = hidden ? '▲ Настройки' : '⚙ Настройки'
  })

  async function saveSettings() {
    threshold   = Math.max(1, Math.min(50, Number(thresholdInput.value) || 5))
    imgClassify = imgToggle.checked
    thresholdInput.value = String(threshold)
    await sendMsg({ type: 'SET_SETTINGS', triggerThreshold: threshold, imageClassification: imgClassify })
    footer.textContent = 'Сохранено ✓'
    setTimeout(() => { footer.textContent = '' }, 1500)
  }

  document.getElementById('btn-save-settings')!.addEventListener('click', saveSettings)
}

init()
