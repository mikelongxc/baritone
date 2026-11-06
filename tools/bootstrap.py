#!/usr/bin/env python3
"""Download only hash-locked upstream compile dependencies. Does not touch Minecraft profiles."""
import concurrent.futures, hashlib, json, pathlib, subprocess, zipfile
root = pathlib.Path(__file__).resolve().parents[1]
dest = root / 'deps'
dest.mkdir(exist_ok=True)
def fetch(entry):
    target = dest / entry['name']
    algorithm = 'sha256' if 'sha256' in entry else 'sha1'
    expected = entry[algorithm]
    if target.exists() and hashlib.new(algorithm, target.read_bytes()).hexdigest() == expected:
        return
    data = subprocess.check_output(['curl', '--fail', '--silent', '--show-error', '--location', '--retry', '2', '--max-time', '120', entry['url']])
    if hashlib.new(algorithm, data).hexdigest() != expected:
        raise RuntimeError('Dependency hash mismatch: ' + entry['name'])
    temporary = target.with_suffix('.tmp')
    temporary.write_bytes(data)
    temporary.replace(target)
with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
    list(pool.map(fetch, json.loads((root / 'tools/dependencies.json').read_text())))
with zipfile.ZipFile(dest / 'meteor.jar') as jar:
    name = 'META-INF/jars/orbit-0.2.4.jar'
    (dest / 'orbit-0.2.4.jar').write_bytes(jar.read(name))
print('Verified Minecraft 26.2, Meteor and compile libraries; Baritone is built from source.')
