#!/usr/bin/env python3
"""Resolve the real .deb dependency closure used by the minimal bootstrap."""
import argparse,pathlib,re,subprocess
def field(deb,name): return subprocess.check_output(['dpkg-deb','-f',str(deb),name],text=True,stderr=subprocess.DEVNULL).strip()
parser=argparse.ArgumentParser(); parser.add_argument('debs',type=pathlib.Path); parser.add_argument('--seed',action='append',default=[]); args=parser.parse_args()
by_name={field(deb,'Package'):deb for deb in args.debs.glob('*.deb')}; selected=set(); pending=list(args.seed)
while pending:
    name=pending.pop()
    if name in selected: continue
    deb=by_name.get(name)
    if not deb: raise SystemExit(f'bootstrap dependency missing: {name}')
    selected.add(name)
    try: depends=field(deb,'Depends')
    except subprocess.CalledProcessError: depends=''
    for group in filter(None,(x.strip() for x in depends.split(','))):
        alternatives=[re.split(r'\s*\(',x.strip(),maxsplit=1)[0].strip() for x in group.split('|')]
        available=next((x for x in alternatives if x in by_name),None)
        if available: pending.append(available)
for name in sorted(selected): print(by_name[name])
