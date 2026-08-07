#!/usr/bin/env node
/** Standalone Ocean proxy — run from terminal on Electron/APK when IPC unavailable */
import http from 'http';
import https from 'https';
import os from 'os';
import { URL } from 'url';

const PORT = Number(process.env.OCEAN_PROXY_PORT ?? process.argv[2] ?? 20128);
const HOST = process.env.OCEAN_PROXY_HOST ?? '0.0.0.0';

function getLanIp() {
  for (const nets of Object.values(os.networkInterfaces())) {
    for (const net of nets ?? []) {
      if (net.family === 'IPv4' && !net.internal) return net.address;
    }
  }
  return '127.0.0.1';
}

const pendingOAuth = new Map();

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url ?? '/', `http://${req.headers.host}`);
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  if (req.method === 'OPTIONS') { res.writeHead(204); res.end(); return; }

  if (url.pathname === '/ocean-proxy/status' || url.pathname === '/health') {
    const lanIp = getLanIp();
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      running: true, port: PORT, host: HOST,
      url: `http://localhost:${PORT}`,
      lanUrl: `http://${lanIp}:${PORT}`,
      oauthBaseUrl: `http://localhost:${PORT}/oauth`,
      message: `Ocean proxy ready — localhost:${PORT} | LAN:${lanIp}:${PORT}`,
    }));
    return;
  }

  const oauthMatch = url.pathname.match(/^\/oauth\/([^/]+)\/callback$/);
  if (oauthMatch && req.method === 'GET') {
    const code = url.searchParams.get('code');
    const state = url.searchParams.get('state');
    const error = url.searchParams.get('error');
    if (error) {
      res.writeHead(200, { 'Content-Type': 'text/html' });
      res.end(`<html><body><h2>Failed: ${error}</h2></body></html>`);
      return;
    }
    if (code && state) {
      res.writeHead(200, { 'Content-Type': 'text/html' });
      res.end(`<html><body style="font-family:sans-serif;text-align:center;padding:40px;background:#FAFAF9">
        <h2 style="color:#16a34a">✓ Connected to Ocean.studio</h2>
        <p>Return to the app.</p></body></html>`);
      return;
    }
  }

  const proxyMatch = url.pathname.match(/^\/proxy\/([^/]+)(\/.*)?$/);
  if (proxyMatch) {
    const targetHost = decodeURIComponent(proxyMatch[1]);
    const targetPath = proxyMatch[2] ?? '/';
    const fullUrl = `${targetHost.startsWith('http') ? targetHost : `https://${targetHost}`}${targetPath}`;
    const parsed = new URL(fullUrl);
    const transport = parsed.protocol === 'https:' ? https : http;
    const proxyReq = transport.request(fullUrl, { method: req.method, headers: { ...req.headers, host: parsed.host } }, (proxyRes) => {
      res.writeHead(proxyRes.statusCode ?? 502, proxyRes.headers);
      proxyRes.pipe(res);
    });
    proxyReq.on('error', () => { res.writeHead(502); res.end('Upstream error'); });
    req.pipe(proxyReq);
    return;
  }

  res.writeHead(404, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ error: 'Not found' }));
});

server.listen(PORT, HOST, () => {
  const lanIp = getLanIp();
  console.log(`Ocean proxy listening on http://localhost:${PORT} | LAN http://${lanIp}:${PORT}`);
});
