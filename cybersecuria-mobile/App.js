import React, { useMemo, useState, useEffect } from 'react';
import { SafeAreaView, ScrollView, StatusBar, StyleSheet, Text, TextInput, TouchableOpacity, View, Switch } from 'react-native';

const C = {
  navy: '#102a34',
  mint: '#5ce1b1',
  bg: '#f6faf8',
  ink: '#17252b',
  muted: '#748487',
  white: '#fff',
  line: '#e4ece9',
  green: '#238965',
  orange: '#ff9661',
  red: '#df6e62',
  purple: '#9275e7',
  blue: '#5a9cdd',
  yellow: '#d6a73d',
  cardDark: '#132f3a'
};

const DEFAULT_SERVER_URL = 'http://192.168.1.70:3300';

const incidents = [
  ['Unusual login attempt', 'Admin portal', 'High', C.red, '⌁'],
  ['Outdated dependency', 'api-prod-01', 'High', C.orange, '◌'],
  ['Public storage bucket', 'media-assets', 'Medium', C.yellow, '⌂']
];

const contacts = ['Maya Chen', 'Jordan Patel', 'Priya Shah'];

const Button = ({ children, onPress, style }) => (
  <TouchableOpacity onPress={onPress} style={style} activeOpacity={0.75}>
    {children}
  </TouchableOpacity>
);

function Header({ notify }) {
  return (
    <View style={styles.header}>
      <View>
        <Text style={styles.kicker}>OMNIRUSH LABS</Text>
        <Text style={styles.brand}>CyberSecuria</Text>
      </View>
      <Button onPress={() => notify('5-Layer Privacy Fortress is fully active.')} style={styles.bell}>
        <Text style={styles.bellText}>🛡️</Text>
      </Button>
    </View>
  );
}

// ==========================================
// MOBILE GEOIP CHAMELEON & 5-LAYER TAB
// ==========================================
function ChameleonScreen({ notify }) {
  const [status, setStatus] = useState(null);
  const [serverUrl, setServerUrl] = useState(DEFAULT_SERVER_URL);
  const [loading, setLoading] = useState(false);

  const fetchStatus = async () => {
    try {
      const res = await fetch(`${serverUrl}/api/status`);
      if (res.ok) {
        const data = await res.json();
        setStatus(data);
      }
    } catch (e) {}
  };

  useEffect(() => {
    fetchStatus();
    const timer = setInterval(fetchStatus, 2000);
    return () => clearInterval(timer);
  }, [serverUrl]);

  const rotateNow = async () => {
    setLoading(true);
    try {
      await fetch(`${serverUrl}/api/rotate`, { method: 'POST' });
      notify('Rotated IP and Location now!');
      fetchStatus();
    } catch (e) {
      notify('Failed to connect to laptop server.');
    }
    setLoading(false);
  };

  const selectNode = async (nodeId) => {
    try {
      await fetch(`${serverUrl}/api/select-node`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ nodeId })
      });
      notify(`Switched to node: ${nodeId}`);
      fetchStatus();
    } catch (e) {}
  };

  const toggleShield = async (val) => {
    try {
      await fetch(`${serverUrl}/api/shield`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ active: val })
      });
      notify(val ? 'Windows Proxy Shield: ON' : 'Windows Proxy Shield: OFF');
      fetchStatus();
    } catch (e) {}
  };

  const formatTimer = (secs) => {
    if (secs === undefined) return '05:00';
    const m = Math.floor(secs / 60);
    const s = secs % 60;
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  };

  const currentNode = status ? status.currentNode : { flag: '🌐', city: 'Connecting...', country: '', lat: 0, lon: 0, virtualIp: '127.0.0.1' };

  return (
    <View>
      <Text style={styles.h1}>GeoIP Chameleon</Text>
      <Text style={styles.sub}>Mobile Remote Controller for Laptop & Network Security</Text>

      {/* Main Status Card */}
      <View style={styles.chameleonCard}>
        <View style={styles.rowBetween}>
          <Text style={styles.kickerMint}>ROTATION COUNTDOWN</Text>
          <Text style={styles.timerBadge}>⏱️ {formatTimer(status ? status.countdownSeconds : 300)}</Text>
        </View>

        <Text style={styles.cityBig}>{currentNode.flag} {currentNode.city}</Text>
        <Text style={styles.countrySub}>{currentNode.country}</Text>
        <Text style={styles.coordsText}>Lat: {Number(currentNode.lat).toFixed(4)}, Lon: {Number(currentNode.lon).toFixed(4)}</Text>

        <View style={styles.ipBox}>
          <Text style={styles.kickerMint}>VERIFIED VIRTUAL IP</Text>
          <Text style={styles.ipValue}>{currentNode.virtualIp}</Text>
        </View>

        <View style={styles.shieldRow}>
          <View>
            <Text style={{ color: C.white, fontWeight: '700', fontSize: 13 }}>Proxy Shield</Text>
            <Text style={{ color: C.muted, fontSize: 10 }}>Routes all laptop traffic</Text>
          </View>
          <Switch
            value={status ? status.shieldActive : false}
            onValueChange={toggleShield}
            trackColor={{ false: '#3b5a63', true: C.mint }}
            thumbColor={C.white}
          />
        </View>

        <Button onPress={rotateNow} style={styles.rotateBtn}>
          <Text style={styles.rotateBtnText}>{loading ? 'Rotating...' : '⚡ Rotate IP & Location Now'}</Text>
        </Button>
      </View>

      {/* 5-Layer Privacy Fortress Indicators */}
      <Text style={[styles.sectionTitle, { marginTop: 15 }]}>🛡️ 5-Layer Privacy Fortress</Text>
      <View style={styles.card}>
        <View style={styles.layerRow}>
          <Text style={styles.layerLabel}>Layer 1: Wi-Fi MAC Address</Text>
          <Text style={styles.layerGood}>● Randomized</Text>
        </View>
        <View style={styles.layerRow}>
          <Text style={styles.layerLabel}>Layer 2: Windows Advertising ID</Text>
          <Text style={styles.layerGood}>● Zeroed & Blocked</Text>
        </View>
        <View style={styles.layerRow}>
          <Text style={styles.layerLabel}>Layer 3: Network & Zero-DNS-Leak</Text>
          <Text style={styles.layerGood}>● Multi-Hop Active</Text>
        </View>
        <View style={styles.layerRow}>
          <Text style={styles.layerLabel}>Layer 4: Canvas, WebGL & GPS</Text>
          <Text style={styles.layerGood}>● Pixel Noise Active</Text>
        </View>
      </View>

      {/* Jurisdiction Node Grid */}
      <Text style={styles.sectionTitle}>Global Jurisdictions</Text>
      <View style={styles.nodeGrid}>
        {(status ? status.allNodes : []).map((node) => {
          const isActive = status && status.currentNode && status.currentNode.id === node.id;
          return (
            <Button
              key={node.id}
              onPress={() => selectNode(node.id)}
              style={[styles.nodeCard, isActive && styles.nodeCardActive]}
            >
              <Text style={styles.nodeFlag}>{node.flag}</Text>
              <Text style={styles.nodeCity}>{node.city}</Text>
              <Text style={styles.nodeIp}>{node.virtualIp}</Text>
            </Button>
          );
        })}
      </View>
    </View>
  );
}

function Home({ navigate, notify, scan }) {
  return (
    <>
      <Text style={styles.h1}>Cockpit <Text style={{ color: C.mint }}>✦</Text></Text>
      <Text style={styles.sub}>Digital safety and GeoIP Chameleon cockpit.</Text>
      <Button onPress={() => navigate('Chameleon')} style={styles.alert}>
        <Text style={styles.alertIcon}>📍</Text>
        <View style={{ flex: 1 }}>
          <Text style={styles.title}>GeoIP Chameleon Active</Text>
          <Text style={styles.detail}>Rotating IP & GPS every 5 minutes automatically.</Text>
        </View>
        <Text style={styles.arrow}>→</Text>
      </Button>
      <View style={styles.score}>
        <View>
          <Text style={styles.kicker}>SECURITY SCORE</Text>
          <Text style={styles.scoreNumber}>96 <Text style={styles.out}>/ 100</Text></Text>
          <Text style={styles.muted}>5-Layer Privacy Fortress Active</Text>
        </View>
        <View style={styles.ring}>
          <Text style={styles.ringNumber}>96</Text>
          <Text style={styles.ringLabel}>FORTRESS</Text>
        </View>
      </View>
      <Button onPress={() => navigate('Chameleon')} style={styles.primary}>
        <Text style={styles.primaryText}>🌐 Open GeoIP Chameleon</Text>
      </Button>
    </>
  );
}

function Profile({ notify }) {
  return (
    <>
      <Text style={styles.h1}>Prince Settings</Text>
      <Text style={styles.sub}>OmniRush Chameleon Administrator</Text>
      <View style={styles.card}>
        <Text style={styles.sectionTitle}>Laptop Connection</Text>
        <Text style={styles.preference}>Laptop Server: <Text style={styles.good}>http://192.168.1.70:3300</Text></Text>
        <Text style={styles.preference}>Proxy Port: <Text style={styles.good}>8899 (Smart Tunnel)</Text></Text>
        <Text style={styles.preference}>Rotation Cycle: <Text style={styles.good}>5 Minutes</Text></Text>
      </View>
    </>
  );
}

export default function App() {
  const [page, setPage] = useState('Chameleon');
  const [notice, setNotice] = useState('');
  const notify = (text) => {
    setNotice(text);
    setTimeout(() => setNotice(''), 2400);
  };

  const pageContent = useMemo(() => ({
    Chameleon: <ChameleonScreen notify={notify} />,
    Home: <Home navigate={setPage} notify={notify} />,
    Profile: <Profile notify={notify} />
  })[page], [page]);

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar barStyle="light-content" backgroundColor={C.navy} />
      <View style={styles.app}>
        <Header notify={notify} />
        <ScrollView contentContainerStyle={styles.scroll}>
          {pageContent}
          <View style={{ height: 40 }} />
        </ScrollView>
        {notice ? <View style={styles.toast}><Text style={styles.toastText}>{notice}</Text></View> : null}
        <View style={styles.tabBar}>
          <Tab label="Chameleon" icon="📍" active={page === 'Chameleon'} onPress={() => setPage('Chameleon')} />
          <Tab label="Home" icon="◈" active={page === 'Home'} onPress={() => setPage('Home')} />
          <Tab label="Settings" icon="⚙" active={page === 'Profile'} onPress={() => setPage('Profile')} />
        </View>
      </View>
    </SafeAreaView>
  );
}

function Tab({ label, icon, active, onPress }) {
  return (
    <Button onPress={onPress} style={styles.tab}>
      <Text style={[styles.tabIcon, active && { color: C.mint }]}>{icon}</Text>
      <Text style={[styles.tabLabel, active && { color: C.mint }]}>{label}</Text>
    </Button>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: C.navy },
  app: { flex: 1, backgroundColor: C.bg },
  header: { backgroundColor: C.navy, paddingHorizontal: 20, paddingTop: 16, paddingBottom: 18, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  kicker: { color: '#91aaa9', fontSize: 9, fontWeight: '700', letterSpacing: 1.1 },
  kickerMint: { color: C.mint, fontSize: 9, fontWeight: '700', letterSpacing: 1.1 },
  brand: { color: C.white, fontSize: 20, fontWeight: '700', marginTop: 4 },
  bell: { width: 38, height: 38, borderRadius: 20, borderWidth: 1, borderColor: '#35606a', alignItems: 'center', justifyContent: 'center' },
  bellText: { fontSize: 18 },
  scroll: { padding: 20 },
  h1: { color: C.ink, fontSize: 26, fontWeight: '700', letterSpacing: -0.8 },
  sub: { color: C.muted, fontSize: 12, marginTop: 4, marginBottom: 16 },
  alert: { backgroundColor: '#fff8f4', borderWidth: 1, borderColor: '#f4d8c9', padding: 13, borderRadius: 10, flexDirection: 'row', alignItems: 'center', marginBottom: 14 },
  alertIcon: { fontSize: 20, marginRight: 10 },
  title: { color: '#31464b', fontSize: 12, fontWeight: '700' },
  detail: { color: '#8f9e9f', fontSize: 10, marginTop: 4, lineHeight: 15 },
  arrow: { color: C.green, fontSize: 20 },
  score: { backgroundColor: C.navy, padding: 18, borderRadius: 12, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 },
  scoreNumber: { color: C.white, fontSize: 44, fontWeight: '700', marginTop: 4 },
  out: { color: '#90a6a7', fontSize: 12 },
  muted: { color: '#8f9e9f', fontSize: 10, marginTop: 4 },
  ring: { width: 72, height: 72, borderRadius: 40, borderWidth: 6, borderColor: C.mint, alignItems: 'center', justifyContent: 'center' },
  ringNumber: { color: C.white, fontSize: 20, fontWeight: '700' },
  ringLabel: { color: C.mint, fontSize: 7, fontWeight: '700' },
  sectionTitle: { color: C.ink, fontSize: 15, fontWeight: '700', marginTop: 12, marginBottom: 8 },
  card: { backgroundColor: C.white, borderRadius: 10, borderWidth: 1, borderColor: C.line, padding: 14, marginBottom: 16 },
  primary: { backgroundColor: C.navy, padding: 14, borderRadius: 8, alignItems: 'center', marginTop: 10 },
  primaryText: { color: C.white, fontWeight: '700', fontSize: 12 },
  preference: { color: C.muted, fontSize: 12, paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: C.line },
  good: { color: C.green, fontWeight: '700' },
  toast: { position: 'absolute', left: 20, right: 20, bottom: 84, backgroundColor: C.navy, borderRadius: 9, padding: 14, elevation: 6 },
  toastText: { color: C.white, textAlign: 'center', fontSize: 12 },
  tabBar: { height: 68, backgroundColor: C.navy, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-around' },
  tab: { width: 75, alignItems: 'center' },
  tabIcon: { color: '#83a0a0', fontSize: 18 },
  tabLabel: { color: '#83a0a0', fontSize: 9, marginTop: 4, fontWeight: '600' },
  
  // Chameleon Screen Styles
  chameleonCard: { backgroundColor: C.navy, borderRadius: 14, padding: 18, marginBottom: 14 },
  rowBetween: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  timerBadge: { color: C.mint, backgroundColor: '#143e49', paddingVertical: 4, paddingHorizontal: 10, borderRadius: 6, fontWeight: '700', fontSize: 12 },
  cityBig: { color: C.white, fontSize: 24, fontWeight: '700', marginTop: 10 },
  countrySub: { color: C.muted, fontSize: 13, marginTop: 2 },
  coordsText: { color: C.mint, fontFamily: 'monospace', fontSize: 11, marginTop: 4 },
  ipBox: { backgroundColor: '#132f3a', borderRadius: 8, padding: 10, marginTop: 12 },
  ipValue: { color: C.white, fontSize: 16, fontWeight: '700', marginTop: 2, fontFamily: 'monospace' },
  shieldRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: 14, borderTopWidth: 1, borderTopColor: '#1d4351', paddingTop: 12 },
  rotateBtn: { backgroundColor: C.mint, padding: 12, borderRadius: 8, alignItems: 'center', marginTop: 14 },
  rotateBtnText: { color: C.navy, fontWeight: '700', fontSize: 13 },
  layerRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 8, borderBottomWidth: 1, borderBottomColor: '#f0f4f3' },
  layerLabel: { color: '#31464b', fontSize: 11, fontWeight: '600' },
  layerGood: { color: C.green, fontSize: 11, fontWeight: '700' },
  nodeGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginBottom: 16 },
  nodeCard: { width: '48%', backgroundColor: C.white, borderWidth: 1, borderColor: C.line, borderRadius: 8, padding: 12 },
  nodeCardActive: { borderColor: C.mint, backgroundColor: '#effbf6' },
  nodeFlag: { fontSize: 24, marginBottom: 4 },
  nodeCity: { fontSize: 13, fontWeight: '700', color: C.ink },
  nodeIp: { fontSize: 10, color: C.muted, marginTop: 2, fontFamily: 'monospace' }
});
