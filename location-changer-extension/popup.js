/* ============================================================================
 * OmniRush Chameleon — popup logic
 *
 * Two jobs:
 *   1. Show the active spoofed jurisdiction (from the local dashboard API,
 *      falling back to the same 5-minute epoch math the content script uses).
 *   2. Drive the Leak Protection section: read state from the background
 *      service worker and persist toggle changes to chrome.storage.
 *
 * All code below is original; no third-party code is used.
 * ========================================================================== */
'use strict';

/* ---------------- Spoofed-node display (existing behavior) ---------------- */

const DEFAULT_NODES = [
  { city: 'Tokyo', country: 'Japan', flag: '🇯🇵', lat: 35.6762, lon: 139.6503 },
  { city: 'Zurich', country: 'Switzerland', flag: '🇨🇭', lat: 47.3769, lon: 8.5417 },
  { city: 'New York', country: 'United States', flag: '🇺🇸', lat: 40.7128, lon: -74.0060 },
  { city: 'Reykjavik', country: 'Iceland', flag: '🇮🇸', lat: 64.1466, lon: -21.9426 },
  { city: 'Singapore', country: 'Singapore', flag: '🇸🇬', lat: 1.3521, lon: 103.8198 },
  { city: 'Berlin', country: 'Germany', flag: '🇩🇪', lat: 52.5200, lon: 13.4050 }
];

function renderNode(node) {
  const cityEl = document.getElementById('city-text');
  const coordsEl = document.getElementById('coords-text');
  if (cityEl) cityEl.textContent = `${node.flag} ${node.city}, ${node.country}`;
  if (coordsEl) coordsEl.textContent = `Lat: ${Number(node.lat).toFixed(4)}, Lon: ${Number(node.lon).toFixed(4)}`;
}

function computeLocalNode() {
  const epochFiveMinutes = Math.floor(Date.now() / (5 * 60 * 1000));
  return DEFAULT_NODES[epochFiveMinutes % DEFAULT_NODES.length];
}

/* Initial immediate render (script runs at end of body). */
renderNode(computeLocalNode());

async function updatePopup() {
  try {
    const res = await fetch('http://127.0.0.1:3300/api/status');
    if (res.ok) {
      const d = await res.json();
      if (d.currentNode) {
        renderNode(d.currentNode);
        const badgeEl = document.getElementById('countdown-badge');
        if (badgeEl && d.countdownSeconds !== undefined) {
          const m = Math.floor(d.countdownSeconds / 60);
          const s = d.countdownSeconds % 60;
          badgeEl.textContent = `⏱️ ${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
        }
        return;
      }
    }
  } catch (e) {
    /* Dashboard unreachable — fall through to local epoch math. */
  }

  renderNode(computeLocalNode());
}

/* ---------------- Leak Protection controls ---------------- */

function sendToBackground(type, patch) {
  return new Promise((resolve) => {
    try {
      chrome.runtime.sendMessage({ type, patch }, (resp) => {
        /* chrome.runtime.lastError is set if the worker is unavailable. */
        resolve(chrome.runtime.lastError ? null : resp || null);
      });
    } catch (e) {
      resolve(null);
    }
  });
}

async function refreshLeakUI() {
  const state = await sendToBackground('getLeakState');
  if (!state || !state.settings) return;
  const { settings, proxyControl, engineOnline, stats } = state;

  const select = document.getElementById('webrtc-select');
  if (select && settings.webrtcPolicy) select.value = settings.webrtcPolicy;

  const specToggle = document.getElementById('spec-toggle');
  if (specToggle) specToggle.checked = !!settings.blockSpeculative;

  const proxyToggle = document.getElementById('proxy-toggle');
  if (proxyToggle) proxyToggle.checked = !!settings.proxyEnabled;

  const adsToggle = document.getElementById('ads-toggle');
  if (adsToggle) adsToggle.checked = !!settings.blockAdsTrackers;

  const malwareToggle = document.getElementById('malware-toggle');
  if (malwareToggle) malwareToggle.checked = !!settings.blockMalware;

  const adsCount = document.getElementById('ads-count');
  if (adsCount && stats) adsCount.textContent = Number(stats.adsBlocked || 0).toLocaleString();
  const malwareCount = document.getElementById('malware-count');
  if (malwareCount && stats) malwareCount.textContent = Number(stats.malwareBlocked || 0).toLocaleString();

  const engineDot = document.getElementById('engine-dot');
  if (engineDot) {
    engineDot.className = 'dot ' + (engineOnline ? 'on' : 'off');
    engineDot.title = engineOnline
      ? 'Local proxy engine reachable (127.0.0.1:3300)'
      : 'Local proxy engine offline';
  }

  const warn = document.getElementById('proxy-warning');
  if (warn) warn.hidden = !(settings.proxyEnabled && !engineOnline);

  const ctrlWarn = document.getElementById('proxy-control-warning');
  if (ctrlWarn) {
    const level = proxyControl && proxyControl.levelOfControl;
    const foreign =
      level &&
      level !== 'controllable_by_this_extension' &&
      level !== 'controlled_by_this_extension';
    ctrlWarn.hidden = !foreign;
    if (foreign) {
      ctrlWarn.textContent =
        `⚠️ Proxy is currently managed by "${level.replace(/_/g, ' ')}" ` +
        `(e.g. another extension or policy). OmniRush cannot apply its PAC ` +
        `until that control is released.`;
    }
  }
}

async function pushSetting(patch) {
  await sendToBackground('setLeakSettings', patch);
  /* The service worker applies the change via storage.onChanged; re-read to
     reflect any failure (e.g. another extension owns the proxy). */
  await refreshLeakUI();
}

document.addEventListener('DOMContentLoaded', () => {
  renderNode(computeLocalNode());
  updatePopup();
  setInterval(updatePopup, 1000);
  refreshLeakUI();

  const select = document.getElementById('webrtc-select');
  if (select) {
    select.addEventListener('change', (e) => pushSetting({ webrtcPolicy: e.target.value }));
  }

  const specToggle = document.getElementById('spec-toggle');
  if (specToggle) {
    specToggle.addEventListener('change', (e) => pushSetting({ blockSpeculative: e.target.checked }));
  }

  const proxyToggle = document.getElementById('proxy-toggle');
  if (proxyToggle) {
    proxyToggle.addEventListener('change', (e) => pushSetting({ proxyEnabled: e.target.checked }));
  }

  const adsToggle = document.getElementById('ads-toggle');
  if (adsToggle) {
    adsToggle.addEventListener('change', (e) => pushSetting({ blockAdsTrackers: e.target.checked }));
  }

  const malwareToggle = document.getElementById('malware-toggle');
  if (malwareToggle) {
    malwareToggle.addEventListener('change', (e) => pushSetting({ blockMalware: e.target.checked }));
  }

  const resetBtn = document.getElementById('reset-stats-btn');
  if (resetBtn) {
    resetBtn.addEventListener('click', async () => {
      await sendToBackground('resetBlockStats');
      await refreshLeakUI();
    });
  }

  const copyBtn = document.getElementById('copy-dns-link');
  if (copyBtn) {
    copyBtn.addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText('chrome://settings/security');
        const original = copyBtn.textContent;
        copyBtn.textContent = 'Copied ✓';
        setTimeout(() => { copyBtn.textContent = original; }, 1600);
      } catch (err) {
        /* Clipboard unavailable — the URL is shown in the hint text. */
      }
    });
  }
});
