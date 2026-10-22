/* ============================================================================
 * OmniRush Chameleon — background service worker (MV3)
 *
 * Browser-bodyguard duties that a content script cannot do:
 *   1. WebRTC IP leak prevention  (chrome.privacy.network.webRTCIPHandlingPolicy)
 *   2. Blocking speculative DNS prefetch / preconnect
 *      (chrome.privacy.network.networkPredictionEnabled)
 *   3. Optional PAC routing of browser traffic through the local
 *      Smart Proxy Engine at 127.0.0.1:8899 (chrome.proxy)
 *
 * Settings live in chrome.storage.local under the key "leakProtection" and
 * are re-applied on install, on browser startup, and whenever they change.
 * All code below is original; no third-party code is used.
 * ========================================================================== */
'use strict';

const PROXY_HOST = '127.0.0.1';
const PROXY_PORT = 8899;
const STATUS_URL = 'http://127.0.0.1:3300/api/status';
const STORAGE_KEY = 'leakProtection';

/* Valid chrome.privacy.network.webRTCIPHandlingPolicy values. */
const WEBRTC_POLICIES = [
  'disable_non_proxied_udp',
  'default_public_interface_only',
  'default',
];

const DEFAULT_SETTINGS = {
  /* Maximum protection: WebRTC may not use non-proxied UDP, so the real IP
     cannot leak through STUN/ICE when the proxy engine is routing traffic. */
  webrtcPolicy: 'disable_non_proxied_udp',
  /* When true, networkPredictionEnabled is forced OFF so Chrome does not
     speculatively resolve DNS / preconnect to the ISP resolver. */
  blockSpeculative: true,
  /* When true, browser traffic is routed through 127.0.0.1:8899 via PAC. */
  proxyEnabled: false,
  /* NetShield-style domain blocking (declarativeNetRequest static rulesets). */
  blockAdsTrackers: true,
  blockMalware: true,
};

const RULESET_ADS = 'ads_trackers';
const RULESET_MALWARE = 'malware_unwanted';
const STATS_KEY = 'netshieldStats';

function buildPacScript() {
  return [
    'function FindProxyForURL(url, host) {',
    '  // Never loop the engine itself or loopback traffic through the proxy.',
    '  if (host === "127.0.0.1" || host === "localhost" || host === "[::1]") return "DIRECT";',
    '  if (isPlainHostName(host)) return "DIRECT";',
    '  // LAN bypass: keep local-network traffic direct (mirrors VPN LAN-bypass).',
    '  if (isInNet(host, "10.0.0.0", "255.0.0.0")) return "DIRECT";',
    '  if (isInNet(host, "172.16.0.0", "255.240.0.0")) return "DIRECT";',
    '  if (isInNet(host, "192.168.0.0", "255.255.0.0")) return "DIRECT";',
    '  if (isInNet(host, "169.254.0.0", "255.255.0.0")) return "DIRECT";',
    '  // Everything else goes through the local Smart Proxy Engine.',
    '  // Fail closed: there is intentionally no DIRECT fallback, so traffic',
    '  // cannot silently leak outside the proxy when the engine is down.',
    `  return "PROXY ${PROXY_HOST}:${PROXY_PORT}";`,
    '}',
  ].join('\n');
}

async function getSettings() {
  const stored = await chrome.storage.local.get(STORAGE_KEY);
  const saved = stored[STORAGE_KEY] || {};
  const merged = { ...DEFAULT_SETTINGS, ...saved };
  if (!WEBRTC_POLICIES.includes(merged.webrtcPolicy)) {
    merged.webrtcPolicy = DEFAULT_SETTINGS.webrtcPolicy;
  }
  return merged;
}

async function saveSettings(patch) {
  const next = { ...(await getSettings()), ...(patch || {}) };
  await chrome.storage.local.set({ [STORAGE_KEY]: next });
  return next;
}

function setPrivacy(setting, value) {
  return new Promise((resolve) => {
    if (!setting || typeof setting.set !== 'function') {
      resolve({ ok: false, reason: 'privacy API unavailable in this browser' });
      return;
    }
    setting.get({}, (details) => {
      if (
        details &&
        details.levelOfControl &&
        details.levelOfControl !== 'controllable_by_this_extension' &&
        details.levelOfControl !== 'controlled_by_this_extension'
      ) {
        resolve({ ok: false, reason: `controlled by ${details.levelOfControl}` });
        return;
      }
      setting.set({ value }, () => {
        if (chrome.runtime.lastError) {
          resolve({ ok: false, reason: chrome.runtime.lastError.message });
        } else {
          resolve({ ok: true });
        }
      });
    });
  });
}

function applyWebRtcPolicy(policy) {
  const setting =
    chrome.privacy && chrome.privacy.network && chrome.privacy.network.webRTCIPHandlingPolicy;
  return setPrivacy(setting, policy);
}

function applySpeculativeBlock(block) {
  const setting =
    chrome.privacy && chrome.privacy.network && chrome.privacy.network.networkPredictionEnabled;
  /* networkPredictionEnabled=true means "allow prefetch"; we invert the toggle. */
  return setPrivacy(setting, !block);
}

function applyProxy(enabled) {
  return new Promise((resolve) => {
    if (!chrome.proxy || !chrome.proxy.settings) {
      resolve({ ok: false, reason: 'proxy API unavailable in this browser' });
      return;
    }
    const done = () => {
      if (chrome.runtime.lastError) {
        resolve({ ok: false, reason: chrome.runtime.lastError.message });
      } else {
        resolve({ ok: true });
      }
    };
    if (enabled) {
      chrome.proxy.settings.set(
        {
          value: {
            mode: 'pac_script',
            pacScript: { data: buildPacScript(), mandatory: true },
          },
          scope: 'regular',
        },
        done
      );
    } else {
      chrome.proxy.settings.clear({ scope: 'regular' }, done);
    }
  });
}

async function applyAll(settings) {
  const s = settings || (await getSettings());
  return {
    webrtc: await applyWebRtcPolicy(s.webrtcPolicy),
    speculative: await applySpeculativeBlock(s.blockSpeculative),
    proxy: await applyProxy(s.proxyEnabled),
    blocking: await applyDomainBlocking(s),
  };
}

/* NetShield-style domain blocking via declarativeNetRequest static rulesets.
   Toggleable per category without reloading the extension. */
function applyDomainBlocking(settings) {
  return new Promise((resolve) => {
    if (!chrome.declarativeNetRequest || !chrome.declarativeNetRequest.updateEnabledRulesets) {
      resolve({ ok: false, reason: 'declarativeNetRequest unavailable' });
      return;
    }
    const enableRulesetIds = [];
    const disableRulesetIds = [];
    (settings.blockAdsTrackers ? enableRulesetIds : disableRulesetIds).push(RULESET_ADS);
    (settings.blockMalware ? enableRulesetIds : disableRulesetIds).push(RULESET_MALWARE);
    chrome.declarativeNetRequest.updateEnabledRulesets(
      { enableRulesetIds, disableRulesetIds },
      () => {
        if (chrome.runtime.lastError) {
          resolve({ ok: false, reason: chrome.runtime.lastError.message });
        } else {
          resolve({ ok: true });
        }
      }
    );
  });
}

/* Block counters: incremented whenever a static rule matches a request.
   Best-effort — the debug event may not fire in every browser build. */
async function bumpStat(kind) {
  try {
    const stored = await chrome.storage.local.get(STATS_KEY);
    const stats = stored[STATS_KEY] || { adsBlocked: 0, malwareBlocked: 0, since: Date.now() };
    if (kind === RULESET_ADS) stats.adsBlocked = (stats.adsBlocked || 0) + 1;
    else stats.malwareBlocked = (stats.malwareBlocked || 0) + 1;
    await chrome.storage.local.set({ [STATS_KEY]: stats });
  } catch (e) { /* storage failure must never break browsing */ }
}

if (chrome.declarativeNetRequest && chrome.declarativeNetRequest.onRuleMatchedDebug) {
  chrome.declarativeNetRequest.onRuleMatchedDebug.addListener((info) => {
    if (!info || !info.rule || !info.rule.rulesetId) return;
    bumpStat(info.rule.rulesetId).catch(() => {});
  });
}

function getProxyControl() {
  return new Promise((resolve) => {
    if (!chrome.proxy || !chrome.proxy.settings) {
      resolve({ levelOfControl: 'unavailable' });
      return;
    }
    chrome.proxy.settings.get({ incognito: false }, (details) => {
      resolve({
        levelOfControl: (details && details.levelOfControl) || 'unknown',
      });
    });
  });
}

async function isEngineOnline() {
  try {
    const res = await fetch(STATUS_URL, { cache: 'no-store' });
    return res.ok;
  } catch (e) {
    return false;
  }
}

/* Re-apply stored settings on install / update / browser startup. */
chrome.runtime.onInstalled.addListener(() => {
  applyAll().catch(() => {});
});

chrome.runtime.onStartup.addListener(() => {
  applyAll().catch(() => {});
});

/* The popup writes to storage; the service worker applies the change. */
chrome.storage.onChanged.addListener((changes, area) => {
  if (area === 'local' && changes[STORAGE_KEY]) {
    applyAll(changes[STORAGE_KEY].newValue).catch(() => {});
  }
});

/* If something else grabs the WebRTC setting while we are awake, take it back. */

if (chrome.privacy && chrome.privacy.network) {
  const webrtcSetting = chrome.privacy.network.webRTCIPHandlingPolicy;
  if (webrtcSetting && webrtcSetting.onChange && typeof webrtcSetting.onChange.addListener === 'function') {
    webrtcSetting.onChange.addListener((details) => {
      if (!details) return;
      /* Re-assert whatever the user currently has selected, not the default. */
      getSettings().then((s) => {
        if (details.value !== s.webrtcPolicy) {
          applyWebRtcPolicy(s.webrtcPolicy).catch(() => {});
        }
      });
    });
  }
}

/* Popup <-> background messaging. */
chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
  (async () => {
    if (!msg || typeof msg.type !== 'string') {
      sendResponse(null);
      return;
    }
    if (msg.type === 'getLeakState') {
      const settings = await getSettings();
      const proxyControl = await getProxyControl();
      const engineOnline = await isEngineOnline();
      const stored = await chrome.storage.local.get(STATS_KEY);
      const stats = stored[STATS_KEY] || { adsBlocked: 0, malwareBlocked: 0 };
      sendResponse({ settings, proxyControl, engineOnline, stats });
    } else if (msg.type === 'setLeakSettings') {
      const settings = await saveSettings(msg.patch);
      sendResponse({ settings });
    } else if (msg.type === 'resetBlockStats') {
      await chrome.storage.local.set({
        [STATS_KEY]: { adsBlocked: 0, malwareBlocked: 0, since: Date.now() },
      });
      sendResponse({ ok: true });
    } else {
      sendResponse(null);
    }
  })().catch(() => sendResponse(null));
  return true; /* async response */
});
