const http = require('http');
const net = require('net');
const url = require('url');

const PROXY_PORT = 8899;
const TOR_PORT = 9055;
const TOR_TIMEOUT_MS = 2000; // 2 seconds max before instant failover to direct

// Hostnames and certificate revocation checks that should always connect directly
// to prevent CRYPT_E_REVOCATION_OFFLINE and keep Windows connectivity 100% online
const BYPASS_PATTERNS = [
  /localhost/i,
  /127\.0\.0\.1/,
  /192\.168\./,
  /msftncsi\.com/i,
  /msftconnecttest\.com/i,
  /windowsupdate\.com/i,
  /microsoft\.com/i,
  /windows\.com/i,
  /pki\.goog/i,
  /digicert\.com/i,
  /sectigo\.com/i,
  /identrust\.com/i,
  /letsencrypt\.org/i,
  /globalsign\.com/i,
  /ocsp/i,
  /crl/i,
  /project-osrm\.org/i,
  /openstreetmap\.org/i
];

function shouldBypass(hostname) {
  if (!hostname) return false;
  return BYPASS_PATTERNS.some(pattern => pattern.test(hostname));
}

// Create Smart HTTP & HTTPS Tunnel Proxy
function createSmartProxy() {
  const server = http.createServer((req, res) => {
    // Normal HTTP proxy requests
    const parsed = url.parse(req.url);
    const targetHost = parsed.hostname;
    const targetPort = parsed.port || 80;

    const connector = net.connect(targetPort, targetHost, () => {
      connector.write(`${req.method} ${parsed.path} HTTP/1.1\r\n`);
      for (let i = 0; i < req.rawHeaders.length; i += 2) {
        connector.write(`${req.rawHeaders[i]}: ${req.rawHeaders[i+1]}\r\n`);
      }
      connector.write('\r\n');
      req.pipe(connector);
      connector.pipe(res);
    });

    connector.on('error', () => {
      res.writeHead(502);
      res.end('Proxy Gateway Error');
    });
  });

  // HTTPS CONNECT Tunneling
  server.on('connect', (req, clientSocket, head) => {
    const { port, hostname } = url.parse(`//${req.url}`, false, true);
    const targetPort = parseInt(port, 10) || 443;
    const targetHost = hostname;

    // Direct connection helper
    function connectDirect() {
      const directSocket = net.connect(targetPort, targetHost, () => {
        clientSocket.write('HTTP/1.1 200 Connection Established\r\n\r\n');
        if (head && head.length) directSocket.write(head);
        directSocket.pipe(clientSocket);
        clientSocket.pipe(directSocket);
      });

      directSocket.on('error', () => {
        try { clientSocket.end(); } catch (e) {}
      });

      clientSocket.on('error', () => {
        try { directSocket.end(); } catch (e) {}
      });
    }

    // If host matches bypass rules (Windows NCSI, CRL, OCSP, local network), connect direct immediately
    if (shouldBypass(targetHost)) {
      return connectDirect();
    }

    // Try routing via Tor HTTP tunnel
    let handled = false;
    const torSocket = net.connect(TOR_PORT, '127.0.0.1');

    const timer = setTimeout(() => {
      if (!handled) {
        handled = true;
        try { torSocket.destroy(); } catch (e) {}
        // Tor timed out (likely rebuilding circuit during rotation) - Failover to direct!
        connectDirect();
      }
    }, TOR_TIMEOUT_MS);

    torSocket.on('connect', () => {
      torSocket.write(`CONNECT ${targetHost}:${targetPort} HTTP/1.1\r\nHost: ${targetHost}:${targetPort}\r\n\r\n`);
    });

    torSocket.once('data', (data) => {
      if (handled) return;
      handled = true;
      clearTimeout(timer);

      const statusLine = data.toString().split('\r\n')[0];
      if (statusLine.includes('200')) {
        // Tor tunnel successfully established!
        clientSocket.write('HTTP/1.1 200 Connection Established\r\n\r\n');
        if (head && head.length) torSocket.write(head);
        torSocket.pipe(clientSocket);
        clientSocket.pipe(torSocket);
      } else {
        // Tor rejected or errored - failover to direct!
        try { torSocket.destroy(); } catch (e) {}
        connectDirect();
      }
    });

    torSocket.on('error', () => {
      if (handled) return;
      handled = true;
      clearTimeout(timer);
      // Tor daemon unavailable - failover to direct
      connectDirect();
    });

    clientSocket.on('error', () => {
      try { torSocket.destroy(); } catch (e) {}
    });
  });

  return new Promise((resolve) => {
    server.listen(PROXY_PORT, '127.0.0.1', () => {
      console.log(`[Smart Proxy] Active on 127.0.0.1:${PROXY_PORT} (with zero-downtime failover)`);
      resolve(server);
    });
  });
}

module.exports = {
  createSmartProxy,
  PROXY_PORT
};

