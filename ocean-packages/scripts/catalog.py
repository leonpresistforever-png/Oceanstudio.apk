#!/usr/bin/env python3
"""Validate and query the versioned Ocean package catalogue."""
import argparse, json, pathlib
ROOT = pathlib.Path(__file__).resolve().parents[1]
def load():
    data=json.loads((ROOT/'catalog.json').read_text()); target=data['target']
    actual=(target['applicationId'],target['architecture'],target['androidApi'],target['prefix'])
    expected=('studio.ocean.app','aarch64',28,'/data/data/studio.ocean.app/files/usr')
    if actual != expected: raise SystemExit(f'catalog target mismatch: {actual!r}')
    for phase,names in data['phases'].items():
        if len(names)!=len(set(names)): raise SystemExit(f'duplicate package in phase {phase}')
        for name in names:
            item=data['packages'].get(name)
            if not item or item.get('phase')!=phase: raise SystemExit(f'invalid phase mapping: {phase}/{name}')
            if not item.get('recipe','').startswith('packages/'): raise SystemExit(f'invalid recipe path: {name}')
    return data
def main():
    parser=argparse.ArgumentParser(); parser.add_argument('command',choices=('validate','roots','table')); parser.add_argument('--through',default='foundation'); args=parser.parse_args()
    data=load(); phases=list(data['phases'])
    if args.through not in phases: raise SystemExit(f'unknown phase: {args.through}')
    names=[name for phase in phases[:phases.index(args.through)+1] for name in data['phases'][phase]]
    if args.command=='roots': print('\n'.join(names))
    elif args.command=='table':
        for name in names:
            item=data['packages'][name]; print(f"{item['phase']}\t{name}\t{item['recipe']}\t{','.join(item.get('commands',[]))}")
    else: print(f"Ocean catalogue valid: {len(data['packages'])} packages, phases={','.join(phases)}")
if __name__=='__main__': main()
