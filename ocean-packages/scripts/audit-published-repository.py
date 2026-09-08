#!/usr/bin/env python3
"""Audit a published Ocean APT tree without trusting its Packages index."""
from __future__ import annotations
import argparse, hashlib, json, re, subprocess, sys
from collections import Counter, defaultdict
from pathlib import Path
REQUIRED_FIELDS=("Package","Version","Architecture","Filename","Size","SHA256")
FOREIGN_NAMES=("com.termux","termux-","termux_")
def paragraphs(path):
 out=[]
 for raw in path.read_text(errors="replace").strip().split("\n\n"):
  item={}; key=""
  for line in raw.splitlines():
   if line[:1].isspace() and key:item[key]+="\n"+line
   elif ": " in line:key,value=line.split(": ",1);item[key]=value
  if item:out.append(item)
 return out
def dependency_names(value):
 groups=[]
 for group in re.split(r",\s*",value):
  alternatives=[]
  for alternative in group.split("|"):
   name=re.split(r"[\s(]",alternative.strip(),maxsplit=1)[0].split(":",1)[0]
   if name:alternatives.append(name)
  if alternatives:groups.append(alternatives)
 return groups
def package_listing(deb):
 output=subprocess.run(["dpkg-deb","-c",str(deb)],check=True,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT).stdout
 count=0; foreign=[]; bad_modes=[]
 for line in output.splitlines():
  parts=line.split(maxsplit=5)
  if len(parts)!=6:continue
  mode,path=parts[0],parts[5]
  if mode[0]!="d":count+=1
  if any(marker in path.lower() for marker in FOREIGN_NAMES):foreign.append(path)
  if "/files/usr/bin/" in path and mode[0]=="-" and "x" not in mode:bad_modes.append(path)
 return count,foreign,bad_modes
def main():
 p=argparse.ArgumentParser();p.add_argument("repository",type=Path);p.add_argument("--expected-fingerprint",default="");p.add_argument("--json",type=Path);a=p.parse_args()
 root=a.repository.resolve();apt=root/"apt";entries=paragraphs(apt/"dists/stable/main/binary-aarch64/Packages");pool=apt/"pool/main"
 names=Counter(e.get("Package","") for e in entries);available=set(names);indexed=set();missing_fields=[];missing_files=[];checksum_errors=[];payloadless=[];empty_non_meta=[];foreign_paths={};bad_modes={};archive_errors=[]
 for e in entries:
  if any(field not in e for field in REQUIRED_FIELDS):missing_fields.append(e.get("Package","<unknown>"))
  filename=e.get("Filename","")
  if not filename:continue
  # APT Filename values are relative to the repository's `apt/` root.
  deb=apt/filename;indexed.add(deb.resolve())
  if not deb.is_file():missing_files.append(filename);continue
  actual_sha=hashlib.sha256(deb.read_bytes()).hexdigest()
  if str(deb.stat().st_size)!=e.get("Size") or actual_sha!=e.get("SHA256"):checksum_errors.append(filename)
  try:
   count,residue,nonexec=package_listing(deb)
   if count==0:
    payloadless.append(deb.name)
    if not e.get("Depends"):empty_non_meta.append(deb.name)
   if residue:foreign_paths[deb.name]=residue
   if nonexec:bad_modes[deb.name]=nonexec
  except (OSError,subprocess.CalledProcessError) as exc:archive_errors.append(f"{deb.name}: {exc}")
 missing_dependencies=defaultdict(list)
 for e in entries:
  for alternatives in dependency_names(e.get("Depends","")):
   if not any(name in available for name in alternatives):missing_dependencies[e.get("Package","<unknown>")].append(" | ".join(alternatives))
 unindexed=sorted(str(x.relative_to(root)) for x in pool.glob("*.deb") if x.resolve() not in indexed)
 key=apt/"ocean.gpg";fingerprint=""
 if key.is_file():
  shown=subprocess.run(["gpg","--batch","--show-keys","--with-colons",str(key)],text=True,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL).stdout
  fingerprint=next((line.split(":")[9] for line in shown.splitlines() if line.startswith("fpr:")),"")
 source_files=[x.name for x in root.iterdir() if x.name not in {".git",".gitignore","apt"}]
 result={"indexedEntries":len(entries),"uniquePackageNames":len(available),"duplicatePackageNames":{n:c for n,c in names.items() if n and c>1},"missingFields":missing_fields,"missingFiles":missing_files,"checksumErrors":checksum_errors,"unindexedDebs":unindexed,"payloadlessPackages":payloadless,"emptyNonMetaPackages":empty_non_meta,"foreignIdentityPaths":foreign_paths,"nonExecutableBinFiles":bad_modes,"unresolvedDependencies":dict(missing_dependencies),"archiveErrors":archive_errors,"repositoryFingerprint":fingerprint,"expectedFingerprint":a.expected_fingerprint,"fingerprintMatches":not a.expected_fingerprint or fingerprint==a.expected_fingerprint,"provenanceFiles":source_files}
 rendered=json.dumps(result,indent=2,sort_keys=True)
 if a.json:a.json.write_text(rendered+"\n")
 print(rendered)
 critical=any((missing_fields,missing_files,checksum_errors,empty_non_meta,foreign_paths,missing_dependencies,archive_errors,unindexed)) or not result["fingerprintMatches"] or not source_files
 return 1 if critical else 0
if __name__=="__main__":sys.exit(main())
