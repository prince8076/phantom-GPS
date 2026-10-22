const express = require('express');
const http = require('http');
const path = require('path');
const os = require('os');
const { exec } = require('child_process');

const torController = require('./tor-controller');
const cdpController = require('./cdp-controller');
const smartProxy = require('./smart-proxy');
const privacyFortress = require('./privacy-fortress');
const routeEngine = require('./route-engine');

const UI_PORT = 3300;
const PROXY_PORT = 8899;

// Comprehensive 30+ Global Jurisdiction Pool with Tor Country Codes & Lat/Lon
const PROXY_NODES = [
  { id: 'zurich', country: 'Switzerland', countryCode: 'ch', city: 'Zurich', flag: '🇨🇭', lat: 47.3769, lon: 8.5417, virtualIp: '185.220.101.98', isp: 'Swiss Privacy IX' },
  { id: 'tokyo', country: 'Japan', countryCode: 'jp', city: 'Tokyo', flag: '🇯🇵', lat: 35.6762, lon: 139.6503, virtualIp: '103.253.140.9', isp: 'Tokyo Datacenter Tunnel' },
  { id: 'reykjavik', country: 'Iceland', countryCode: 'is', city: 'Reykjavik', flag: '🇮🇸', lat: 64.1466, lon: -21.9426, virtualIp: '185.159.157.2', isp: 'Arctic Privacy Enclave' },
  { id: 'newyork', country: 'United States', countryCode: 'us', city: 'New York', flag: '🇺🇸', lat: 40.7128, lon: -74.0060, virtualIp: '198.51.100.42', isp: 'US East Core IX' },
  { id: 'singapore', country: 'Singapore', countryCode: 'sg', city: 'Singapore', flag: '🇸🇬', lat: 1.3521, lon: 103.8198, virtualIp: '165.22.240.18', isp: 'Equinix SG1 Tier 1' },
  { id: 'berlin', country: 'Germany', countryCode: 'de', city: 'Berlin', flag: '🇩🇪', lat: 52.5200, lon: 13.4050, virtualIp: '89.187.160.73', isp: 'Digitalcourage e.V IX' },
  { id: 'london', country: 'United Kingdom', countryCode: 'gb', city: 'London', flag: '🇬🇧', lat: 51.5074, lon: -0.1278, virtualIp: '178.62.193.18', isp: 'LINX London Exchange' },
  { id: 'paris', country: 'France', countryCode: 'fr', city: 'Paris', flag: '🇫🇷', lat: 48.8566, lon: 2.3522, virtualIp: '163.172.67.12', isp: 'France-IX Paris' },
  { id: 'amsterdam', country: 'Netherlands', countryCode: 'nl', city: 'Amsterdam', flag: '🇳🇱', lat: 52.3676, lon: 4.9041, virtualIp: '185.220.100.244', isp: 'AMS-IX Backbone' },
  { id: 'stockholm', country: 'Sweden', countryCode: 'se', city: 'Stockholm', flag: '🇸🇪', lat: 59.3293, lon: 18.0686, virtualIp: '193.180.117.15', isp: 'Netnod Stockholm' },
  { id: 'oslo', country: 'Norway', countryCode: 'no', city: 'Oslo', flag: '🇳🇴', lat: 59.9139, lon: 10.7522, virtualIp: '185.107.56.20', isp: 'NIX Oslo Core' },
  { id: 'helsinki', country: 'Finland', countryCode: 'fi', city: 'Helsinki', flag: '🇫🇮', lat: 60.1699, lon: 24.9384, virtualIp: '95.216.14.88', isp: 'FICIX Helsinki' },
  { id: 'toronto', country: 'Canada', countryCode: 'ca', city: 'Toronto', flag: '🇨🇦', lat: 43.6532, lon: -79.3832, virtualIp: '199.195.250.77', isp: 'TorIX Canada' },
  { id: 'sydney', country: 'Australia', countryCode: 'au', city: 'Sydney', flag: '🇦🇺', lat: -33.8688, lon: 151.2093, virtualIp: '139.99.144.11', isp: 'NSW IX Australia' },
  { id: 'seoul', country: 'South Korea', countryCode: 'kr', city: 'Seoul', flag: '🇰🇷', lat: 37.5665, lon: 126.9780, virtualIp: '118.235.4.19', isp: 'KINX Seoul Tier 1' },
  { id: 'dubai', country: 'United Arab Emirates', countryCode: 'ae', city: 'Dubai', flag: '🇦🇪', lat: 25.2048, lon: 55.2708, virtualIp: '185.165.168.10', isp: 'UAE-IX Dubai' },
  { id: 'saopaulo', country: 'Brazil', countryCode: 'br', city: 'São Paulo', flag: '🇧🇷', lat: -23.5505, lon: -46.6333, virtualIp: '177.105.34.8', isp: 'IX.br São Paulo' },
  { id: 'vienna', country: 'Austria', countryCode: 'at', city: 'Vienna', flag: '🇦🇹', lat: 48.2082, lon: 16.3738, virtualIp: '193.170.0.12', isp: 'VIX Vienna Core' },
  { id: 'madrid', country: 'Spain', countryCode: 'es', city: 'Madrid', flag: '🇪🇸', lat: 40.4168, lon: -3.7038, virtualIp: '82.223.11.90', isp: 'ESPANIX Madrid' },
  { id: 'rome', country: 'Italy', countryCode: 'it', city: 'Rome', flag: '🇮🇹', lat: 41.9028, lon: 12.4964, virtualIp: '185.220.103.5', isp: 'Namex Roma IX' },
  { id: 'dublin', country: 'Ireland', countryCode: 'ie', city: 'Dublin', flag: '🇮🇪', lat: 53.3498, lon: -6.2603, virtualIp: '54.72.19.102', isp: 'INEX Dublin' },
  { id: 'warsaw', country: 'Poland', countryCode: 'pl', city: 'Warsaw', flag: '🇵🇱', lat: 52.2297, lon: 21.0122, virtualIp: '178.217.170.5', isp: 'PLIX Warsaw' },
  { id: 'prague', country: 'Czech Republic', countryCode: 'cz', city: 'Prague', flag: '🇨🇿', lat: 50.0755, lon: 14.4378, virtualIp: '91.219.236.19', isp: 'NIX.CZ Prague' },
  { id: 'bucharest', country: 'Romania', countryCode: 'ro', city: 'Bucharest', flag: '🇷🇴', lat: 44.4268, lon: 26.1025, virtualIp: '185.220.101.4', isp: 'InterLAN Bucharest' },
  { id: 'taipei', country: 'Taiwan', countryCode: 'tw', city: 'Taipei', flag: '🇹🇼', lat: 25.0330, lon: 121.5654, virtualIp: '211.75.1.20', isp: 'TPIX Taipei' },
  { id: 'mumbai', country: 'India', countryCode: 'in', city: 'Mumbai', flag: '🇮🇳', lat: 19.0760, lon: 72.8777, virtualIp: '103.21.244.0', isp: 'NIXI Mumbai IX' },
  { id: 'losangeles', country: 'United States', countryCode: 'us', city: 'Los Angeles', flag: '🇺🇸', lat: 34.0522, lon: -118.2437, virtualIp: '198.51.100.88', isp: 'LAIIX West Core' },
  { id: 'frankfurt', country: 'Germany', countryCode: 'de', city: 'Frankfurt', flag: '🇩🇪', lat: 50.1109, lon: 8.6821, virtualIp: '185.220.101.55', isp: 'DE-CIX Frankfurt' }
];

let currentNodeIndex = 0;
let shieldActive = false;
let autoRotateActive = true;
let rotationIntervalSeconds = 300; // Custom user interval (default 300s)
let countdownSeconds = rotationIntervalSeconds;
let isRandomPoolMode = true; // Randomize across all global nodes instead of sequential 5
let rotationHistory = [];
let verifiedPublicIp = null;

// VPN config pushed from the web dashboard ("Push to phone").
// Consumed by the Android app via GET /api/vpn/config.
let vpnConfig = null;
let vpnConfigUpdatedAt = null;

// Get Real Wi-Fi / Local LAN IP for Mobile Access (ignoring Cloudflare WARP / virtual adapters)
function getLocalLanIp() {
  const interfaces = os.networkInterfaces();
  let wifiIp = null;
  let fallbackIp = null;

  for (const name of Object.keys(interfaces)) {
    // Skip virtual or loopback adapters
    if (/warp|virtual|vethernet|hyper-v|wsl|docker/i.test(name)) continue;

    for (const iface of interfaces[name]) {
      if (iface.family === 'IPv4' && !iface.internal) {
        if (/wi-fi|wlan|wireless|ethernet/i.test(name)) {
          return iface.address;
        }
        if (iface.address.startsWith('192.168.')) {
          wifiIp = iface.address;
        } else if (!fallbackIp && !iface.address.startsWith('172.16.')) {
          fallbackIp = iface.address;
        }
      }
    }
  }
  return wifiIp || fallbackIp || '192.168.1.72';
}

// Windows System Proxy Controls with NCSI & Local Whitelist
function setWindowsProxy(enabled, host = '127.0.0.1', port = PROXY_PORT) {
  return new Promise((resolve) => {
    if (process.platform !== 'win32') return resolve(false);

    const proxyServerStr = `127.0.0.1:${port}`;
    const bypassStr = '<local>;*.msftncsi.com;*.msftconnecttest.com;*.microsoft.com;*.windows.com;*.pki.goog;*.digicert.com;*.identrust.com;*.sectigo.com;localhost;127.0.0.1;192.168.*';

    const regCmd = enabled
      ? `reg add "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings" /v ProxyEnable /t REG_DWORD /d 1 /f && reg add "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings" /v ProxyServer /t REG_SZ /d "${proxyServerStr}" /f && reg add "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings" /v ProxyOverride /t REG_SZ /d "${bypassStr}" /f`
      : `reg add "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings" /v ProxyEnable /t REG_DWORD /d 0 /f`;

    exec(regCmd, (err) => {
      if (err) console.error('[Proxy Reg Error]:', err.message);
      resolve(!err);
    });
  });
}

// Worldwide Open-Source Cities Database (171,000+ cities across 246 countries)
const fs = require('fs');
let WORLD_CITIES = [];
const countryNames = new Intl.DisplayNames(['en'], { type: 'region' });

try {
  const citiesPath = path.join(__dirname, 'world-cities.json');
  if (fs.existsSync(citiesPath)) {
    const rawCities = JSON.parse(fs.readFileSync(citiesPath, 'utf8'));
    WORLD_CITIES = rawCities.filter(c => c.name && c.lat && c.lng && c.country);
    console.log(`[OmniRush] Loaded ${WORLD_CITIES.length} worldwide cities across 246 countries for limitless global roaming!`);
  }
} catch (e) {
  console.error('[OmniRush] Failed loading world cities database:', e.message);
}

function getCountryName(code) {
  try { return countryNames.of(code.toUpperCase()) || code; } catch(e) { return code; }
}

function getFlag(code) {
  try {
    return code.toUpperCase().replace(/./g, char => String.fromCodePoint(127397 + char.charCodeAt(0)));
  } catch(e) { return '📍'; }
}

// Generate random global node from 171,000+ cities
function getRandomGlobalNode() {
  if (WORLD_CITIES.length > 0) {
    const c = WORLD_CITIES[Math.floor(Math.random() * WORLD_CITIES.length)];
    const countryName = getCountryName(c.country);
    return {
      id: `global_${c.name.toLowerCase().replace(/[^a-z0-9]/g, '_')}_${Date.now()}`,
      country: countryName,
      countryCode: c.country.toLowerCase(),
      city: c.name,
      flag: getFlag(c.country),
      lat: parseFloat(c.lat),
      lon: parseFloat(c.lng),
      virtualIp: `1${Math.floor(Math.random() * 89 + 10)}.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 254 + 1)}`,
      isp: `${countryName} Global Roaming Tier 1`
    };
  }
  return PROXY_NODES[Math.floor(Math.random() * PROXY_NODES.length)];
}

// Current Active Node state (defaults to Zurich)
let activeCustomNode = null;

// Rotate to next node, specific index, or completely random global city
async function rotateNode(targetIdx = null, reason = 'Automated Rotation') {
  let node;
  if (targetIdx !== null && targetIdx >= 0 && targetIdx < PROXY_NODES.length) {
    currentNodeIndex = targetIdx;
    activeCustomNode = null;
    node = PROXY_NODES[currentNodeIndex];
  } else if (isRandomPoolMode) {
    // Pick any random city worldwide from 171,000+ cities across 246 countries
    node = getRandomGlobalNode();
    activeCustomNode = node;
  } else {
    activeCustomNode = null;
    currentNodeIndex = (currentNodeIndex + 1) % PROXY_NODES.length;
    node = PROXY_NODES[currentNodeIndex];
  }

  countdownSeconds = rotationIntervalSeconds;

  console.log(`[OmniRush] Rotated to: ${node.flag} ${node.city}, ${node.country} (${node.lat}, ${node.lon})`);

  // 1. Tell Tor to switch exit node country if supported
  if (node.countryCode) {
    torController.switchCountry(node.countryCode).catch(() => {});
  }

  // 2. Tell CDP to update browser geolocation
  cdpController.setBrowserGeolocation(node.lat, node.lon).then(res => {
    if (res.updatedCount > 0) {
      console.log(`[CDP] Overrode geolocation on ${res.updatedCount} Chrome tab(s) to ${node.city}`);
    }
  }).catch(() => {});

  const entry = {
    timestamp: new Date().toLocaleTimeString(),
    node: `${node.city}, ${node.country}`,
    flag: node.flag,
    ip: node.virtualIp,
    coords: `Lat: ${node.lat.toFixed(4)}, Lon: ${node.lon.toFixed(4)}`,
    isp: node.isp,
    reason
  };
  rotationHistory.unshift(entry);
  if (rotationHistory.length > 35) rotationHistory.pop();

  // In background, verify actual public IP through the tunnel
  setTimeout(async () => {
    const verified = await torController.fetchVerifiedIp();
    if (verified) {
      verifiedPublicIp = verified;
      entry.ip = verified;
      node.virtualIp = verified;
      console.log(`[OmniRush] Verified Real Outbound IP: ${verified}`);
    }
  }, 2000);
}

// Cadence Timer Loop
setInterval(() => {
  if (!shieldActive || !autoRotateActive) return;
  countdownSeconds--;
  if (countdownSeconds <= 0) {
    rotateNode(null, `Timed Rotation (${rotationIntervalSeconds}s)`);
  }
}, 1000);

// Active Connected Devices & Users Tracker
const connectedClients = new Map();

// Route engine updates push live position to browser & mobile
routeEngine.onUpdate((state) => {
  cdpController.setBrowserGeolocation(state.lat, state.lon).catch(() => {});
});

// Web Application Server
const app = express();
app.use((req, res, next) => {
  res.header('Access-Control-Allow-Origin', '*');
  res.header('Access-Control-Allow-Headers', '*');
  res.header('Access-Control-Allow-Methods', '*');

  // Track live real devices accessing the GPS location feed
  if (req.path === '/api/status' || req.path.startsWith('/api/route/')) {
    const cfConnectingIp = req.headers['cf-connecting-ip'];
    const xForwardedFor = req.headers['x-forwarded-for'];
    const clientIp = (cfConnectingIp || (xForwardedFor ? xForwardedFor.split(',')[0].trim() : null) || req.socket.remoteAddress || '').replace(/^::ffff:/, '');
    const rawUa = req.headers['user-agent'] || 'Unknown';

    // Ignore internal automated curl/PowerShell loops from local server machine
    const isLocalSelf = (clientIp === '127.0.0.1' || clientIp === '::1') && /powershell|curl/i.test(rawUa);

    if (!isLocalSelf && clientIp) {
      let deviceType = 'Desktop Web';
      let isApp = false;

      if (/PhantomGPS-Android|okhttp/i.test(rawUa)) {
        deviceType = 'Phantom GPS APK (Android)';
        isApp = true;
      } else if (/android/i.test(rawUa)) {
        deviceType = 'Android Mobile';
      } else if (/iphone|ipad|ipod/i.test(rawUa)) {
        deviceType = 'iPhone / iOS';
      } else if (/mobile/i.test(rawUa)) {
        deviceType = 'Mobile Browser';
      }

      // Key by client IP and device type so each real device counts as 1 user
      const clientKey = `${clientIp}_${isApp ? 'apk' : 'web'}`;
      connectedClients.set(clientKey, {
        ip: clientIp,
        deviceType,
        isApp,
        userAgent: rawUa,
        path: req.path,
        lastSeen: Date.now(),
        lastSeenFormatted: new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
      });
    }
  }

  // Prune clients inactive for > 15 seconds (heartbeats are every 1s-2s)
  const now = Date.now();
  for (const [key, client] of connectedClients.entries()) {
    if (now - client.lastSeen > 15000) {
      connectedClients.delete(key);
    }
  }

  next();
});
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// Instant Direct APK Download Routes (Handles /OmniRush-GPS, /download, /phantom.apk, etc.)
const apkFilePath = path.join(__dirname, 'public', 'OmniRush-GPS.apk');
const handleApkDownload = (req, res) => {
  res.setHeader('Content-Type', 'application/vnd.android.package-archive');
  res.setHeader('Content-Disposition', 'attachment; filename="Phantom-GPS.apk"');
  res.download(apkFilePath, 'Phantom-GPS.apk', (err) => {
    if (err && !res.headersSent) {
      res.status(404).send('APK file not found. Rebuilding...');
    }
  });
};

app.get('/download', handleApkDownload);
app.get('/apk', handleApkDownload);
app.get('/OmniRush-GPS', handleApkDownload);
app.get('/OmniRush-GPS.apk', handleApkDownload);
app.get('/phantom.apk', handleApkDownload);

app.get('/api/status', async (req, res) => {
  const lanIp = getLocalLanIp();
  const cdpTargets = await cdpController.getTargets();
  const chromeConnected = cdpTargets.some(t => t.type === 'page');
  const activeRoute = routeEngine.getStatus();

  const activeUsers = Array.from(connectedClients.values()).sort((a, b) => b.lastSeen - a.lastSeen);

  res.json({
    shieldActive,
    autoRotateActive,
    countdownSeconds,
    rotationIntervalSeconds,
    isRandomPoolMode,
    currentNode: activeCustomNode || PROXY_NODES[currentNodeIndex],
    allNodes: PROXY_NODES,
    activeRoute,
    rotationHistory,
    verifiedPublicIp,
    proxyPort: PROXY_PORT,
    lanIp,
    mobileUrl: `http://${lanIp}:${UI_PORT}`,
    chromeConnected,
    connectedUsers: activeUsers,
    activeUserCount: activeUsers.length,
    vpnConfigUpdatedAt
  });
});

app.get('/api/admin/devices', (req, res) => {
  const activeUsers = Array.from(connectedClients.values()).sort((a, b) => b.lastSeen - a.lastSeen);
  res.json({
    count: activeUsers.length,
    devices: activeUsers,
    serverUptime: Math.floor(process.uptime()),
    currentLocation: activeCustomNode || PROXY_NODES[currentNodeIndex]
  });
});

app.post('/api/shield', async (req, res) => {
  const { active } = req.body;
  shieldActive = Boolean(active);
  await setWindowsProxy(shieldActive);
  if (shieldActive) {
    countdownSeconds = rotationIntervalSeconds;
    await torController.ensureTorRunning();
    const node = PROXY_NODES[currentNodeIndex];
    await torController.switchCountry(node.countryCode);
    await cdpController.setBrowserGeolocation(node.lat, node.lon);
  }
  res.json({ success: true, shieldActive });
});

app.post('/api/autorotate', (req, res) => {
  const { active } = req.body;
  autoRotateActive = Boolean(active);
  res.json({ success: true, autoRotateActive });
});

app.post('/api/settings/interval', (req, res) => {
  const { seconds, isRandom } = req.body;
  if (seconds && Number(seconds) >= 5) {
    rotationIntervalSeconds = parseInt(seconds, 10);
    countdownSeconds = rotationIntervalSeconds;
  }
  if (isRandom !== undefined) {
    isRandomPoolMode = Boolean(isRandom);
  }
  res.json({
    success: true,
    rotationIntervalSeconds,
    isRandomPoolMode,
    countdownSeconds
  });
});

app.post('/api/rotate', async (req, res) => {
  await rotateNode(null, 'User Triggered Random Rotation');
  res.json({ success: true, currentNode: activeCustomNode || PROXY_NODES[currentNodeIndex] });
});

app.post('/api/select-node', async (req, res) => {
  const { nodeId } = req.body;
  const idx = PROXY_NODES.findIndex((n) => n.id === nodeId);
  if (idx !== -1) {
    await rotateNode(idx, `Selected ${PROXY_NODES[idx].city}`);
  }
  res.json({ success: true, currentNode: PROXY_NODES[currentNodeIndex] });
});

// ---- VPN config sync (web dashboard -> control hub -> Android app) ----
// Dashboard pushes the full `phantom-vpn-config` payload here (see VPN_CONFIG_CONTRACT.md).
app.post('/api/vpn-config', (req, res) => {
  const cfg = req.body;
  if (!cfg || typeof cfg !== 'object' || Array.isArray(cfg)) {
    return res.status(400).json({ ok: false, error: 'Expected a JSON object payload' });
  }
  if (cfg.contract && cfg.contract !== 'phantom-vpn-config') {
    return res.status(400).json({ ok: false, error: `Unknown contract: ${cfg.contract}` });
  }
  vpnConfig = cfg;
  vpnConfigUpdatedAt = new Date().toISOString();
  console.log(`[VPN] Config updated at ${vpnConfigUpdatedAt} (protocol=${cfg.protocol || 'smart'}, netshield=${cfg.netshield || 'off'})`);
  res.json({ ok: true, appliedAt: vpnConfigUpdatedAt });
});

// Android app fetches the latest pushed config from here.
app.get('/api/vpn/config', (req, res) => {
  if (!vpnConfig) {
    return res.status(404).json({ ok: false, error: 'No VPN config has been pushed yet' });
  }
  res.json({ ok: true, appliedAt: vpnConfigUpdatedAt, config: vpnConfig });
});

// Geocoding Search API (Converts place names like "Uttam Nagar" or "DESU Colony" into coordinates)
app.get('/api/geocode', async (req, res) => {
  const query = req.query.q;
  if (!query || query.trim().length === 0) {
    return res.json({ results: [] });
  }

  try {
    const encoded = encodeURIComponent(query.trim());
    const resp = await fetch(`https://nominatim.openstreetmap.org/search?q=${encoded}&format=json&addressdetails=1&limit=6`, {
      headers: {
        'User-Agent': 'PhantomGPS-Desktop/2.0 (contact: info@phantomgps.local)'
      }
    });

    if (!resp.ok) {
      return res.status(resp.status).json({ error: 'Geocoding service unavailable' });
    }

    const data = await resp.json();
    const results = data.map(item => ({
      name: item.name || item.display_name.split(',')[0],
      displayName: item.display_name,
      lat: parseFloat(item.lat),
      lon: parseFloat(item.lon),
      type: item.type,
      category: item.class
    }));

    res.json({ results });
  } catch (err) {
    console.error('[Geocode Error]:', err.message);
    res.status(500).json({ error: 'Failed to geocode location' });
  }
});

// ROUTE SIMULATION APIS (Walking, Vehicle, Flight) - Real Road Snapping
app.post('/api/route/start', async (req, res) => {
  let { origin, destination, mode } = req.body;
  if (!origin || !destination) {
    return res.status(400).json({ error: 'Origin and destination required' });
  }

  // Helper to resolve string place name to lat/lon if needed
  async function resolveLocation(loc) {
    if (typeof loc === 'object' && loc.lat && loc.lon) {
      return { lat: parseFloat(loc.lat), lon: parseFloat(loc.lon), name: loc.name || `${loc.lat}, ${loc.lon}` };
    }
    if (typeof loc === 'string') {
      const coordMatch = loc.match(/(-?\d+\.\d+),\s*(-?\d+\.\d+)/);
      if (coordMatch) {
        return { lat: parseFloat(coordMatch[1]), lon: parseFloat(coordMatch[2]), name: loc };
      }
      // Query Nominatim
      try {
        const encoded = encodeURIComponent(loc.trim());
        const resp = await fetch(`https://nominatim.openstreetmap.org/search?q=${encoded}&format=json&limit=1`, {
          headers: { 'User-Agent': 'PhantomGPS-Desktop/2.0' }
        });
        const items = await resp.json();
        if (items && items.length > 0) {
          return {
            lat: parseFloat(items[0].lat),
            lon: parseFloat(items[0].lon),
            name: items[0].name || items[0].display_name.split(',')[0]
          };
        }
      } catch (e) {
        console.error('Resolve location failed:', e.message);
      }
    }
    return null;
  }

  const resolvedOrigin = await resolveLocation(origin);
  const resolvedDest = await resolveLocation(destination);

  if (!resolvedOrigin || !resolvedDest) {
    return res.status(400).json({ error: 'Could not resolve origin or destination location name to coordinates.' });
  }

  origin = resolvedOrigin;
  destination = resolvedDest;

  // Track live simulated position and push directly to CDP & mock devices without mutating node pool
  const route = await routeEngine.startRoute(origin, destination, mode || 'vehicle');
  cdpController.setBrowserGeolocation(origin.lat, origin.lon).catch(() => {});
  res.json({ success: true, route });
});

app.post('/api/route/stop', (req, res) => {
  const stopped = routeEngine.stopRoute();
  res.json({ success: true, stopped });
});

app.post('/api/launch-chrome', async (req, res) => {
  const node = PROXY_NODES[currentNodeIndex];
  await torController.ensureTorRunning();
  const launched = cdpController.launchChameleonChrome('https://www.google.com/maps', PROXY_PORT);
  
  setTimeout(async () => {
    await cdpController.setBrowserGeolocation(node.lat, node.lon);
  }, 2500);

  res.json({ success: launched, targetNode: node });
});

// 5-Layer Privacy Fortress Endpoints
app.get('/api/privacy', async (req, res) => {
  const status = await privacyFortress.getPrivacyStatus();
  res.json(status);
});

app.post('/api/privacy/fortress', async (req, res) => {
  const status = await privacyFortress.activateFullFortress();
  res.json({ success: true, status });
});

app.post('/api/privacy/mac', async (req, res) => {
  const { enabled } = req.body;
  const result = await privacyFortress.setMacRandomization(enabled);
  res.json({ success: true, result });
});

app.post('/api/privacy/os', async (req, res) => {
  const { enabled } = req.body;
  const result = await privacyFortress.setOsPrivacyShield(enabled);
  res.json({ success: true, result });
});

// Muse AI MCP Protocol Endpoint
app.post('/mcp', async (req, res) => {
  const { jsonrpc, id, method, params } = req.body;

  if (method === 'initialize') {
    return res.json({
      jsonrpc: '2.0',
      id,
      result: {
        protocolVersion: '2024-11-05',
        capabilities: { tools: {} },
        serverInfo: {
          name: 'omnirush-phantom-gps',
          version: '2.0.0'
        }
      }
    });
  }

  if (method === 'tools/list') {
    const manifestPath = path.join(__dirname, 'public', 'muse-connector.json');
    let tools = [];
    try {
      const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
      tools = manifest.tools || [];
    } catch (e) {}

    return res.json({
      jsonrpc: '2.0',
      id,
      result: { tools }
    });
  }

  if (method === 'tools/call') {
    const toolName = params && params.name;
    const args = (params && params.arguments) || {};

    try {
      if (toolName === 'get_gps_status') {
        const node = activeCustomNode || PROXY_NODES[currentNodeIndex];
        return res.json({
          jsonrpc: '2.0',
          id,
          result: {
            content: [{
              type: 'text',
              text: JSON.stringify({
                currentNode: node,
                countdownSeconds,
                rotationIntervalSeconds,
                activeRoute: routeEngine.getStatus(),
                activeUserCount: connectedClients.size
              })
            }]
          }
        });
      }

      if (toolName === 'rotate_location') {
        await rotateNode(null, 'Muse AI Triggered Hop');
        const node = activeCustomNode || PROXY_NODES[currentNodeIndex];
        return res.json({
          jsonrpc: '2.0',
          id,
          result: {
            content: [{
              type: 'text',
              text: `Teleported location to ${node.city}, ${node.country} (lat: ${node.lat}, lon: ${node.lon})`
            }]
          }
        });
      }

      if (toolName === 'select_node') {
        const idx = PROXY_NODES.findIndex(n => n.id === args.nodeId);
        if (idx !== -1) {
          await rotateNode(idx, `Muse AI Selected ${PROXY_NODES[idx].city}`);
          const node = PROXY_NODES[idx];
          return res.json({
            jsonrpc: '2.0',
            id,
            result: {
              content: [{
                type: 'text',
                text: `Location switched to ${node.city}, ${node.country} (lat: ${node.lat}, lon: ${node.lon})`
              }]
            }
          });
        }
        return res.json({
          jsonrpc: '2.0',
          id,
          error: { code: -32602, message: `Node '${args.nodeId}' not found.` }
        });
      }

      if (toolName === 'start_route_simulation') {
        // Run route through routeEngine
        const originRes = await fetch(`https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(args.origin)}&format=json&limit=1`, { headers: { 'User-Agent': 'PhantomGPS-Desktop/2.0' } });
        const originItems = await originRes.json();
        const destRes = await fetch(`https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(args.destination)}&format=json&limit=1`, { headers: { 'User-Agent': 'PhantomGPS-Desktop/2.0' } });
        const destItems = await destRes.json();

        if (originItems.length > 0 && destItems.length > 0) {
          const origin = { lat: parseFloat(originItems[0].lat), lon: parseFloat(originItems[0].lon), name: originItems[0].display_name };
          const destination = { lat: parseFloat(destItems[0].lat), lon: parseFloat(destItems[0].lon), name: destItems[0].display_name };
          const route = await routeEngine.startRoute(origin, destination, args.mode || 'vehicle');
          return res.json({
            jsonrpc: '2.0',
            id,
            result: {
              content: [{
                type: 'text',
                text: `Route navigation started from ${origin.name} to ${destination.name} (${route.distanceKm} km along roads).`
              }]
            }
          });
        }
      }

      if (toolName === 'stop_route_simulation') {
        routeEngine.stopRoute();
        return res.json({
          jsonrpc: '2.0',
          id,
          result: {
            content: [{
              type: 'text',
              text: 'Route navigation simulation stopped.'
            }]
          }
        });
      }

      if (toolName === 'set_hop_interval') {
        if (args.seconds && args.seconds >= 5) {
          rotationIntervalSeconds = parseInt(args.seconds, 10);
          countdownSeconds = rotationIntervalSeconds;
          return res.json({
            jsonrpc: '2.0',
            id,
            result: {
              content: [{
                type: 'text',
                text: `Rotation cadence interval updated to ${rotationIntervalSeconds} seconds.`
              }]
            }
          });
        }
      }
    } catch (err) {
      return res.json({
        jsonrpc: '2.0',
        id,
        error: { code: -32603, message: err.message }
      });
    }
  }

  return res.json({
    jsonrpc: '2.0',
    id,
    error: { code: -32601, message: 'Method not found' }
  });
});

// Start Server on 0.0.0.0
app.listen(UI_PORT, '0.0.0.0', async () => {
  const lanIp = getLocalLanIp();
  console.log(`[OmniRush Control Hub] Running at:`);
  console.log(`  - Local:  http://127.0.0.1:${UI_PORT}`);
  console.log(`  - Mobile: http://${lanIp}:${UI_PORT}`);
  
  await smartProxy.createSmartProxy();
  await torController.ensureTorRunning();
  await privacyFortress.activateFullFortress();
});

// Clean up proxy on exit
process.on('SIGINT', async () => {
  await setWindowsProxy(false);
  process.exit();
});

process.on('SIGTERM', async () => {
  await setWindowsProxy(false);
  process.exit();
});
