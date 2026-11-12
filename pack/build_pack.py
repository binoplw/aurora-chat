"""Собирает .mrpack: моды с Modrinth (под 26.1.2 Fabric, с зависимостями) + overrides.

python build_pack.py <out.mrpack> <options.txt> "файл|путь/в/overrides" ...
"""
import json, os, sys, urllib.request, urllib.parse, zipfile

GAME = "26.1.2"
UA = {"User-Agent": "AuroraChat-pack-builder/1.0"}
out_path, options_txt = sys.argv[1:3]
extra = [a.split("|", 1) for a in sys.argv[3:]]
MODS = ["fabric-api", "sodium", "sodium-extra", "reeses-sodium-options", "lithium", "ferrite-core", "immediatelyfast",
        "moreculling", "entityculling", "dynamic-fps", "krypton", "badoptimizations", "fastquit", "modmenu", "zoomify"]


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
        return json.loads(r.read().decode())


def latest(project):
    q = urllib.parse.urlencode({"loaders": json.dumps(["fabric"]), "game_versions": json.dumps([GAME])})
    vs = get(f"https://api.modrinth.com/v2/project/{project}/version?{q}")
    rel = [v for v in vs if v["version_type"] == "release"] or vs
    return rel[0] if rel else None


files, seen, names = [], set(), []
queue = list(MODS)
while queue:
    p = queue.pop(0)
    v = latest(p)
    if v is None:
        print("нет версии под Fabric", GAME, "(пропуск):", p)
        continue
    if v["project_id"] in seen:
        continue
    seen.add(v["project_id"])
    f = next((x for x in v["files"] if x.get("primary")), v["files"][0])
    files.append({
        "path": "mods/" + f["filename"],
        "hashes": {"sha1": f["hashes"]["sha1"], "sha512": f["hashes"]["sha512"]},
        "env": {"client": "required", "server": "unsupported"},
        "downloads": [f["url"]],
        "fileSize": f["size"],
    })
    names.append(f"{p} -> {f['filename']}")
    for d in v.get("dependencies", []):
        if d.get("dependency_type") == "required" and d.get("project_id") and d["project_id"] not in seen:
            queue.append(d["project_id"])

loaders = get(f"https://meta.fabricmc.net/v2/versions/loader/{GAME}")
loader = next(x["loader"]["version"] for x in loaders if x["loader"].get("stable"))

index = {
    "formatVersion": 1,
    "game": "minecraft",
    "versionId": "1.3.0",
    "name": "Aurora Optimized 26.1.2",
    "summary": "Оптимизированная сборка под i5-4440 + GTX 660 + 16 ГБ: Aurora Chat + Aurora FPS (держит 100 FPS)",
    "files": files,
    "dependencies": {"minecraft": GAME, "fabric-loader": loader},
}

os.makedirs(os.path.dirname(out_path), exist_ok=True)
with zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED) as z:
    z.writestr("modrinth.index.json", json.dumps(index, ensure_ascii=False, indent=2))
    z.write(options_txt, "overrides/options.txt")
    for src, dst in extra:
        if os.path.isfile(src):
            z.write(src, "overrides/" + dst)
        else:
            print("нет файла (пропуск):", src)

print("loader", loader)
print("\n".join(names))
print("модов с Modrinth:", len(files), "| своих файлов:", len(extra), "->", out_path)
