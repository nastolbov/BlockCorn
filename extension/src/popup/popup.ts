async function sendMsg(msg: object): Promise<Record<string, unknown>> {
  return new Promise((resolve) => {
    chrome.runtime.sendMessage(msg, (resp) => resolve(resp ?? {}))
  })
}

async function init() {
  const resp = await sendMsg({ type: 'GET_STATUS' })
  const enabled = resp['enabled'] as boolean

  const indicator = document.getElementById('indicator')!
  const statusText = document.getElementById('status-text')!
  const statusSub = document.getElementById('status-sub')!
  const toggle = document.getElementById('toggle-enabled') as HTMLInputElement
  const footer = document.getElementById('footer')!

  function updateUI(active: boolean) {
    toggle.checked = active
    indicator.className = `indicator ${active ? 'on' : 'off'}`
    statusText.textContent = active ? 'Фильтр активен' : 'Фильтр отключён'
    statusSub.textContent = active ? 'Блокировка контента включена' : 'Нажмите переключатель для включения'
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
    const btn = document.getElementById('btn-refresh')!
    btn.textContent = '↻ Обновляем...'
    ;(btn as HTMLButtonElement).disabled = true
    await sendMsg({ type: 'FORCE_REFRESH' })
    btn.textContent = '↻ Обновить список'
    ;(btn as HTMLButtonElement).disabled = false
    footer.textContent = 'Список обновлён!'
    setTimeout(() => { footer.textContent = '' }, 2000)
  })

  document.getElementById('btn-settings')!.addEventListener('click', () => {
    chrome.runtime.openOptionsPage?.()
  })
}

init()
