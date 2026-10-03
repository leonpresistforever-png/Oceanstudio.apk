#!/usr/bin/env python3
"""Run actual upstream servers and Ocean's JVM HTTP client; no fabricated inference responses."""
import argparse
import hashlib
import http.cookiejar
import json
import os
import pathlib
import shutil
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
JAVA = ROOT / 'android/app/src/main/java/studio/ocean/app'
parser = argparse.ArgumentParser()
parser.add_argument('--gateway-root', type=pathlib.Path, required=True)
parser.add_argument('--gateway-command', type=pathlib.Path, default=ROOT / 'android/app/src/main/assets/ocean/gateway/ocean-gateway')
parser.add_argument('--llama-server', type=pathlib.Path, required=True)
parser.add_argument('--model', type=pathlib.Path, required=True)
parser.add_argument('--json-jar', type=pathlib.Path, required=True)
args = parser.parse_args()
assert hashlib.sha256(args.model.read_bytes()).hexdigest() == '66967fbece6dbe97886593fdbb73589584927e29119ec31f08090732d1861739'

def port():
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0))
        return listener.getsockname()[1]

opener = urllib.request.build_opener(urllib.request.ProxyHandler({}),
                                   urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
def request(origin, path, body=None, method=None, key=None, anonymous=False):
    headers = {'Content-Type': 'application/json'}
    if key: headers['Authorization'] = 'Bearer ' + key
    payload = None if body is None else json.dumps(body).encode()
    transport = urllib.request.build_opener(urllib.request.ProxyHandler({})) if anonymous else opener
    with transport.open(urllib.request.Request(origin + path, data=payload, method=method, headers=headers), timeout=120) as response:
        return json.load(response)

def ready(origin, path, process):
    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        if process.poll() is not None: raise RuntimeError(f'Server exited: {process.returncode}')
        try: request(origin, path); return
        except (urllib.error.URLError, urllib.error.HTTPError): time.sleep(.2)
    raise RuntimeError('Server readiness timed out')

with tempfile.TemporaryDirectory(prefix='ocean-real-gateway-') as directory:
    work = pathlib.Path(directory)
    gateway_port, llama_port = port(), port()
    environment = {**os.environ, 'OCEAN_GATEWAY_ROOT': str(args.gateway_root.resolve()),
                   'OCEAN_GATEWAY_DATA': str(work / 'state'), 'OCEAN_GATEWAY_PORT': str(gateway_port)}
    subprocess.run(['bash', str(args.gateway_command.resolve()), 'install'], env=environment, check=True)
    gateway_log = (work / 'gateway.log').open('w')
    llama_log = (work / 'llama.log').open('w')
    processes = []
    try:
        llama = subprocess.Popen([str(args.llama_server.resolve()), '-m', str(args.model.resolve()),
                                  '--host', '127.0.0.1', '--port', str(llama_port), '--alias', 'stories',
                                  '--api-key', 'ocean-integration-key', '-c', '512', '--parallel', '1',
                                  '--chat-template', 'chatml'], stdout=llama_log, stderr=subprocess.STDOUT)
        processes.append(llama)
        gateway = subprocess.Popen(['bash', str(args.gateway_command.resolve()), 'serve'], env=environment,
                                   stdout=gateway_log, stderr=subprocess.STDOUT)
        processes.append(gateway)
        local, origin = f'http://127.0.0.1:{llama_port}', f'http://127.0.0.1:{gateway_port}'
        ready(local, '/health', llama)
        ready(origin, '/api/monitoring/health', gateway)
        secret = dict(line.split('=', 1) for line in (work / 'state/.env').read_text().splitlines())['INITIAL_PASSWORD']
        request(origin, '/api/auth/login', {'password': secret})
        try:
            request(origin, '/v1/chat/completions', {'model':'stories','messages':[{'role':'user','content':'Hello'}]}, anonymous=True)
            raise AssertionError('Unauthenticated inference accepted')
        except urllib.error.HTTPError as rejected: assert rejected.code == 401
        node = request(origin, '/api/provider-nodes', {'name':'Ocean llama integration','prefix':'oceanllama',
                       'apiType':'chat','type':'openai-compatible','baseUrl':local+'/v1'})['node']
        accounts = []
        for name, key in [('valid','ocean-integration-key'), ('expired','invalid-integration-key')]:
            response = request(origin, '/api/providers', {'provider':node['id'], 'name':name, 'apiKey':key,
                               'providerSpecificData':{'baseUrl':local+'/v1'}})
            account = response['connection']
            # This negative test deliberately exercises a real 401 and fallback.
            # All test resources are confined to the temporary, isolated gateway database.
            request(origin, '/api/providers/'+account['id'], {'isActive':True}, method='PUT')
            accounts.append(account['id'])
        compiler = ['javac'] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
        subprocess.run(compiler + ['-cp',str(args.json_jar.resolve()),'-d',directory,
                       str(JAVA/'providers/gateway/GatewayClient.java'),str(JAVA/'providers/gateway/GatewayQuota.java'),
                       str(JAVA/'providers/model/QuotaSnapshot.java'),str(JAVA/'mcp/OAuthLoopbackReceiver.java'),
                       str(ROOT/'scripts/tests/GatewayClientIntegrationCheck.java')],check=True)
        classpath = directory + os.pathsep + str(args.json_jar.resolve())
        subprocess.run(['java','-cp',classpath,'GatewayClientIntegrationCheck',str(gateway_port),
                       str(work/'state/.env'),accounts[0],accounts[1],'oceanllama/stories'],check=True)
    except Exception:
        gateway_log.flush(); llama_log.flush()
        for path in (work/'llama.log',work/'gateway.log'):
            print(path.name, path.read_text(errors='replace')[-3000:])
        raise
    finally:
        for process in reversed(processes):
            process.terminate()
            try: process.wait(timeout=15)
            except subprocess.TimeoutExpired: process.kill(); process.wait()
        gateway_log.close(); llama_log.close()
