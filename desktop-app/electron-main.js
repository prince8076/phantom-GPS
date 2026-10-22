/**
 * Phantom VPN — minimal Electron shell for the PC client.
 *
 * Turns the hub's pc-client.html into a real desktop window.
 *
 * Run:
 *   1. Start the hub:      cd desktop-app && npm start        (http://localhost:3300)
 *   2. Install Electron:   cd desktop-app && npm i -D electron
 *   3. Launch the app:     npx electron electron-main.js
 *
 * Point at a remote hub with:  PHANTOM_HUB_URL=http://<host>:3300/pc-client.html
 * Package an installer later with electron-builder / electron-forge.
 */
const { app, BrowserWindow } = require('electron');

const HUB_URL = process.env.PHANTOM_HUB_URL || 'http://localhost:3300/pc-client.html';

function createWindow() {
  const win = new BrowserWindow({
    width: 1280,
    height: 800,
    minWidth: 1024,
    minHeight: 640,
    backgroundColor: '#0a0a10',
    title: 'Phantom VPN',
    autoHideMenuBar: true,
  });
  win.loadURL(HUB_URL);
}

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) createWindow();
});
