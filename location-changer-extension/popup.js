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

// Initial immediate render
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
  } catch (e) {}

  renderNode(computeLocalNode());
}

document.addEventListener('DOMContentLoaded', () => {
  renderNode(computeLocalNode());
  updatePopup();
  setInterval(updatePopup, 1000);
});

