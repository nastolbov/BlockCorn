/**
 * NSFW image classifier — runtime CDN loading architecture.
 *
 * nsfwjs + TF.js (~3 MB) are NOT bundled with the extension to stay within
 * the Chrome Web Store 5 MB package limit.  Instead, when imageClassification
 * is enabled they are loaded from jsDelivr at runtime.
 *
 * Because MV3 content scripts run in an isolated world we inject a <script>
 * into the MAIN world that does the actual TF.js work, then sends results
 * back via postMessage.
 */

const TFJS_CDN    = 'https://cdn.jsdelivr.net/npm/@tensorflow/tfjs@4.22.0/dist/tf.min.js'
const NSFWJS_CDN  = 'https://cdn.jsdelivr.net/npm/nsfwjs@4.3.0/dist/nsfwjs.min.js'
const BLOCK_SCORE = 0.7
const MIN_SIZE_PX = 100

let injected = false

/** Inject the main-world runner and listen for its results. */
export async function classifyImages(): Promise<void> {
  if (injected) return
  injected = true

  // Listen for classification results from the main-world script
  window.addEventListener('message', handleNsfwResult)

  // Inject runner into main world (loads TF.js + nsfwjs from CDN)
  injectMainWorldRunner()
}

function handleNsfwResult(event: MessageEvent) {
  if (event.source !== window) return
  const data = event.data as { type?: string; src?: string; score?: number }
  if (data?.type !== 'BLOCKCORN_NSFW' || data.score == null) return

  if (data.score > BLOCK_SCORE) {
    const img = document.querySelector<HTMLImageElement>(
      `img[src="${CSS.escape(data.src ?? '')}"]`
    )
    if (img) hideImage(img, data.score)
  }
}

function injectMainWorldRunner() {
  // This script tag executes in the MAIN world (page context),
  // where it can access window.tf and window.nsfwjs after CDN load.
  const script = document.createElement('script')
  script.textContent = buildRunnerSource()
  document.documentElement.appendChild(script)
  script.remove()
}

function buildRunnerSource(): string {
  return `
(async function blockCornNsfwRunner() {
  const TFJS    = ${JSON.stringify(TFJS_CDN)};
  const NSFWJS  = ${JSON.stringify(NSFWJS_CDN)};
  const MIN_PX  = ${MIN_SIZE_PX};

  function loadScript(src) {
    return new Promise((res, rej) => {
      const s = document.createElement('script');
      s.src = src; s.crossOrigin = 'anonymous';
      s.onload = res; s.onerror = rej;
      document.head.appendChild(s);
    });
  }

  try {
    if (!window.tf)      await loadScript(TFJS);
    if (!window.nsfwjs)  await loadScript(NSFWJS);

    const model = await window.nsfwjs.load(
      'https://nsfwjs.com/quant_nsfw_model/', { type: 'graph' }
    );

    async function checkImg(img) {
      if (img.dataset.nsfwDone) return;
      img.dataset.nsfwDone = '1';
      if (img.naturalWidth < MIN_PX || img.naturalHeight < MIN_PX) return;
      try {
        const preds = await model.classify(img);
        const score = preds
          .filter(p => p.className === 'Porn' || p.className === 'Hentai')
          .reduce((s, p) => s + p.probability, 0);
        window.postMessage({ type: 'BLOCKCORN_NSFW', src: img.src, score }, '*');
      } catch (e) { /* cross-origin tainted canvas — skip */ }
    }

    const imgs = Array.from(document.images);
    for (const img of imgs) {
      if (img.complete) checkImg(img);
      else img.addEventListener('load', () => checkImg(img), { once: true });
    }

    new MutationObserver(muts => {
      for (const m of muts) for (const n of m.addedNodes) {
        if (n instanceof HTMLImageElement) {
          if (n.complete) checkImg(n);
          else n.addEventListener('load', () => checkImg(n), { once: true });
        } else if (n instanceof Element) {
          n.querySelectorAll('img').forEach(img => {
            if (img.complete) checkImg(img);
            else img.addEventListener('load', () => checkImg(img), { once: true });
          });
        }
      }
    }).observe(document.body, { childList: true, subtree: true });

  } catch (err) {
    console.warn('[BlockCorn] nsfwjs load failed:', err);
  }
})();
`.trim()
}

function hideImage(img: HTMLImageElement, score: number): void {
  const w = img.offsetWidth  || img.naturalWidth  || 120
  const h = img.offsetHeight || img.naturalHeight || 120

  const wrapper = document.createElement('div')
  wrapper.style.cssText = [
    'display:inline-flex',
    'align-items:center',
    'justify-content:center',
    'flex-direction:column',
    'gap:6px',
    `width:${w}px`,
    `height:${h}px`,
    'min-width:80px',
    'min-height:80px',
    'background:#0f0f13',
    'border-radius:8px',
    'color:#7c7c9a',
    'font:13px/1.4 system-ui,sans-serif',
    'text-align:center',
    'padding:8px',
    'box-sizing:border-box',
    'vertical-align:middle',
  ].join(';')

  wrapper.innerHTML = `
    <span style="font-size:22px">🛡️</span>
    <span>Скрыто BlockCorn<br>
      <small style="color:#ef4444">NSFW ${Math.round(score * 100)}%</small>
    </span>`

  img.replaceWith(wrapper)
}
