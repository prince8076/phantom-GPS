/**
 * OmniRush / Phantom GPS - Real Road-Snapping Navigation & Velocity Engine
 * Fetches turn-by-turn road geometry from OpenStreetMap OSRM routing engine
 * so vehicle and walking movements stay 100% snapped to real roads, avenues,
 * turns, and street intersections instead of cutting across rivers or buildings!
 */

const https = require('https');
const http = require('http');

class RouteEngine {
  constructor() {
    this.activeRoute = null;
    this.timer = null;
    this.listeners = [];
  }

  // Calculate distance between two lat/lon in meters using Haversine formula
  static calculateDistance(lat1, lon1, lat2, lon2) {
    const R = 6371000;
    const dLat = (lat2 - lat1) * Math.PI / 180;
    const dLon = (lon2 - lon1) * Math.PI / 180;
    const a =
      Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
      Math.sin(dLon / 2) * Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
  }

  // Calculate bearing in degrees from point 1 to point 2
  static calculateBearing(lat1, lon1, lat2, lon2) {
    const y = Math.sin((lon2 - lon1) * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180);
    const x = Math.cos(lat1 * Math.PI / 180) * Math.sin(lat2 * Math.PI / 180) -
              Math.sin(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * Math.cos((lon2 - lon1) * Math.PI / 180);
    return (Math.atan2(y, x) * 180 / Math.PI + 360) % 360;
  }

  // Query OpenStreetMap OSRM public routing API for actual road waypoints
  async fetchRoadGeometry(origin, destination, mode = 'vehicle') {
    return new Promise((resolve) => {
      const profile = (mode === 'foot' || mode === 'bicycle') ? 'foot' : 'driving';
      const url = `https://router.project-osrm.org/route/v1/${profile}/${origin.lon},${origin.lat};${destination.lon},${destination.lat}?overview=full&geometries=geojson`;

      const req = https.get(url, { headers: { 'User-Agent': 'PhantomGPS/1.0' }, timeout: 4000 }, (res) => {
        let body = '';
        res.on('data', chunk => body += chunk);
        res.on('end', () => {
          try {
            const data = JSON.parse(body);
            if (data.routes && data.routes[0] && data.routes[0].geometry && data.routes[0].geometry.coordinates) {
              // Convert [lon, lat] pairs into [{lat, lon}]
              const waypoints = data.routes[0].geometry.coordinates.map(coord => ({
                lat: coord[1],
                lon: coord[0]
              }));
              const distanceMeters = data.routes[0].distance;
              return resolve({ waypoints, distanceMeters, isRoadSnapped: true });
            }
          } catch (e) {}
          resolve(this.generateFallbackWaypoints(origin, destination));
        });
      });

      req.on('error', () => {
        resolve(this.generateFallbackWaypoints(origin, destination));
      });
      req.on('timeout', () => {
        req.destroy();
        resolve(this.generateFallbackWaypoints(origin, destination));
      });
    });
  }

  // Linear interpolation fallback if internet/OSRM is unavailable
  generateFallbackWaypoints(origin, destination) {
    const waypoints = [];
    const count = 50;
    for (let i = 0; i <= count; i++) {
      const p = i / count;
      waypoints.push({
        lat: origin.lat + (destination.lat - origin.lat) * p,
        lon: origin.lon + (destination.lon - origin.lon) * p
      });
    }
    const dist = RouteEngine.calculateDistance(origin.lat, origin.lon, destination.lat, destination.lon);
    return { waypoints, distanceMeters: dist, isRoadSnapped: false };
  }

  /**
   * Start a realistic turn-by-turn road route between two points.
   * mode: 'foot' (1.4 m/s ~ 5km/h), 'bicycle' (5.0 m/s ~ 18km/h), 'vehicle' (16.7 m/s ~ 60km/h), 'flight' (140 m/s ~ 500km/h)
   */
  async startRoute(origin, destination, mode = 'vehicle', intervalMs = 1000) {
    this.stopRoute();

    const speedMap = {
      foot: 1.4,       // 5 km/h
      bicycle: 5.0,    // 18 km/h
      vehicle: 16.7,   // 60 km/h
      flight: 140.0    // 500 km/h
    };
    const speed = speedMap[mode] || 16.7;

    // Fetch genuine road turn coordinates from OpenStreetMap
    const { waypoints, distanceMeters, isRoadSnapped } = await this.fetchRoadGeometry(origin, destination, mode);

    // Resample waypoints so each 1-second step travels exactly `speed` meters along the road
    const smoothPoints = this.densifyRoadPath(waypoints, speed * (intervalMs / 1000));

    this.activeRoute = {
      origin,
      destination,
      mode,
      speedKmh: Math.round(speed * 3.6),
      totalDistanceKm: (distanceMeters / 1000).toFixed(2),
      totalSteps: smoothPoints.length,
      currentStep: 0,
      currentLat: smoothPoints[0].lat,
      currentLon: smoothPoints[0].lon,
      currentBearing: 0.0,
      smoothPoints,
      isRoadSnapped,
      startTime: Date.now()
    };

    console.log(`[Phantom Route Engine] Starting road trip: ${this.activeRoute.totalDistanceKm} km (${mode} @ ${this.activeRoute.speedKmh} km/h, road-snapped: ${isRoadSnapped})`);

    this.timer = setInterval(() => {
      if (!this.activeRoute) return;

      this.activeRoute.currentStep++;
      if (this.activeRoute.currentStep >= this.activeRoute.smoothPoints.length) {
        this.stopRoute();
        return;
      }

      const prev = this.activeRoute.smoothPoints[this.activeRoute.currentStep - 1];
      const curr = this.activeRoute.smoothPoints[this.activeRoute.currentStep];

      // Exact road coordinate with natural street micro-jitter (sub-meter)
      this.activeRoute.currentLat = curr.lat + (Math.random() - 0.5) * 0.000008;
      this.activeRoute.currentLon = curr.lon + (Math.random() - 0.5) * 0.000008;
      this.activeRoute.currentBearing = RouteEngine.calculateBearing(prev.lat, prev.lon, curr.lat, curr.lon);
      this.activeRoute.progressPercent = Math.round((this.activeRoute.currentStep / this.activeRoute.smoothPoints.length) * 100);

      // Push update to all listeners (browser Chrome, server, Android phone)
      for (const listener of this.listeners) {
        try {
          listener({
            lat: this.activeRoute.currentLat,
            lon: this.activeRoute.currentLon,
            bearing: this.activeRoute.currentBearing,
            speed: speed,
            progress: this.activeRoute.progressPercent,
            isCompleted: false,
            mode: this.activeRoute.mode,
            isRoadSnapped: this.activeRoute.isRoadSnapped
          });
        } catch (e) {}
      }
    }, intervalMs);

    return this.activeRoute;
  }

  // Densify road waypoints into equal step distances along streets
  densifyRoadPath(waypoints, stepDistanceMeters) {
    if (waypoints.length <= 1) return waypoints;
    const path = [waypoints[0]];

    for (let i = 0; i < waypoints.length - 1; i++) {
      const p1 = waypoints[i];
      const p2 = waypoints[i + 1];
      const segmentDist = RouteEngine.calculateDistance(p1.lat, p1.lon, p2.lat, p2.lon);

      if (segmentDist <= stepDistanceMeters) {
        path.push(p2);
      } else {
        const subSteps = Math.ceil(segmentDist / stepDistanceMeters);
        for (let j = 1; j <= subSteps; j++) {
          const frac = j / subSteps;
          path.push({
            lat: p1.lat + (p2.lat - p1.lat) * frac,
            lon: p1.lon + (p2.lon - p1.lon) * frac
          });
        }
      }
    }
    return path;
  }

  stopRoute() {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
    const finished = this.activeRoute;
    this.activeRoute = null;
    return finished;
  }

  getStatus() {
    if (!this.activeRoute) return null;
    return {
      mode: this.activeRoute.mode,
      speedKmh: this.activeRoute.speedKmh,
      totalDistanceKm: this.activeRoute.totalDistanceKm,
      progressPercent: this.activeRoute.progressPercent,
      currentLat: this.activeRoute.currentLat,
      currentLon: this.activeRoute.currentLon,
      currentBearing: this.activeRoute.currentBearing,
      isRoadSnapped: this.activeRoute.isRoadSnapped
    };
  }

  onUpdate(fn) {
    this.listeners.push(fn);
  }
}

module.exports = new RouteEngine();
