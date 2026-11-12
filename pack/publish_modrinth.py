"""Выложить новую версию Aurora Chat на Modrinth одной командой.

    set MODRINTH_TOKEN=mrp_...        (токен: modrinth.com → Settings → PATs, права: Create versions)
    python publish_modrinth.py                 — собрать публичную версию, проверить тестом и выложить
    python publish_modrinth.py --dry-run       — всё то же, но без отправки (покажет, что ушло бы)
    python publish_modrinth.py --skip-test     — без игрового теста (быстрее)

Версия берётся из gradle.properties, список изменений — из CHANGELOG.md (раздел текущей версии).
Токен читается только из переменной окружения и никуда, кроме api.modrinth.com, не отправляется.
"""
import json, os, re, subprocess, sys, urllib.request, urllib.error, uuid

SLUG = "aurora-chat"
API = "https://api.modrinth.com/v2"
UA = "PrimLixe/AuroraChat-publisher/1.0 (modrinth.com/mod/aurora-chat)"
FABRIC_API = "P7dR8mSH"   # fabric-api
MOD_MENU = "mOgUt4GM"     # modmenu (необязательная)
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
args = set(sys.argv[1:])
dry = "--dry-run" in args


def prop(name):
    for line in open(os.path.join(ROOT, "gradle.properties"), encoding="utf-8"):
        if line.startswith(name + "="):
            return line.split("=", 1)[1].strip()
    sys.exit(f"нет {name} в gradle.properties")


def changelog(version):
    p = os.path.join(ROOT, "CHANGELOG.md")
    if not os.path.exists(p):
        return f"Aurora Chat {version}"
    text = open(p, encoding="utf-8").read()
    m = re.search(r"^## " + re.escape(version) + r"\b.*?$(.*?)(?=^## |\Z)", text, re.S | re.M)
    return m.group(1).strip() if m else f"Aurora Chat {version}"


def gradle(*a):
    w = os.path.join(ROOT, "gradlew.bat" if os.name == "nt" else "gradlew")
    r = subprocess.run([w, *a, "-Pedition=public", "--console=plain"], cwd=ROOT)
    if r.returncode != 0:
        sys.exit(f"gradle {' '.join(a)} — ошибка, ничего не выложено")


def call(method, path, token, body=None, ctype=None):
    req = urllib.request.Request(API + path, data=body, method=method, headers={"User-Agent": UA, "Authorization": token})
    if ctype:
        req.add_header("Content-Type", ctype)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return json.loads(r.read().decode() or "null")
    except urllib.error.HTTPError as e:
        sys.exit(f"Modrinth ответил {e.code}: {e.read().decode(errors='replace')[:500]}")


mod_version, game = prop("version"), prop("minecraft_version")
version = f"{mod_version}+mc{game}"
jar = os.path.join(ROOT, "build", "libs", f"aurora-chat-{version}-public.jar")
token = os.environ.get("MODRINTH_TOKEN", "")
if not token and not dry:
    sys.exit("Нет токена: set MODRINTH_TOKEN=mrp_...  (или запусти с --dry-run)")

print(f"Aurora Chat {version} → Modrinth ({SLUG})")
gradle("build")
if "--skip-test" not in args:
    gradle("runClientGameTest")
if not os.path.exists(jar):
    sys.exit(f"нет файла {jar}")

project_id = call("GET", f"/project/{SLUG}", token)["id"] if token else "<project_id>"
existing = call("GET", f"/project/{SLUG}/version", token) if token else []
if any(v.get("version_number") == version for v in existing):
    sys.exit(f"Версия {version} уже есть на Modrinth — подними version в gradle.properties")

data = {
    "name": f"Aurora Chat {mod_version}",
    "version_number": version,
    "changelog": changelog(mod_version),
    "dependencies": [{"project_id": FABRIC_API, "dependency_type": "required"},
                     {"project_id": MOD_MENU, "dependency_type": "optional"}],
    "game_versions": [game],
    "version_type": "release",
    "loaders": ["fabric"],
    "featured": True,
    "status": "listed",
    "project_id": project_id,
    "file_parts": ["file"],
    "primary_file": "file",
}
print(json.dumps({k: v for k, v in data.items() if k != "changelog"}, ensure_ascii=False, indent=1))
print("changelog:\n" + data["changelog"])
if dry:
    print(f"\n--dry-run: ничего не отправлено. Файл: {jar} ({os.path.getsize(jar)} байт)")
    sys.exit(0)

b = uuid.uuid4().hex
body = (f"--{b}\r\nContent-Disposition: form-data; name=\"data\"\r\nContent-Type: application/json\r\n\r\n").encode() \
    + json.dumps(data).encode() \
    + (f"\r\n--{b}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{os.path.basename(jar).replace('-public', '')}\"\r\n"
       f"Content-Type: application/java-archive\r\n\r\n").encode() \
    + open(jar, "rb").read() + f"\r\n--{b}--\r\n".encode()
v = call("POST", "/version", token, body, f"multipart/form-data; boundary={b}")
print(f"\nГотово: https://modrinth.com/mod/{SLUG}/version/{v['id']}")
