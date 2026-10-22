// OmniRush 5-Layer Privacy Fortress & Chameleon Content Script (Runs in MAIN world at document_start)
(function() {
  const DEFAULT_NODES = [
    { city: 'Tokyo', country: 'Japan', flag: '🇯🇵', lat: 35.6762, lon: 139.6503, ip: '103.253.140.9' },
    { city: 'Zurich', country: 'Switzerland', flag: '🇨🇭', lat: 47.3769, lon: 8.5417, ip: '185.220.101.98' },
    { city: 'New York', country: 'United States', flag: '🇺🇸', lat: 40.7128, lon: -74.0060, ip: '198.51.100.42' },
    { city: 'Reykjavik', country: 'Iceland', flag: '🇮🇸', lat: 64.1466, lon: -21.9426, ip: '185.159.157.2' },
    { city: 'Singapore', country: 'Singapore', flag: '🇸🇬', lat: 1.3521, lon: 103.8198, ip: '165.22.240.18' },
    { city: 'Berlin', country: 'Germany', flag: '🇩🇪', lat: 52.5200, lon: 13.4050, ip: '89.187.160.73' }
  ];

  let activeNode = DEFAULT_NODES[0];
  let lastReportedCity = null;
  const watchCallbacks = new Set();

  function compute5MinEpoch() {
    return Math.floor(Date.now() / (5 * 60 * 1000));
  }

  function compute5MinNode() {
    return DEFAULT_NODES[compute5MinEpoch() % DEFAULT_NODES.length];
  }

  activeNode = compute5MinNode();
  lastReportedCity = activeNode.city;

  // ==========================================
  // LAYER 4: CANVAS FINGERPRINT DEFENSE
  // ==========================================
  const noiseFactor = ((compute5MinEpoch() % 7) - 3) || 1; // Subtle delta shifting every 5 min

  const origToDataURL = HTMLCanvasElement.prototype.toDataURL;
  HTMLCanvasElement.prototype.toDataURL = function(...args) {
    try {
      const ctx = this.getContext('2d');
      if (ctx && this.width > 8 && this.height > 8) {
        const px = ctx.getImageData(0, 0, 1, 1);
        px.data[0] = (px.data[0] + noiseFactor) % 256;
        ctx.putImageData(px, 0, 0);
      }
    } catch (e) {}
    return origToDataURL.apply(this, args);
  };

  const origGetImageData = CanvasRenderingContext2D.prototype.getImageData;
  CanvasRenderingContext2D.prototype.getImageData = function(...args) {
    const res = origGetImageData.apply(this, args);
    try {
      if (res && res.data && res.data.length >= 4) {
        res.data[0] = (res.data[0] + noiseFactor) % 256;
      }
    } catch (e) {}
    return res;
  };

  // ==========================================
  // LAYER 4: WEBGL GPU MODEL SPOOFING
  // ==========================================
  const GPU_PROFILES = [
    { vendor: 'Google Inc. (NVIDIA)', renderer: 'ANGLE (NVIDIA, NVIDIA GeForce RTX 3070 Direct3D11 vs_5_0 ps_5_0, D3D11)' },
    { vendor: 'Google Inc. (Apple)', renderer: 'ANGLE (Apple, Apple M2 Pro, OpenGL 4.1)' },
    { vendor: 'Google Inc. (Intel)', renderer: 'ANGLE (Intel, Intel(R) Iris(R) Xe Graphics Direct3D11 vs_5_0 ps_5_0, D3D11)' },
    { vendor: 'Google Inc. (AMD)', renderer: 'ANGLE (AMD, AMD Radeon RX 6700 XT Direct3D11 vs_5_0 ps_5_0, D3D11)' }
  ];

  function getActiveGpu() {
    return GPU_PROFILES[compute5MinEpoch() % GPU_PROFILES.length];
  }

  const hookWebGL = (proto) => {
    if (!proto || !proto.getParameter) return;
    const origGetParam = proto.getParameter;
    proto.getParameter = function(param) {
      if (param === 0x9245 || param === 37445) { // UNMASKED_VENDOR_WEBGL
        return getActiveGpu().vendor;
      }
      if (param === 0x9246 || param === 37446) { // UNMASKED_RENDERER_WEBGL
        return getActiveGpu().renderer;
      }
      return origGetParam.apply(this, arguments);
    };
  };

  if (typeof WebGLRenderingContext !== 'undefined') hookWebGL(WebGLRenderingContext.prototype);
  if (typeof WebGL2RenderingContext !== 'undefined') hookWebGL(WebGL2RenderingContext.prototype);

  // ==========================================
  // LAYER 4: AUDIOCONTEXT FREQUENCY NOISE
  // ==========================================
  if (typeof AudioBuffer !== 'undefined' && AudioBuffer.prototype.getChannelData) {
    const origChannel = AudioBuffer.prototype.getChannelData;
    AudioBuffer.prototype.getChannelData = function(channel) {
      const buf = origChannel.apply(this, arguments);
      if (buf && buf.length > 50) {
        buf[10] += (Math.random() - 0.5) * 0.0000001;
      }
      return buf;
    };
  }

  // ==========================================
  // LAYER 4: HARDWARE NORMALIZATION
  // ==========================================
  try {
    Object.defineProperty(Navigator.prototype, 'hardwareConcurrency', { get: () => 8, configurable: true });
    Object.defineProperty(Navigator.prototype, 'deviceMemory', { get: () => 8, configurable: true });
  } catch (e) {}

  // ==========================================
  // LAYER 3 & 4: GEOLOCATION SPOOFING & AUTO-FLY
  // ==========================================
  function createPositionObj() {
    return {
      coords: {
        latitude: activeNode.lat,
        longitude: activeNode.lon,
        accuracy: 10,
        altitude: null,
        altitudeAccuracy: null,
        heading: null,
        speed: null
      },
      timestamp: Date.now()
    };
  }

  function notifyLocationChanged() {
    for (const cb of watchCallbacks) {
      try { cb(createPositionObj()); } catch (e) {}
    }

    if (window.location.hostname.includes('google') && window.location.pathname.includes('/maps')) {
      const targetUrl = `https://www.google.com/maps/@${activeNode.lat},${activeNode.lon},14z`;
      console.log(`[OmniRush] Auto-Pan Google Maps to ${activeNode.city}:`, targetUrl);
      window.location.replace(targetUrl);
    }
  }

  // Sync with desktop app server
  async function syncWithDesktopApp() {
    try {
      const res = await fetch('http://127.0.0.1:3300/api/status');
      if (res.ok) {
        const data = await res.json();
        if (data.currentNode) {
          const newCity = data.currentNode.city;
          const hasChanged = lastReportedCity && lastReportedCity !== newCity;

          activeNode = {
            city: data.currentNode.city,
            country: data.currentNode.country,
            flag: data.currentNode.flag,
            lat: data.currentNode.lat,
            lon: data.currentNode.lon,
            ip: data.currentNode.virtualIp || data.currentNode.upstreamHost
          };

          if (hasChanged) {
            lastReportedCity = newCity;
            notifyLocationChanged();
          }
        }
      }
    } catch (e) {
      const calculated = compute5MinNode();
      if (lastReportedCity && lastReportedCity !== calculated.city) {
        activeNode = calculated;
        lastReportedCity = calculated.city;
        notifyLocationChanged();
      }
    }
  }

  syncWithDesktopApp();
  setInterval(syncWithDesktopApp, 4000);

  const fakeGeo = {
    getCurrentPosition: function(success, error, options) {
      console.log('[OmniRush] Spoofing GPS to:', activeNode.city, activeNode.lat, activeNode.lon);
      setTimeout(() => success(createPositionObj()), 10);
    },
    watchPosition: function(success, error, options) {
      watchCallbacks.add(success);
      setTimeout(() => success(createPositionObj()), 10);
      return success;
    },
    clearWatch: function(id) {
      watchCallbacks.delete(id);
    }
  };

  if (typeof Geolocation !== 'undefined' && Geolocation.prototype) {
    try {
      Geolocation.prototype.getCurrentPosition = fakeGeo.getCurrentPosition;
      Geolocation.prototype.watchPosition = fakeGeo.watchPosition;
    } catch(e) {}
  }

  if (typeof Navigator !== 'undefined' && Navigator.prototype) {
    try {
      Object.defineProperty(Navigator.prototype, 'geolocation', {
        get: function() { return fakeGeo; },
        configurable: true
      });
    } catch(e) {}
  }

  if (navigator.geolocation) {
    try {
      Object.defineProperty(navigator, 'geolocation', {
        value: fakeGeo,
        configurable: true,
        writable: true
      });
    } catch (e) {
      navigator.geolocation.getCurrentPosition = fakeGeo.getCurrentPosition;
      navigator.geolocation.watchPosition = fakeGeo.watchPosition;
    }
  }

  if (navigator.permissions && navigator.permissions.query) {
    const origQuery = navigator.permissions.query.bind(navigator.permissions);
    navigator.permissions.query = function(params) {
      if (params && params.name === 'geolocation') {
        return Promise.resolve({
          state: 'granted',
          onchange: null
        });
      }
      return origQuery(params);
    };
  }

  // Intercept fetch for Google location service APIs
  const origFetch = window.fetch;
  window.fetch = async function(...args) {
    const url = typeof args[0] === 'string' ? args[0] : (args[0] && args[0].url ? args[0].url : '');
    if (url && (url.includes('geolocate') || url.includes('geolocation') || url.includes('maps/api/place'))) {
      return new Response(JSON.stringify({
        location: {
          lat: activeNode.lat,
          lng: activeNode.lon
        },
        accuracy: 10.0
      }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      });
    }
    return origFetch.apply(this, args);
  };

  console.log(`[OmniRush 5-Layer Privacy Fortress] Active. Node: ${activeNode.flag} ${activeNode.city} | Canvas, WebGL, Audio Poisoning: ON`);
})();
