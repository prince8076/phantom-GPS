# UI & Design System Specification

## Color Palette (:root tokens)
- `--ink`: `#17252b` (Primary text)
- `--muted`: `#708087` (Secondary text)
- `--line`: `#e6eceb` (Borders and dividers)
- `--surface`: `#fff` (Card background)
- `--canvas`: `#f7faf9` (App background)
- `--mint`: `#5ce1b1` (Accent color / success state)
- `--mint-dark`: `#178d68` (Deep accent)
- `--navy`: `#102b35` (Sidebar & terminal background)
- `--orange`: `#ff9661` (Warning / alert indicator)
- `--purple`: `#9374e8` (Secondary badge indicator)
- `--yellow`: `#f4c95d` (Notice indicator)

## Typography
- Font Family: `'Space Grotesk', system-ui, sans-serif`
- Mono Font: `'DM Mono', monospace` (used for terminal logs, IP addresses, and timers)

## Layout Components
- **Sidebar**: Fixed 250px width with brand logo, workspace switcher, navigation links, and user profile.
- **Topbar**: Breadcrumb trail, quick action buttons, and profile chip.
- **Metric Grid**: 4-column responsive card layout displaying timers, virtual IPs, spoofed locations, and REA status.
- **Activity Panel / Proxy Grid**: Interactive node selection buttons with active status indicators.
- **Terminal Box**: Dark-themed monospace terminal simulating real-time REA socket hooks.
