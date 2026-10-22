const net = require('net');
const tls = require('tls');
const http = require('http');
const { spawn, exec } = require('child_process');
const path = require('path');
const fs = require('fs');

const TOR_EXE = path.join(__dirname, '..', 'tor-daemon', 'tor', 'tor.exe');
const TORRC = path.join(__dirname, '..', 'tor-daemon', 'torrc');
const CONTROL_PORT = 9051;
const HTTP_TUNNEL_PORT = 9055;

let torProcess = null;

// Send command to Tor control port
function sendControlCommand(command) {
  return new Promise((resolve, reject) => {
    const client = net.connect(CONTROL_PORT, '127.0.0.1', () => {
      client.write(`AUTHENTICATE ""\r\n${command}\r\nQUIT\r\n`);
    });

    let data = '';
    client.on('data', chunk => data += chunk.toString());
    client.on('end', () => resolve(data));
    client.on('error', (err) => reject(err));
  });
}

// Check if Tor is already running and ready
async function isTorReady() {
  try {
    const resp = await sendControlCommand('GETINFO status/bootstrap-phase');
    return resp.includes('PROGRESS=100');
  } catch (e) {
    return false;
  }
}

// Ensure Tor daemon is running
async function ensureTorRunning() {
  const ready = await isTorReady();
  if (ready) {
    console.log('[Tor] Daemon is already running and ready on ports 8899/9051.');
    return true;
  }

  console.log('[Tor] Starting Tor daemon process...');
  if (!fs.existsSync(TOR_EXE)) {
    console.error('[Tor] Executable not found at:', TOR_EXE);
    return false;
  }

  torProcess = spawn(TOR_EXE, ['-f', TORRC], {
    detached: true,
    stdio: 'ignore'
  });
  torProcess.unref();

  // Poll until bootstrapped (max 30 seconds)
  for (let i = 0; i < 30; i++) {
    await new Promise(r => setTimeout(r, 1000));
    if (await isTorReady()) {
      console.log('[Tor] Tor successfully bootstrapped and ready!');
      return true;
    }
  }
  return false;
}

// Switch Tor exit node country
async function switchCountry(countryCode) {
  try {
    const cmd = `SETCONF ExitNodes={${countryCode}} StrictNodes=0\r\nSIGNAL NEWNYM`;
    const resp = await sendControlCommand(cmd);
    console.log(`[Tor] Switched exit country to {${countryCode}}:`, resp.trim().replace(/\r\n/g, ' '));
    return true;
  } catch (err) {
    console.error(`[Tor] Error switching exit country:`, err.message);
    return false;
  }
}

// Verify real external IP through the Tor HTTP tunnel
function fetchVerifiedIp() {
  return new Promise((resolve) => {
    const proxySocket = net.connect(HTTP_TUNNEL_PORT, '127.0.0.1', () => {
      proxySocket.write('CONNECT api.ipify.org:443 HTTP/1.1\r\nHost: api.ipify.org:443\r\n\r\n');
    });

    proxySocket.once('data', (d) => {
      const tlsSocket = tls.connect({
        socket: proxySocket,
        servername: 'api.ipify.org'
      }, () => {
        tlsSocket.write('GET /?format=json HTTP/1.1\r\nHost: api.ipify.org\r\nConnection: close\r\n\r\n');
      });

      let responseText = '';
      tlsSocket.on('data', chunk => responseText += chunk.toString());
      tlsSocket.on('end', () => {
        try {
          const body = responseText.split('\r\n\r\n')[1];
          const parsed = JSON.parse(body);
          resolve(parsed.ip);
        } catch (e) {
          resolve(null);
        }
      });
      tlsSocket.on('error', () => resolve(null));
    });

    proxySocket.on('error', () => resolve(null));
    setTimeout(() => resolve(null), 8000);
  });
}

module.exports = {
  ensureTorRunning,
  isTorReady,
  switchCountry,
  fetchVerifiedIp,
  HTTP_TUNNEL_PORT,
  CONTROL_PORT
};

