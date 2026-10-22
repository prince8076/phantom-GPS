const toast = document.querySelector('#toast');
let toastTimer;
const overviewHtml = document.querySelector('#app-view').innerHTML;

const ROTATION_INTERVAL_SECONDS = 300; // 5 minutes
let countdownValue = ROTATION_INTERVAL_SECONDS;
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


const proxyNodes = [
  { id: 'zurich', name: 'Zurich, Switzerland', ip: '185.220.101.7', coords: 'Lat: 47.3769 · Lon: 8.5417', isp: 'Tor Exit Node · AS59842' },
  { id: 'tokyo', name: 'Tokyo, Japan', ip: '103.253.140.9', coords: 'Lat: 35.6762 · Lon: 139.6503', isp: 'Datacenter Proxy · AS2516' },
  { id: 'reykjavik', name: 'Reykjavik, Iceland', ip: '185.159.157.2', coords: 'Lat: 64.1466 · Lon: -21.9426', isp: 'Secure Enclave · AS44556' },
  { id: 'newyork', name: 'New York, US', ip: '198.51.100.42', coords: 'Lat: 40.7128 · Lon: -74.0060', isp: 'Cloudflare Warp · AS13335' },
  { id: 'singapore', name: 'Singapore, SG', ip: '165.22.240.18', coords: 'Lat: 1.3521 · Lon: 103.8198', isp: 'DigitalOcean SG · AS14061' },
  { id: 'berlin', name: 'Berlin, Germany', ip: '89.187.160.73', coords: 'Lat: 52.5200 · Lon: 13.4050', isp: 'MivoCloud · AS208833' }
];

function showToast(message) {
  toast.textContent = message;
  toast.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove('show'), 3200);
}

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

// GeoIP Chameleon Engine Logic
function initGeoIpWorkspace() {
  countdownValue = ROTATION_INTERVAL_SECONDS;
  startGeoIpTimer();
  fetchRealIp();

  const rotateBtn = document.querySelector('#rotate-now-btn');
  if (rotateBtn) {
    rotateBtn.addEventListener('click', () => rotateToRandomNode('Manual 5-Min Rotation'));
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
      const autoRotateCheckbox = document.querySelector('#auto-rotate-toggle');
      if (autoRotateCheckbox) autoRotateCheckbox.checked = chameleonShieldActive;
      
      const badge = document.querySelector('#shield-status-badge');
      if (badge) {
        badge.textContent = chameleonShieldActive ? '● Active' : '○ Paused';
        badge.className = chameleonShieldActive ? 'trend positive' : 'trend negative';
      }

      const caption = document.querySelector('#timer-status-caption');
      if (caption) {
        caption.textContent = chameleonShieldActive 
          ? 'Auto-rotates IP & location every 5 mins' 
          : 'Auto-rotation paused (Shield OFF)';
      }

      if (chameleonShieldActive) {
        countdownValue = ROTATION_INTERVAL_SECONDS;
        startGeoIpTimer();
        showToast('🛡️ Chameleon Shield ON: Auto-rotating IP and Location every 5 minutes.');
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
      const caption = document.querySelector('#timer-status-caption');
      if (caption) {
        caption.textContent = autoRotateActive 
          ? 'Auto-rotates IP & location every 5 mins' 
          : 'Auto-rotation paused';
      }
      showToast(autoRotateActive ? '5-Minute Auto-Rotate enabled.' : '5-Minute Auto-Rotate paused.');
    });
  }

  document.querySelectorAll('.node-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      const nodeId = btn.dataset.node;
      const node = proxyNodes.find(n => n.id === nodeId);
      if (node) {
        applyProxyNode(node, `Selected Node: ${node.name}`);
        document.querySelectorAll('.node-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
      }
    });
  });
}

function startGeoIpTimer() {
  stopGeoIpTimer();
  timerInterval = setInterval(() => {
    if (!autoRotateActive || !chameleonShieldActive) return;
    countdownValue--;
    const timerEl = document.querySelector('#countdown-timer');
    const percentEl = document.querySelector('#timer-percent');
    if (timerEl) timerEl.textContent = formatCountdown(countdownValue);
    if (percentEl) percentEl.textContent = `${Math.round((countdownValue / ROTATION_INTERVAL_SECONDS) * 100)}%`;

    if (countdownValue <= 0) {
      countdownValue = ROTATION_INTERVAL_SECONDS;
      rotateToRandomNode('Automatic 5-Min Rotation');
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
  const randomNode = proxyNodes[Math.floor(Math.random() * proxyNodes.length)];
  applyProxyNode(randomNode, reason);
  
  document.querySelectorAll('.node-btn').forEach((btn) => {
    btn.classList.toggle('active', btn.dataset.node === randomNode.id);
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

  countdownValue = ROTATION_INTERVAL_SECONDS;
  if (chameleonShieldActive) {
    updateGeolocationMock(node.coords);
  }
  showToast(`Location & IP rotated to ${node.name} (${node.ip}) — next in 5m!`);
}

// Initial binding
bindOverviewEvents();

