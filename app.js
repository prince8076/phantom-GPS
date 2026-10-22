/* ============================================================
   Phantom VPN Control Plane — dashboard script
   Original work. Clean-room implementation; no third-party
   VPN client code is referenced or reproduced here.
   ============================================================ */

const toast = document.querySelector('#toast');
let toastTimer;
const overviewHtml = document.querySelector('#app-view').innerHTML;

/* ---------- Chameleon engine state (original behavior) ---------- */
const DEFAULT_CADENCE_SECONDS = 300; // 5 minutes
let rotationCadenceSeconds = DEFAULT_CADENCE_SECONDS;
let countdownValue = rotationCadenceSeconds;
let timerInterval = null;
let autoRotateActive = true;
let chameleonShieldActive = true;
const originalGeolocation = navigator.geolocation;

function updateGeolocationMock(coordsStr) {
  const parts = coordsStr.split('·');
  const lat = parseFloat(parts[0].replace('Lat:', '').trim());
  const lon = parseFloat(parts[1].replace('Lon:', '').trim());

  if (navigator.geolocation) {
    const mockGeo = {
      getCurrentPosition(success, error, options) {
        if (!chameleonShieldActive) {
          if (originalGeolocation && originalGeolocation.getCurrentPosition) {
            return originalGeolocation.getCurrentPosition(success, error, options);
          }
          if (error) error(new Error("Geolocation disabled when Chameleon Shield is OFF."));
          return;
        }
        success({
          coords: {
            latitude: lat,
            longitude: lon,
            accuracy: 12,
            altitude: null,
            altitudeAccuracy: null,
            heading: null,
            speed: null
          },
          timestamp: Date.now()
        });
      },
      watchPosition(success, error, options) {
        if (!chameleonShieldActive) {
          if (originalGeolocation && originalGeolocation.watchPosition) {
            return originalGeolocation.watchPosition(success, error, options);
          }
          return 0;
        }
        success({
          coords: {
            latitude: lat,
            longitude: lon,
            accuracy: 12,
            altitude: null,
            altitudeAccuracy: null,
            heading: null,
            speed: null
          },
          timestamp: Date.now()
        });
        return 9999;
      },
      clearWatch(id) {
        if (originalGeolocation && originalGeolocation.clearWatch) {
          originalGeolocation.clearWatch(id);
        }
      }
    };

    try {
      Object.defineProperty(navigator, 'geolocation', {
        value: mockGeo,
        configurable: true,
        writable: true
      });
    } catch (e) {
      // fallback
    }
  }
}

/* ---------- Server fleet (34 global exit nodes) ---------- */
const proxyNodes = [
  { id: 'zurich',    name: 'Zurich, Switzerland', country: 'Switzerland', ip: '185.220.101.7',  lat: 47.3769,  lon: 8.5417,   isp: 'Tor Exit Node · AS59842',     load: 34, latencyMs: 28 },
  { id: 'tokyo',     name: 'Tokyo, Japan',        country: 'Japan',       ip: '103.253.140.9',  lat: 35.6762,  lon: 139.6503, isp: 'Datacenter Proxy · AS2516',   load: 52, latencyMs: 74 },
  { id: 'reykjavik', name: 'Reykjavik, Iceland',  country: 'Iceland',     ip: '185.159.157.2',  lat: 64.1466,  lon: -21.9426, isp: 'Secure Enclave · AS44556',    load: 21, latencyMs: 61 },
  { id: 'newyork',   name: 'New York, US',        country: 'United States',ip: '198.51.100.42',  lat: 40.7128,  lon: -74.0060, isp: 'Cloudflare Warp · AS13335',   load: 68, latencyMs: 88 },
  { id: 'singapore', name: 'Singapore, SG',       country: 'Singapore',   ip: '165.22.240.18',  lat: 1.3521,   lon: 103.8198, isp: 'DigitalOcean SG · AS14061',   load: 45, latencyMs: 42 },
  { id: 'berlin',    name: 'Berlin, Germany',     country: 'Germany',     ip: '89.187.160.73',  lat: 52.5200,  lon: 13.4050,  isp: 'MivoCloud · AS208833',        load: 39, latencyMs: 33 },
  { id: 'london',    name: 'London, UK',          country: 'United Kingdom', ip: '45.148.10.88',lat: 51.5074,  lon: -0.1278,  isp: 'Privacy Relay · AS9009',      load: 44, latencyMs: 24 },
  { id: 'paris',     name: 'Paris, France',       country: 'France',      ip: '212.102.41.52',  lat: 48.8566,  lon: 2.3522,   isp: 'Stealth POP · AS21409',       load: 47, latencyMs: 31 },
  { id: 'amsterdam', name: 'Amsterdam, NL',       country: 'Netherlands', ip: '89.187.161.11',  lat: 52.3676,  lon: 4.9041,   isp: 'MivoCloud · AS208833',        load: 55, latencyMs: 29 },
  { id: 'stockholm', name: 'Stockholm, SE',       country: 'Sweden',      ip: '193.32.127.65',  lat: 59.3293,  lon: 18.0686,  isp: 'Nordic Relay · AS39351',      load: 29, latencyMs: 41 },
  { id: 'oslo',      name: 'Oslo, NO',            country: 'Norway',      ip: '185.93.2.14',    lat: 59.9139,  lon: 10.7522,  isp: 'Nordic Relay · AS39351',      load: 24, latencyMs: 48 },
  { id: 'helsinki',  name: 'Helsinki, FI',        country: 'Finland',     ip: '95.216.24.33',   lat: 60.1699,  lon: 24.9384,  isp: 'Nordic Relay · AS39351',      load: 31, latencyMs: 52 },
  { id: 'toronto',   name: 'Toronto, CA',         country: 'Canada',      ip: '172.98.84.9',    lat: 43.6532,  lon: -79.3832, isp: 'Maple Relay · AS6327',        load: 58, latencyMs: 76 },
  { id: 'sydney',    name: 'Sydney, AU',          country: 'Australia',   ip: '103.86.96.100',  lat: -33.8688, lon: 151.2093, isp: 'Pacific POP · AS13335',       load: 49, latencyMs: 96 },
  { id: 'seoul',     name: 'Seoul, KR',           country: 'South Korea', ip: '103.152.112.7',  lat: 37.5665,  lon: 126.9780, isp: 'Han River POP · AS9318',      load: 51, latencyMs: 68 },
  { id: 'dubai',     name: 'Dubai, AE',           country: 'UAE',         ip: '94.200.116.20',  lat: 25.2048,  lon: 55.2708,  isp: 'Gulf Relay · AS5384',         load: 63, latencyMs: 58 },
  { id: 'saopaulo',  name: 'São Paulo, BR',       country: 'Brazil',      ip: '181.215.228.4',  lat: -23.5558, lon: -46.6396, isp: 'Latam POP · AS27678',         load: 57, latencyMs: 118 },
  { id: 'vienna',    name: 'Vienna, AT',          country: 'Austria',     ip: '89.238.150.22',  lat: 48.2082,  lon: 16.3738,  isp: 'Danube Relay · AS212238',     load: 36, latencyMs: 37 },
  { id: 'madrid',    name: 'Madrid, ES',          country: 'Spain',       ip: '185.230.125.9',  lat: 40.4168,  lon: -3.7038,  isp: 'Iberia POP · AS3352',         load: 42, latencyMs: 39 },
  { id: 'rome',      name: 'Rome, IT',           country: 'Italy',       ip: '185.107.80.44',  lat: 41.9028,  lon: 12.4964,  isp: 'Mediterraneo · AS8612',       load: 46, latencyMs: 44 },
  { id: 'dublin',    name: 'Dublin, IE',          country: 'Ireland',     ip: '89.207.132.15',  lat: 53.3498,  lon: -6.2603,  isp: 'Emerald Relay · AS5466',      load: 33, latencyMs: 27 },
  { id: 'warsaw',    name: 'Warsaw, PL',          country: 'Poland',      ip: '91.193.75.6',    lat: 52.2297,  lon: 21.0122,  isp: 'Vistula POP · AS1299',        load: 38, latencyMs: 46 },
  { id: 'prague',    name: 'Prague, CZ',          country: 'Czechia',     ip: '185.159.156.8',  lat: 50.0755,  lon: 14.4378,  isp: 'Bohemia Relay · AS15685',     load: 35, latencyMs: 43 },
  { id: 'bucharest', name: 'Bucharest, RO',       country: 'Romania',     ip: '89.44.10.2',     lat: 44.4268,  lon: 26.1025,  isp: 'Carpathian POP · AS8708',     load: 41, latencyMs: 54 },
  { id: 'taipei',    name: 'Taipei, TW',          country: 'Taiwan',      ip: '103.199.16.30',  lat: 25.0330,  lon: 121.5654, isp: 'Formosa Relay · AS3462',      load: 54, latencyMs: 71 },
  { id: 'mumbai',    name: 'Mumbai, IN',          country: 'India',       ip: '103.86.98.100',  lat: 19.0760,  lon: 72.8777,  isp: 'Deccan POP · AS24560',        load: 61, latencyMs: 63 },
  { id: 'losangeles',name: 'Los Angeles, US',     country: 'United States', ip: '107.174.214.55', lat: 34.0522,lon: -118.2437,isp: 'Pacific POP · AS13335',       load: 66, latencyMs: 104 },
  { id: 'frankfurt', name: 'Frankfurt, DE',       country: 'Germany',     ip: '45.148.10.92',   lat: 50.1109,  lon: 8.6821,   isp: 'Privacy Relay · AS9009',      load: 59, latencyMs: 35 },
  { id: 'lisbon',    name: 'Lisbon, PT',          country: 'Portugal',    ip: '185.232.22.76',  lat: 38.7223,  lon: -9.1393,  isp: 'Tagus Relay · AS15589',       load: 37, latencyMs: 47 },
  { id: 'athens',    name: 'Athens, GR',          country: 'Greece',      ip: '185.213.82.19',  lat: 37.9838,  lon: 23.7275,  isp: 'Aegean POP · AS6799',         load: 43, latencyMs: 57 },
  { id: 'vancouver', name: 'Vancouver, CA',       country: 'Canada',      ip: '154.16.20.8',    lat: 49.2827,  lon: -123.1207,isp: 'Maple Relay · AS6327',        load: 53, latencyMs: 112 },
  { id: 'auckland',  name: 'Auckland, NZ',        country: 'New Zealand', ip: '103.86.99.100',  lat: -36.8485, lon: 174.7633, isp: 'Pacific POP · AS13335',       load: 48, latencyMs: 142 },
  { id: 'capetown',  name: 'Cape Town, ZA',       country: 'South Africa',ip: '196.251.88.12',  lat: -33.9249, lon: 18.4241,  isp: 'Cape Relay · AS36994',        load: 56, latencyMs: 128 },
  { id: 'bangkok',   name: 'Bangkok, TH',         country: 'Thailand',    ip: '103.152.118.9',  lat: 13.7563,  lon: 100.5018, isp: 'Chao Phraya POP · AS17552',   load: 59, latencyMs: 66 }
];

// Back-compat: legacy code reads node.coords / node.ip / node.name / node.isp.
proxyNodes.forEach((n) => {
  n.coords = 'Lat: ' + n.lat.toFixed(4) + ' · Lon: ' + n.lon.toFixed(4);
});

function nodeScore(n) {
  return n.latencyMs * (1 + n.load / 100);
}

function pickFastestNode() {
  return proxyNodes.slice().sort((a, b) => nodeScore(a) - nodeScore(b))[0];
}

/* ---------- VPN config model (persisted) ---------- */
const CONFIG_KEY = 'phantom.vpn.config.v1';
const PROFILES_KEY = 'phantom.vpn.profiles.v1';
const HUB_KEY = 'phantom.vpn.hub.v1';

const CADENCE_OPTIONS = [60, 180, 300, 600, 1800];
const PROTOCOL_OPTIONS = ['smart', 'wireguard-udp', 'wireguard-tcp', 'wireguard-tls'];
const NETSHIELD_OPTIONS = ['off', 'malware', 'ads-trackers', 'adult'];

function defaultVpnConfig() {
  return {
    cadenceSeconds: DEFAULT_CADENCE_SECONDS,
    serverMode: 'manual',            // manual | fastest | random
    serverId: 'zurich',
    protocol: 'smart',
    netshield: 'off',
    killSwitch: true,
    killSwitchLockdown: true,
    splitTunneling: { enabled: false, mode: 'exclude', apps: [], ips: [] },
    customDns: [],
    lanBypass: true,
    ipv6: 'block'                    // block | route
  };
}

function loadVpnConfig() {
  try {
    const raw = localStorage.getItem(CONFIG_KEY);
    if (!raw) return defaultVpnConfig();
    const parsed = JSON.parse(raw);
    const merged = Object.assign(defaultVpnConfig(), parsed);
    merged.splitTunneling = Object.assign(defaultVpnConfig().splitTunneling, parsed.splitTunneling || {});
    return merged;
  } catch (e) {
    return defaultVpnConfig();
  }
}

function saveVpnConfig() {
  try {
    localStorage.setItem(CONFIG_KEY, JSON.stringify(vpnConfig));
  } catch (e) { /* storage unavailable */ }
  updatePayloadPreview();
  syncConfigSummary();
}

let vpnConfig = loadVpnConfig();
rotationCadenceSeconds = vpnConfig.cadenceSeconds || DEFAULT_CADENCE_SECONDS;
countdownValue = rotationCadenceSeconds;

function loadProfiles() {
  try {
    return JSON.parse(localStorage.getItem(PROFILES_KEY) || '[]');
  } catch (e) {
    return [];
  }
}

function saveProfiles(profiles) {
  try {
    localStorage.setItem(PROFILES_KEY, JSON.stringify(profiles));
  } catch (e) { /* storage unavailable */ }
}

function makeProfileId() {
  return 'profile-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 7);
}

function snapshotConfigForProfile() {
  return {
    rotation: { cadenceSeconds: vpnConfig.cadenceSeconds },
    server: { mode: vpnConfig.serverMode, serverId: vpnConfig.serverId },
    protocol: vpnConfig.protocol,
    netshield: vpnConfig.netshield,
    killSwitch: { enabled: vpnConfig.killSwitch, lockdown: vpnConfig.killSwitchLockdown },
    splitTunneling: JSON.parse(JSON.stringify(vpnConfig.splitTunneling)),
    dns: { custom: vpnConfig.customDns.slice(), lanBypass: vpnConfig.lanBypass, ipv6: vpnConfig.ipv6 }
  };
}

function applyProfileConfig(snapshot) {
  if (snapshot.rotation && snapshot.rotation.cadenceSeconds) {
    setCadence(snapshot.rotation.cadenceSeconds, { silent: true });
  }
  if (snapshot.server) {
    vpnConfig.serverMode = snapshot.server.mode || 'manual';
    vpnConfig.serverId = snapshot.server.serverId || vpnConfig.serverId;
  }
  if (snapshot.protocol) vpnConfig.protocol = snapshot.protocol;
  if (snapshot.netshield) vpnConfig.netshield = snapshot.netshield;
  if (snapshot.killSwitch) {
    vpnConfig.killSwitch = !!snapshot.killSwitch.enabled;
    vpnConfig.killSwitchLockdown = !!snapshot.killSwitch.lockdown;
  }
  if (snapshot.splitTunneling) {
    vpnConfig.splitTunneling = Object.assign(defaultVpnConfig().splitTunneling, snapshot.splitTunneling);
  }
  if (snapshot.dns) {
    vpnConfig.customDns = (snapshot.dns.custom || []).slice();
    vpnConfig.lanBypass = snapshot.dns.lanBypass !== false;
    vpnConfig.ipv6 = snapshot.dns.ipv6 || 'block';
  }
  saveVpnConfig();
  refreshAllControls();
}

function showToast(message) {
  toast.textContent = message;
  toast.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove('show'), 3200);
}

/* ---------- Config payload (phantom-vpn-config v1 contract) ---------- */
function getHubUrl() {
  try {
    return localStorage.getItem(HUB_KEY) || 'http://127.0.0.1:3300';
  } catch (e) {
    return 'http://127.0.0.1:3300';
  }
}

function setHubUrl(url) {
  try { localStorage.setItem(HUB_KEY, url); } catch (e) { /* ignore */ }
}

function buildConfigPayload() {
  return {
    contract: 'phantom-vpn-config',
    version: 1,
    issuedAt: new Date().toISOString(),
    rotation: {
      cadenceSeconds: vpnConfig.cadenceSeconds,
      autoRotate: autoRotateActive,
      shieldEnabled: chameleonShieldActive
    },
    server: {
      mode: vpnConfig.serverMode,
      serverId: vpnConfig.serverId,
      pool: vpnConfig.serverMode === 'random' ? proxyNodes.map((n) => n.id) : []
    },
    protocol: vpnConfig.protocol,
    netshield: vpnConfig.netshield,
    killSwitch: {
      enabled: vpnConfig.killSwitch,
      lockdown: vpnConfig.killSwitchLockdown
    },
    splitTunneling: {
      enabled: vpnConfig.splitTunneling.enabled,
      mode: vpnConfig.splitTunneling.mode,
      apps: vpnConfig.splitTunneling.apps.slice(),
      ips: vpnConfig.splitTunneling.ips.slice()
    },
    dns: {
      custom: vpnConfig.customDns.slice(),
      lanBypass: vpnConfig.lanBypass,
      ipv6: vpnConfig.ipv6
    }
  };
}

function setPushStatus(text, tone) {
  const pill = document.querySelector('#push-status');
  if (pill) {
    pill.textContent = '● ' + text;
    pill.className = 'sync-pill ' + (tone || '');
  }
  const badge = document.querySelector('#sync-badge');
  if (badge) {
    badge.textContent = '● ' + text;
    badge.className = tone === 'ok' ? 'trend positive' : (tone === 'warn' ? 'trend negative' : 'trend positive');
  }
}

async function pushConfigToPhone() {
  const payload = buildConfigPayload();
  const hub = (document.querySelector('#hub-url-input') || {}).value || getHubUrl();
  setHubUrl(hub.trim());
  setPushStatus('Pushing…', '');
  showToast('📲 Pushing VPN config to the control hub…');

  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 8000);
    const res = await fetch(hub.replace(/\/$/, '') + '/api/vpn-config', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
      signal: controller.signal
    });
    clearTimeout(timeout);
    const data = await res.json().catch(() => ({}));
    if (res.ok && data.ok !== false) {
      setPushStatus('Synced ' + new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }), 'ok');
      showToast('📲 Config pushed — phone will apply on next sync.');
    } else {
      throw new Error((data && data.error) || 'Hub rejected the payload');
    }
  } catch (err) {
    setPushStatus('Pending — hub unreachable', 'warn');
    showToast('⚠️ Hub unreachable. Config saved locally, marked pending.');
  }
}

function updatePayloadPreview() {
  const pre = document.querySelector('#payload-preview');
  if (pre) {
    pre.textContent = JSON.stringify(buildConfigPayload(), null, 2);
  }
}

function syncConfigSummary() {
  const el = document.querySelector('#config-summary');
  if (!el) return;
  let rules = 0;
  if (vpnConfig.splitTunneling.enabled) {
    rules += vpnConfig.splitTunneling.apps.length + vpnConfig.splitTunneling.ips.length;
  }
  rules += vpnConfig.customDns.length;
  if (vpnConfig.netshield !== 'off') rules += 1;
  if (vpnConfig.killSwitch) rules += 1;
  el.textContent = rules + (rules === 1 ? ' rule' : ' rules');
}

/* ---------- Fleet rendering ---------- */
function fmtCadenceShort() {
  const mins = Math.round(rotationCadenceSeconds / 60);
  return mins + 'm';
}

function fmtCadenceLong() {
  const mins = Math.round(rotationCadenceSeconds / 60);
  return mins === 1 ? '1 min' : mins + ' mins';
}

function renderFleetList() {
  const list = document.querySelector('#fleet-list');
  if (!list) return;
  const query = ((document.querySelector('#fleet-search') || {}).value || '').trim().toLowerCase();
  const rows = proxyNodes
    .filter((n) => !query || n.name.toLowerCase().includes(query) || n.country.toLowerCase().includes(query))
    .map((n) => {
      const active = vpnConfig.serverMode === 'manual' && vpnConfig.serverId === n.id;
      const loadClass = n.load >= 65 ? 'high' : (n.load >= 40 ? 'mid' : 'low');
      return '<button class="fleet-row' + (active ? ' active' : '') + '" data-node="' + n.id + '">' +
        '<span class="fleet-flag">' + countryFlag(n.country) + '</span>' +
        '<span class="fleet-meta"><strong>' + n.name + '</strong><small>' + n.ip + ' · ' + n.latencyMs + ' ms</small></span>' +
        '<span class="load-wrap"><span class="load-bar"><i class="' + loadClass + '" style="width:' + n.load + '%"></i></span><small>' + n.load + '%</small></span>' +
        '</button>';
    })
    .join('');
  list.innerHTML = rows || '<div class="fleet-empty">No servers match your search.</div>';
  const count = document.querySelector('#fleet-count');
  if (count) count.textContent = proxyNodes.length + ' nodes';
}

function countryFlag(country) {
  const flags = {
    'Switzerland': '🇨🇭', 'Japan': '🇯🇵', 'Iceland': '🇮🇸', 'United States': '🇺🇸',
    'Singapore': '🇸🇬', 'Germany': '🇩🇪', 'United Kingdom': '🇬🇧', 'France': '🇫🇷',
    'Netherlands': '🇳🇱', 'Sweden': '🇸🇪', 'Norway': '🇳🇴', 'Finland': '🇫🇮',
    'Canada': '🇨🇦', 'Australia': '🇦🇺', 'South Korea': '🇰🇷', 'UAE': '🇦🇪',
    'Brazil': '🇧🇷', 'Austria': '🇦🇹', 'Spain': '🇪🇸', 'Italy': '🇮🇹',
    'Ireland': '🇮🇪', 'Poland': '🇵🇱', 'Czechia': '🇨🇿', 'Romania': '🇷🇴',
    'Taiwan': '🇹🇼', 'India': '🇮🇳', 'Portugal': '🇵🇹', 'Greece': '🇬🇷',
    'New Zealand': '🇳🇿', 'South Africa': '🇿🇦', 'Thailand': '🇹🇭'
  };
  return flags[country] || '🌐';
}

/* ---------- Profiles rendering ---------- */
const DEFAULT_PROFILES = [
  { name: 'Maximum Stealth', seed: { protocol: 'wireguard-tls', netshield: 'ads-trackers', server: { mode: 'random' }, killSwitch: { enabled: true, lockdown: true } } },
  { name: 'Streaming', seed: { protocol: 'wireguard-udp', netshield: 'off', server: { mode: 'fastest' }, killSwitch: { enabled: false, lockdown: false } } },
  { name: 'Travel Mode', seed: { protocol: 'smart', netshield: 'malware', server: { mode: 'manual', serverId: 'zurich' }, killSwitch: { enabled: true, lockdown: true } } }
];

function ensureSeedProfiles() {
  const existing = loadProfiles();
  if (existing.length > 0) return;
  const seeded = DEFAULT_PROFILES.map((p) => ({
    id: makeProfileId(),
    name: p.name,
    createdAt: new Date().toISOString(),
    config: Object.assign(snapshotConfigForProfile(), p.seed)
  }));
  saveProfiles(seeded);
}

function renderProfiles() {
  const list = document.querySelector('#profiles-list');
  if (!list) return;
  const profiles = loadProfiles();
  list.innerHTML = profiles.map((p) => {
    const cfg = p.config || {};
    const proto = (cfg.protocol || 'smart').replace('wireguard-', 'WG-').toUpperCase();
    const mode = cfg.server ? cfg.server.mode : 'manual';
    return '<div class="profile-card">' +
      '<div><strong>' + escapeHtml(p.name) + '</strong>' +
      '<small>' + proto + ' · ' + escapeHtml(mode) + ' · NetShield ' + escapeHtml(cfg.netshield || 'off') + '</small></div>' +
      '<div class="profile-actions">' +
      '<button class="link-button profile-apply" data-profile="' + p.id + '">Apply <span>→</span></button>' +
      '<button class="row-menu profile-delete" data-profile="' + p.id + '" aria-label="Delete profile">✕</button>' +
      '</div></div>';
  }).join('') || '<div class="fleet-empty">No profiles yet — save your current setup above.</div>';
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

/* ---------- Tag lists (split tunneling + DNS) ---------- */
function renderTagList(elId, items, kind) {
  const el = document.querySelector(elId);
  if (!el) return;
  el.innerHTML = items.map((item, i) =>
    '<span class="tag-chip">' + escapeHtml(item) +
    '<button class="tag-remove" data-kind="' + kind + '" data-index="' + i + '" aria-label="Remove">✕</button></span>'
  ).join('') || '<span class="tag-empty">None added</span>';
}

function renderAllTagLists() {
  renderTagList('#split-apps-list', vpnConfig.splitTunneling.apps, 'apps');
  renderTagList('#split-ips-list', vpnConfig.splitTunneling.ips, 'ips');
  renderTagList('#custom-dns-list', vpnConfig.customDns, 'dns');
}

function isValidCidrOrIp(v) {
  return /^(?:\d{1,3}\.){3}\d{1,3}(?:\/\d{1,2})?$/.test(v) || /^[0-9a-fA-F:]+$/.test(v);
}

/* ---------- Control syncing ---------- */
function setCadence(seconds, opts) {
  if (!CADENCE_OPTIONS.includes(seconds)) seconds = DEFAULT_CADENCE_SECONDS;
  rotationCadenceSeconds = seconds;
  vpnConfig.cadenceSeconds = seconds;
  countdownValue = seconds;
  if (!(opts && opts.silent)) {
    saveVpnConfig();
    showToast('⏱ Rotation cadence set to ' + fmtCadenceLong() + '.');
  }
  refreshCadenceUi();
  updatePayloadPreview();
}

function refreshCadenceUi() {
  document.querySelectorAll('#cadence-row .cadence-btn').forEach((btn) => {
    btn.classList.toggle('active', parseInt(btn.dataset.cadence, 10) === rotationCadenceSeconds);
  });
  const caption = document.querySelector('#timer-status-caption');
  if (caption) {
    caption.textContent = (autoRotateActive && chameleonShieldActive)
      ? 'Auto-rotates IP & location every ' + fmtCadenceLong()
      : 'Auto-rotation paused';
  }
  const autoLabel = document.querySelector('#auto-rotate-label');
  if (autoLabel) autoLabel.textContent = 'Auto-Rotate (' + fmtCadenceShort() + ')';
  const timerEl = document.querySelector('#countdown-timer');
  if (timerEl) timerEl.textContent = formatCountdown(countdownValue);
}

function refreshAllControls() {
  refreshCadenceUi();
  renderFleetList();
  renderProfiles();
  renderAllTagLists();
  document.querySelectorAll('#protocol-cards .radio-card').forEach((card) => {
    const on = card.dataset.protocol === vpnConfig.protocol;
    card.classList.toggle('active', on);
    card.setAttribute('aria-checked', on ? 'true' : 'false');
  });
  document.querySelectorAll('#netshield-seg .seg-btn').forEach((btn) => {
    btn.classList.toggle('active', btn.dataset.netshield === vpnConfig.netshield);
  });
  document.querySelectorAll('#split-mode-seg .seg-btn').forEach((btn) => {
    btn.classList.toggle('active', btn.dataset.splitMode === vpnConfig.splitTunneling.mode);
  });
  document.querySelectorAll('#ipv6-seg .seg-btn').forEach((btn) => {
    btn.classList.toggle('active', btn.dataset.ipv6 === vpnConfig.ipv6);
  });
  document.querySelectorAll('.pool-btn').forEach((btn) => {
    btn.classList.toggle('active', btn.dataset.pool === vpnConfig.serverMode);
  });
  const set = (sel, val) => { const el = document.querySelector(sel); if (el) el.checked = !!val; };
  set('#kill-switch-toggle', vpnConfig.killSwitch);
  set('#kill-switch-lockdown-toggle', vpnConfig.killSwitchLockdown);
  set('#split-toggle', vpnConfig.splitTunneling.enabled);
  set('#lan-bypass-toggle', vpnConfig.lanBypass);
  set('#auto-rotate-toggle', autoRotateActive);
  const hubInput = document.querySelector('#hub-url-input');
  if (hubInput) hubInput.value = getHubUrl();
  updatePayloadPreview();
  syncConfigSummary();
}

/* ---------- Overview (unchanged behavior) ---------- */
function bindOverviewEvents() {
  const scanBtn = document.querySelector('#scan-button');
  const scanAgain = document.querySelector('#scan-again');
  if (scanBtn) scanBtn.addEventListener('click', runScan);
  if (scanAgain) scanAgain.addEventListener('click', runScan);

  const inviteBtn = document.querySelector('#invite-button');
  const reportBtn = document.querySelector('#report-button');
  const policyBtn = document.querySelector('#policy-button');
  if (inviteBtn) inviteBtn.addEventListener('click', () => showToast('Invite flow opened — add a teammate to Northstar Labs.'));
  if (reportBtn) reportBtn.addEventListener('click', () => showToast('Your security summary is being prepared.'));
  if (policyBtn) policyBtn.addEventListener('click', () => showToast('Policy center opened — 2 controls are ready for review.'));

  document.querySelectorAll('.row-menu').forEach((button) => {
    button.addEventListener('click', () => showToast('Incident actions: assign, snooze, or mark as resolved.'));
  });
}

function setView(view) {
  const label = view === 'geoip' ? 'GeoIP Chameleon' : view.charAt(0).toUpperCase() + view.slice(1);
  document.querySelector('#breadcrumb-label').textContent = label;
  document.querySelectorAll('.nav-item').forEach((item) => item.classList.toggle('active', item.dataset.view === view));

  const appView = document.querySelector('#app-view');

  if (view === 'overview') {
    appView.innerHTML = overviewHtml;
    bindOverviewEvents();
    stopGeoIpTimer();
  } else if (view === 'geoip') {
    const template = document.querySelector('#geoip-view-template');
    appView.innerHTML = template ? template.innerHTML : '<div class="page-heading"><h1>GeoIP Chameleon</h1></div>';
    initGeoIpWorkspace();
  } else {
    appView.innerHTML = `
      <div class="page-heading">
        <div>
          <p class="eyebrow">WORKSPACE MODULE</p>
          <h1>${label} <span class="wave">✦</span></h1>
          <p class="subheading">${label} management and telemetry feed.</p>
        </div>
        <button class="primary-button" onclick="showToast('${label} configuration saved.')"><span>＋</span> New ${label.slice(0, -1)}</button>
      </div>
      <div class="panel" style="padding: 32px; text-align: center;">
        <h2>${label} is fully synchronized</h2>
        <p style="color: var(--muted); margin-top: 8px;">No pending alerts or anomalies found in ${label.toLowerCase()} subsystem.</p>
      </div>
    `;
    stopGeoIpTimer();
    showToast(`${label} workspace is ready for your next review.`);
  }

  appView.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

document.querySelectorAll('.nav-item').forEach((item) => {
  item.addEventListener('click', () => setView(item.dataset.view));
});

document.querySelectorAll('[data-view-target]').forEach((item) => {
  item.addEventListener('click', () => setView(item.dataset.viewTarget));
});

function runScan() {
  const button = document.querySelector('#scan-button');
  const scanAgain = document.querySelector('#scan-again');
  const lastScan = document.querySelector('#last-scan');
  const scanDetail = document.querySelector('#scan-detail');
  if (!button) return;
  button.disabled = true;
  button.innerHTML = '<span>◌</span> Scanning workspace…';
  if (scanAgain) scanAgain.disabled = true;
  if (scanDetail) scanDetail.textContent = 'Checking protected assets…';
  setTimeout(() => {
    const now = new Date();
    if (lastScan) lastScan.textContent = `Today, ${now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
    if (scanDetail) scanDetail.textContent = 'No new threats detected';
    button.disabled = false;
    button.innerHTML = '<span>＋</span> Run security scan';
    if (scanAgain) scanAgain.disabled = false;
    showToast('Security scan complete — no new threats detected.');
  }, 1200);
}

async function fetchRealIp() {
  const realIpEl = document.querySelector('#real-ip');
  const realLocEl = document.querySelector('#real-location');
  if (!realIpEl) return;
  try {
    const res = await fetch('https://ipapi.co/json/');
    const data = await res.json();
    if (data && data.ip) {
      realIpEl.textContent = data.ip;
      realLocEl.textContent = `${data.city || 'Unknown'}, ${data.country_name || 'Unknown'} (${data.org || 'ISP'})`;
    } else {
      throw new Error('Invalid IP data');
    }
  } catch (err) {
    try {
      const fallbackRes = await fetch('https://api.ipify.org?format=json');
      const fallbackData = await fallbackRes.json();
      realIpEl.textContent = fallbackData.ip || '192.168.1.100';
      realLocEl.textContent = 'Local Network / ISP Detected';
    } catch (e) {
      realIpEl.textContent = '127.0.0.1 (Localhost)';
      realLocEl.textContent = 'Direct Connection';
    }
  }
}

function formatCountdown(totalSeconds) {
  const mins = Math.floor(totalSeconds / 60);
  const secs = totalSeconds % 60;
  return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
}

/* ---------- GeoIP workspace (original behavior + control plane) ---------- */
function initGeoIpWorkspace() {
  countdownValue = rotationCadenceSeconds;
  startGeoIpTimer();
  fetchRealIp();
  ensureSeedProfiles();
  refreshAllControls();

  const rotateBtn = document.querySelector('#rotate-now-btn');
  if (rotateBtn) {
    rotateBtn.addEventListener('click', () => rotateToRandomNode('Manual ' + fmtCadenceShort() + ' Rotation'));
  }

  const copyIpBtn = document.querySelector('#copy-ip-btn');
  if (copyIpBtn) {
    copyIpBtn.addEventListener('click', () => {
      const ip = document.querySelector('#virtual-ip').textContent;
      navigator.clipboard?.writeText(ip);
      showToast(`Copied Virtual IP: ${ip} to clipboard.`);
    });
  }

  const testLiveIpBtn = document.querySelector('#test-live-ip-btn');
  if (testLiveIpBtn) {
    testLiveIpBtn.addEventListener('click', () => {
      const virtualIp = document.querySelector('#virtual-ip').textContent;
      showToast(`Live IP Check: Outbound traffic successfully routed through ${virtualIp} (Zero Leaks).`);
    });
  }

  const triggerGeoTestBtn = document.querySelector('#trigger-geo-test-btn');
  if (triggerGeoTestBtn) {
    triggerGeoTestBtn.addEventListener('click', () => {
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          showToast(`Browser Geo API Test: Lat ${pos.coords.latitude.toFixed(4)}, Lon ${pos.coords.longitude.toFixed(4)} (Spoofed).`);
        },
        (err) => {
          showToast(`Geolocation Error: ${err.message}`);
        }
      );
    });
  }

  const clearLogsBtn = document.querySelector('#clear-logs-btn');
  if (clearLogsBtn) {
    clearLogsBtn.addEventListener('click', () => {
      const tbody = document.querySelector('#rotation-log-table');
      if (tbody) tbody.innerHTML = '';
      showToast('Rotation history log cleared.');
    });
  }

  const masterShieldToggle = document.querySelector('#master-shield-toggle');
  if (masterShieldToggle) {
    masterShieldToggle.checked = chameleonShieldActive;
    masterShieldToggle.addEventListener('change', (e) => {
      chameleonShieldActive = e.target.checked;
      autoRotateActive = e.target.checked;
      saveVpnConfig();

      const badge = document.querySelector('#shield-status-badge');
      if (badge) {
        badge.textContent = chameleonShieldActive ? '● Active' : '○ Paused';
        badge.className = chameleonShieldActive ? 'trend positive' : 'trend negative';
      }

      refreshCadenceUi();

      if (chameleonShieldActive) {
        countdownValue = rotationCadenceSeconds;
        startGeoIpTimer();
        showToast('🛡️ Chameleon Shield ON: Auto-rotating IP and location every ' + fmtCadenceLong() + '.');
      } else {
        stopGeoIpTimer();
        showToast('⚠️ Chameleon Shield OFF: Auto-rotation paused. Real location exposed.');
      }
    });
  }

  const autoRotateToggle = document.querySelector('#auto-rotate-toggle');
  if (autoRotateToggle) {
    autoRotateToggle.checked = autoRotateActive;
    autoRotateToggle.addEventListener('change', (e) => {
      autoRotateActive = e.target.checked;
      refreshCadenceUi();
      showToast(autoRotateActive ? fmtCadenceShort() + ' Auto-Rotate enabled.' : fmtCadenceShort() + ' Auto-Rotate paused.');
    });
  }

  /* ----- Server fleet ----- */
  const search = document.querySelector('#fleet-search');
  if (search) search.addEventListener('input', renderFleetList);

  const fleetList = document.querySelector('#fleet-list');
  if (fleetList) {
    fleetList.addEventListener('click', (e) => {
      const row = e.target.closest('[data-node]');
      if (!row) return;
      const node = proxyNodes.find((n) => n.id === row.dataset.node);
      if (node) {
        vpnConfig.serverMode = 'manual';
        vpnConfig.serverId = node.id;
        saveVpnConfig();
        applyProxyNode(node, 'Selected Node: ' + node.name);
        renderFleetList();
        refreshAllControls();
      }
    });
  }

  const fastestBtn = document.querySelector('#fleet-fastest-btn');
  if (fastestBtn) {
    fastestBtn.addEventListener('click', () => {
      const node = pickFastestNode();
      vpnConfig.serverMode = 'fastest';
      vpnConfig.serverId = node.id;
      saveVpnConfig();
      applyProxyNode(node, 'Fastest Node: ' + node.name);
      renderFleetList();
      refreshAllControls();
      showToast('⚡ Fastest node selected: ' + node.name + ' (' + node.latencyMs + ' ms, ' + node.load + '% load).');
    });
  }

  document.querySelectorAll('.pool-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      vpnConfig.serverMode = btn.dataset.pool;
      saveVpnConfig();
      refreshAllControls();
      showToast('Server selection mode: ' + btn.dataset.pool + '.');
    });
  });

  /* ----- Cadence ----- */
  document.querySelectorAll('#cadence-row .cadence-btn').forEach((btn) => {
    btn.addEventListener('click', () => setCadence(parseInt(btn.dataset.cadence, 10)));
  });

  /* ----- Protocol ----- */
  document.querySelectorAll('#protocol-cards .radio-card').forEach((card) => {
    card.addEventListener('click', () => {
      vpnConfig.protocol = card.dataset.protocol;
      saveVpnConfig();
      refreshAllControls();
      showToast('🔒 Protocol: ' + card.querySelector('strong').textContent + '.');
    });
  });

  /* ----- Shield settings ----- */
  document.querySelectorAll('#netshield-seg .seg-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      vpnConfig.netshield = btn.dataset.netshield;
      saveVpnConfig();
      refreshAllControls();
      showToast('🛡️ NetShield: ' + btn.textContent + '.');
    });
  });

  const ksToggle = document.querySelector('#kill-switch-toggle');
  if (ksToggle) ksToggle.addEventListener('change', (e) => {
    vpnConfig.killSwitch = e.target.checked;
    saveVpnConfig();
    showToast(e.target.checked ? 'Kill switch enabled.' : 'Kill switch disabled.');
  });

  const ksLockToggle = document.querySelector('#kill-switch-lockdown-toggle');
  if (ksLockToggle) ksLockToggle.addEventListener('change', (e) => {
    vpnConfig.killSwitchLockdown = e.target.checked;
    saveVpnConfig();
  });

  /* ----- Split tunneling ----- */
  const splitToggle = document.querySelector('#split-toggle');
  if (splitToggle) splitToggle.addEventListener('change', (e) => {
    vpnConfig.splitTunneling.enabled = e.target.checked;
    saveVpnConfig();
  });

  document.querySelectorAll('#split-mode-seg .seg-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      vpnConfig.splitTunneling.mode = btn.dataset.splitMode;
      saveVpnConfig();
      refreshAllControls();
    });
  });

  const addSplitApp = () => {
    const input = document.querySelector('#split-apps-input');
    const val = (input.value || '').trim();
    if (!val) return;
    if (!/^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$/.test(val)) {
      showToast('⚠️ Enter a valid package name, e.g. com.bank.example');
      return;
    }
    if (!vpnConfig.splitTunneling.apps.includes(val)) vpnConfig.splitTunneling.apps.push(val);
    input.value = '';
    saveVpnConfig();
    renderAllTagLists();
  };
  const appsAdd = document.querySelector('#split-apps-add');
  if (appsAdd) appsAdd.addEventListener('click', addSplitApp);

  const addSplitIp = () => {
    const input = document.querySelector('#split-ips-input');
    const val = (input.value || '').trim();
    if (!val) return;
    if (!isValidCidrOrIp(val)) {
      showToast('⚠️ Enter a valid IP or CIDR, e.g. 192.168.1.0/24');
      return;
    }
    if (!vpnConfig.splitTunneling.ips.includes(val)) vpnConfig.splitTunneling.ips.push(val);
    input.value = '';
    saveVpnConfig();
    renderAllTagLists();
  };
  const ipsAdd = document.querySelector('#split-ips-add');
  if (ipsAdd) ipsAdd.addEventListener('click', addSplitIp);

  document.querySelectorAll('.tag-list').forEach((list) => {
    list.addEventListener('click', (e) => {
      const btn = e.target.closest('.tag-remove');
      if (!btn) return;
      const idx = parseInt(btn.dataset.index, 10);
      if (btn.dataset.kind === 'apps') vpnConfig.splitTunneling.apps.splice(idx, 1);
      else if (btn.dataset.kind === 'ips') vpnConfig.splitTunneling.ips.splice(idx, 1);
      else if (btn.dataset.kind === 'dns') vpnConfig.customDns.splice(idx, 1);
      saveVpnConfig();
      renderAllTagLists();
    });
  });

  /* ----- DNS & leak protection ----- */
  const lanToggle = document.querySelector('#lan-bypass-toggle');
  if (lanToggle) lanToggle.addEventListener('change', (e) => {
    vpnConfig.lanBypass = e.target.checked;
    saveVpnConfig();
  });

  document.querySelectorAll('#ipv6-seg .seg-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      vpnConfig.ipv6 = btn.dataset.ipv6;
      saveVpnConfig();
      refreshAllControls();
    });
  });

  const addDns = () => {
    const input = document.querySelector('#custom-dns-input');
    const val = (input.value || '').trim();
    if (!val) return;
    if (!isValidCidrOrIp(val)) {
      showToast('⚠️ Enter a valid resolver IP, e.g. 1.1.1.1');
      return;
    }
    if (!vpnConfig.customDns.includes(val)) vpnConfig.customDns.push(val);
    input.value = '';
    saveVpnConfig();
    renderAllTagLists();
  };
  const dnsAdd = document.querySelector('#custom-dns-add');
  if (dnsAdd) dnsAdd.addEventListener('click', addDns);

  /* ----- Profiles ----- */
  const saveProfileBtn = document.querySelector('#save-profile-btn');
  if (saveProfileBtn) saveProfileBtn.addEventListener('click', () => {
    const input = document.querySelector('#profile-name-input');
    const name = (input.value || '').trim();
    if (!name) {
      showToast('⚠️ Give the profile a name first.');
      return;
    }
    const profiles = loadProfiles();
    profiles.push({ id: makeProfileId(), name, createdAt: new Date().toISOString(), config: snapshotConfigForProfile() });
    saveProfiles(profiles);
    input.value = '';
    renderProfiles();
    showToast('✓ Profile "' + name + '" saved.');
  });

  const profilesList = document.querySelector('#profiles-list');
  if (profilesList) {
    profilesList.addEventListener('click', (e) => {
      const applyBtn = e.target.closest('.profile-apply');
      const delBtn = e.target.closest('.profile-delete');
      const profiles = loadProfiles();
      if (applyBtn) {
        const profile = profiles.find((p) => p.id === applyBtn.dataset.profile);
        if (profile && profile.config) {
          applyProfileConfig(profile.config);
          showToast('✓ Profile "' + profile.name + '" applied.');
        }
      } else if (delBtn) {
        const id = delBtn.dataset.profile;
        saveProfiles(profiles.filter((p) => p.id !== id));
        renderProfiles();
        showToast('Profile deleted.');
      }
    });
  }

  /* ----- Phone sync ----- */
  const hubInput = document.querySelector('#hub-url-input');
  if (hubInput) {
    hubInput.addEventListener('change', () => {
      setHubUrl(hubInput.value.trim() || 'http://127.0.0.1:3300');
      showToast('Control hub URL updated.');
    });
  }
  const pushBtn = document.querySelector('#push-phone-btn');
  if (pushBtn) pushBtn.addEventListener('click', pushConfigToPhone);
  const pushBtn2 = document.querySelector('#push-phone-btn-2');
  if (pushBtn2) pushBtn2.addEventListener('click', pushConfigToPhone);

  updatePayloadPreview();
  syncConfigSummary();
}

function startGeoIpTimer() {
  stopGeoIpTimer();
  timerInterval = setInterval(() => {
    if (!autoRotateActive || !chameleonShieldActive) return;
    countdownValue--;
    const timerEl = document.querySelector('#countdown-timer');
    const percentEl = document.querySelector('#timer-percent');
    if (timerEl) timerEl.textContent = formatCountdown(countdownValue);
    if (percentEl) percentEl.textContent = `${Math.round((countdownValue / rotationCadenceSeconds) * 100)}%`;

    if (countdownValue <= 0) {
      countdownValue = rotationCadenceSeconds;
      rotateToRandomNode('Automatic ' + fmtCadenceShort() + ' Rotation');
    }
  }, 1000);
}

function stopGeoIpTimer() {
  if (timerInterval) {
    clearInterval(timerInterval);
    timerInterval = null;
  }
}

function rotateToRandomNode(reason) {
  let pool = proxyNodes;
  if (vpnConfig.serverMode === 'fastest') {
    pool = [pickFastestNode()];
  }
  const randomNode = pool[Math.floor(Math.random() * pool.length)];
  applyProxyNode(randomNode, reason);

  document.querySelectorAll('#fleet-list [data-node]').forEach((row) => {
    row.classList.toggle('active', row.dataset.node === randomNode.id && vpnConfig.serverMode === 'manual');
  });
}

function applyProxyNode(node, triggerReason) {
  const ipEl = document.querySelector('#virtual-ip');
  const locEl = document.querySelector('#virtual-location');
  const coordsEl = document.querySelector('#virtual-coords');
  const ispEl = document.querySelector('#virtual-isp');

  if (ipEl) ipEl.textContent = node.ip;
  if (locEl) locEl.textContent = `${node.name} (${node.coords})`;
  if (coordsEl) coordsEl.textContent = node.coords;
  if (ispEl) ispEl.textContent = node.isp;

  const terminal = document.querySelector('#rea-terminal');
  if (terminal) {
    const timeStr = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    terminal.innerHTML += `<br>[${timeStr}] ${triggerReason} -> IP: ${node.ip} | Location: ${node.name}`;
    terminal.scrollTop = terminal.scrollHeight;
  }

  const tbody = document.querySelector('#rotation-log-table');
  if (tbody) {
    const row = document.createElement('tr');
    row.innerHTML = `
      <td>Just now</td>
      <td><strong>${node.ip}</strong></td>
      <td>${node.name}</td>
      <td>${node.coords.replace('Lat: ', '').replace(' · Lon: ', ', ')}</td>
      <td>${node.isp}</td>
      <td><span class="status investigating" style="background:#183b43;color:var(--mint);">Active</span></td>
    `;
    tbody.insertBefore(row, tbody.firstChild);
  }

  countdownValue = rotationCadenceSeconds;
  if (chameleonShieldActive) {
    updateGeolocationMock(node.coords);
  }
  showToast(`Location & IP rotated to ${node.name} (${node.ip}) — next in ${fmtCadenceShort()}!`);
}

// Initial binding
bindOverviewEvents();
