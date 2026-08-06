# Ocean.studio

Hardware-level coding agent workspace built with Electron, React, TypeScript, and Python.

## Features

- **Premium Authentication** — Email/password, Google Sign-In, forgot password (Firebase)
- **Workspace Launcher** — Template selection, language, agent mode configuration
- **IDE Layout** — Left sidebar explorer, center editor/preview/codebase, right agent panel
- **Real Terminal** — node-pty powered Linux shell with port auto-detection
- **Live Preview** — Embedded iframe preview for any active port
- **Agent Chat** — Cursor-style expandable logs, file/image upload, pause control
- **Agent Modes** — Review-driven, auto (cautious), bypass (independent)

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Desktop | Electron 35 |
| Frontend | React 19 + TypeScript + Vite |
| Editor | Monaco Editor |
| Terminal | xterm.js + node-pty |
| Auth | Firebase Authentication |
| Agent | Python backend (spawned from Electron) |
| State | Zustand |

## Getting Started

### Prerequisites

- Node.js 20+
- Python 3.10+
- Firebase project (for authentication)

### Install

```bash
npm install
```

### Firebase Setup

1. Create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com)
2. Enable Authentication → Email/Password and Google Sign-In
3. Create a Web app and copy the config
4. Copy `.env.example` to `.env` and fill in Firebase values

### Development

```bash
# Web UI only
npm run dev

# Full Electron app
npm run electron:dev
```

### Build

```bash
npm run electron:build
```

## Agent Documentation

- [`agent/agent.md`](agent/agent.md) — Agent capabilities and architecture
- [`agent/skill.md`](agent/skill.md) — Agent skill instructions for AI integration

## Platform Roadmap

- [x] Desktop (Electron) — current
- [ ] Web (Firebase Hosting)
- [x] Mobile (APK with native Linux terminal via proot) — in progress

## License

Private — Ocean.studio
