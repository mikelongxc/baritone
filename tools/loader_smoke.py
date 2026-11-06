#!/usr/bin/env python3
"""Isolated Fabric/Mixin transform test. Does not invoke Minecraft.main or a live client."""
import argparse, json, os, pathlib, shutil, subprocess
p=argparse.ArgumentParser();p.add_argument('--java-home',required=True);p.add_argument('--work-dir',required=True)
p.add_argument('--mod-jar');p.add_argument('--loader-jar')
p.add_argument('--extra-mod',action='append',default=[])
p.add_argument('--xaero-check',action='store_true')
a=p.parse_args();root=pathlib.Path(__file__).resolve().parents[1];work=pathlib.Path(a.work_dir).resolve()
if work.exists() and any(work.iterdir()):
    raise SystemExit('Use a new empty smoke-test directory; never an existing Minecraft installation.')
work.mkdir(parents=True,exist_ok=True);(work/'mods').mkdir();(work/'classes').mkdir()
for name in ['meteor.jar']:shutil.copy2(root/'deps'/name,work/'mods'/name)
shutil.copy2(pathlib.Path(a.mod_jar).resolve() if a.mod_jar else root/'build/libs/baritone-autostore-26.2-autostore-2.6.7.jar',work/'mods/baritone-autostore.jar')
for mod in a.extra_mod:
    path=pathlib.Path(mod).resolve();shutil.copy2(path,work/'mods'/path.name)
loader=pathlib.Path(a.loader_jar).resolve() if a.loader_jar else root/'deps/fabric-loader-0.19.3.jar'
java=pathlib.Path(a.java_home)/'bin'
subprocess.run([str(java/'javac'),'-cp',str(loader),'-d',str(work/'classes'),str(root/'tools/LoaderSmoke.java')],check=True)
exclude={'meteor.jar','baritone.jar','orbit-0.2.4.jar','junit-platform-console-standalone-1.12.2.jar','nether-pathfinder-1.6.jar','mixinextras-fabric-0.5.4.jar'}
cp=os.pathsep.join([str(work/'classes')]+[str(root/'deps'/e['name']) for e in json.loads((root/'tools/dependencies.json').read_text()) if e['name'] not in exclude and not e['name'].startswith('fabric-loader-')]+[str(loader)])
result = subprocess.run([str(java/'java'),'-Xmx1G','-Djava.awt.headless=true','-Dmixin.debug.export=true','-Dautostore.smoke.xaero='+str(a.xaero_check).lower(),'-cp',cp,'LoaderSmoke','--gameDir',str(work),'--version','26.2'],cwd=work,timeout=120)
raise SystemExit(result.returncode)
