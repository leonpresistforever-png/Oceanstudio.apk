#!/usr/bin/env python3
"""Exercise the published server with Android platform selection and a fresh HOME."""
import argparse
import http.cookiejar
import json
import os
import pathlib
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument('--gateway-root', type=pathlib.Path, required=True)
parser.add_argument('--gateway-command', type=pathlib.Path, required=True)
args = parser.parse_args()
with tempfile.TemporaryDirectory(prefix='ocean-android-gateway-') as directory:
    work = pathlib.Path(directory)
    (work / 'home').mkdir()
    preload = work / 'android-platform.cjs'
    preload.write_text("Object.defineProperty(process, 'platform', {value: 'android'});\n")
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0))
        port = listener.getsockname()[1]
    environment = {**os.environ, 'HOME': str(work / 'home'),
                   'OCEAN_GATEWAY_CODE': str(args.gateway_command.resolve().parent),
                   'OCEAN_GATEWAY_DATA': str(work / 'state'), 'OCEAN_GATEWAY_PORT': str(port)}
    subprocess.run(['bash', str(args.gateway_command.resolve()), 'install'], env=environment, check=True)
    subprocess.run(['bash', str(args.gateway_command.resolve()), 'install'], env=environment, check=True)
    environment['NODE_OPTIONS'] = '--require=' + str(preload)
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}),
                                        urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    def request(path, body=None):
        payload = None if body is None else json.dumps(body).encode()
        try:
            with opener.open(urllib.request.Request(f'http://127.0.0.1:{port}' + path, data=payload,
                             headers={'Content-Type': 'application/json'}), timeout=20) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            detail = json.load(error).get('error', {})
            raise RuntimeError(f'Gateway HTTP {error.code}: {detail.get("message", "request failed")}') from error
    with (work / 'server.log').open('w') as log:
        server = subprocess.Popen(['bash', str(args.gateway_command.resolve()), 'serve'], env=environment,
                                  stdout=log, stderr=subprocess.STDOUT)
        try:
            password = dict(line.split('=', 1) for line in (work / 'state/.env').read_text().splitlines()
                            if '=' in line)['INITIAL_PASSWORD']
            deadline = time.monotonic() + 90
            while True:
                assert server.poll() is None, 'Android gateway process exited'
                try:
                    request('/api/auth/login', {'password': password})
                    break
                except (urllib.error.URLError, urllib.error.HTTPError):
                    if time.monotonic() > deadline: raise
                    time.sleep(.2)
            # This request returned a bare HTTP 500 on the unprepared dependency.
            assert request('/api/providers?provider=antigravity')['connections'] == []
            for provider, callback in [('antigravity', 'http://127.0.0.1:44444/callback'),
                                       ('codex', 'http://localhost:1455/auth/callback')]:
                auth = request('/api/oauth/' + provider + '/authorize?redirect_uri=' +
                               urllib.parse.quote(callback, safe=''))
                assert auth['redirectUri'] == callback
                assert auth['authUrl'].startswith('https://') and auth['state']
            assert request('/api/providers?provider=codex')['connections'] == []
            assert request('/health')['engine'] == 'ocean'
            assert not (work / 'state/node_modules').exists()
            print('PASS: actual Android-selected gateway account routes and authorization')
        except Exception:
            log.flush()
            print((work / 'server.log').read_text(errors='replace')[-3000:])
            raise
        finally:
            server.terminate()
            try: server.wait(timeout=10)
            except subprocess.TimeoutExpired: server.kill(); server.wait()
