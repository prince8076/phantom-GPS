const http = require('http');
const { spawn } = require('child_process');
const path = require('path');

const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const USER_DATA_DIR = path.join(__dirname, '..', 'chrome-chameleon-profile');
const EXTENSION_PATH = path.join(__dirname, '..', 'location-changer-extension');
const CDP_PORT = 9222;

let activeChromeProcess = null;
let currentCoords = { lat: 47.3769, lon: 8.5417 };

// Get list of targets from Chrome CDP
function getTargets() {
  return new Promise((resolve) => {
    http.get(`http://127.0.0.1:${CDP_PORT}/json/list`, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          resolve(JSON.parse(data));
        } catch (e) {
          resolve([]);
        }
      });
    }).on('error', () => resolve([]));
  });
}

// Override geolocation on a target via WebSocket
function overrideTargetGeo(wsUrl, lat, lon) {
  return new Promise((resolve) => {
    try {
      const ws = new WebSocket(wsUrl);
      ws.onopen = () => {
        const cmd = {
          id: Date.now(),
          method: 'Emulation.setGeolocationOverride',
          params: {
            latitude: lat,
            longitude: lon,
            accuracy: 10
          }
        };
        ws.send(JSON.stringify(cmd));
        setTimeout(() => {
          try { ws.close(); } catch(e){}
          resolve(true);
        }, 150);
      };
      ws.onerror = () => resolve(false);
    } catch (e) {
      resolve(false);
    }
  });
}

// Set geolocation across all active browser tabs
async function setBrowserGeolocation(lat, lon) {
  currentCoords = { lat, lon };
  const targets = await getTargets();
  const pageTargets = targets.filter(t => t.type === 'page' && t.webSocketDebuggerUrl);
  let updatedCount = 0;
  for (const target of pageTargets) {
    const success = await overrideTargetGeo(target.webSocketDebuggerUrl, lat, lon);
    if (success) updatedCount++;
  }
  return { totalTabs: pageTargets.length, updatedCount };
}

// Background keep-alive for newly opened tabs
setInterval(async () => {
  const targets = await getTargets();
  const pageTargets = targets.filter(t => t.type === 'page' && t.webSocketDebuggerUrl);
  for (const target of pageTargets) {
    overrideTargetGeo(target.webSocketDebuggerUrl, currentCoords.lat, currentCoords.lon).catch(() => {});
  }
}, 4000);

// Launch Chrome with proxy, extension auto-loaded, and CDP debugging enabled
function launchChameleonChrome(initialUrl = 'https://www.google.com/maps', proxyPort = 8899) {
  const args = [
    `--remote-debugging-port=${CDP_PORT}`,
    `--proxy-server=http://127.0.0.1:${proxyPort}`,
    `--user-data-dir=${USER_DATA_DIR}`,
    `--load-extension=${EXTENSION_PATH}`,
    '--no-first-run',
    '--no-default-browser-check',
    initialUrl
  ];

  try {
    activeChromeProcess = spawn(CHROME_PATH, args, {
      detached: true,
      stdio: 'ignore'
    });
    activeChromeProcess.unref();
    return true;
  } catch (err) {
    console.error('[CDP] Failed to launch Chrome:', err);
    return false;
  }
}

module.exports = {
  getTargets,
  setBrowserGeolocation,
  launchChameleonChrome
};

