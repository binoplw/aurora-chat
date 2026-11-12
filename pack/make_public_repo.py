"""Чистая копия Aurora Chat для GitHub: без кода личной сборки (модерация, наказания, персонал, викторины).
python make_public_repo.py <AuroraChat> <AuroraChat-public>"""
import os, shutil, sys, re

src, dst = sys.argv[1], sys.argv[2]
SKIP_DIRS = {'.gradle', 'build', 'run', '.git', '.idea', 'out', 'github', '__pycache__'}
SKIP_FILES = {'build_test.log'}
PRIVATE = ['chat/Moderation.java', 'chat/PunishLog.java', 'chat/StaffWatch.java', 'chat/ChatGames.java', 'gui/PunishLogScreen.java']
PKG = os.path.join('src', 'main', 'java', 'net', 'primlixe', 'aurorachat')
# Aurora Chat+: код платных функций на GitHub не публикуется — заглушки из build/premium-stubs (pack/gen_premium_stubs.py)
STUBS = os.path.join(src, 'build', 'premium-stubs')
sys.path.insert(0, os.path.join(src, 'pack'))
import gen_premium_stubs
gen_premium_stubs.main()
PREMIUM = [p + '.java' for p in gen_premium_stubs.PREMIUM] + ['premium/Premium.java']
HIDDEN = ['pack' + os.sep + 'IssueKey.java', 'PremiumTest.java']

if os.path.exists(dst):
    # пересоздаём всё, кроме .git
    for n in os.listdir(dst):
        if n == '.git':
            continue
        p = os.path.join(dst, n)
        shutil.rmtree(p) if os.path.isdir(p) else os.remove(p)
os.makedirs(dst, exist_ok=True)

for d, dirs, files in os.walk(src):
    dirs[:] = [x for x in dirs if x not in SKIP_DIRS]
    rel = os.path.relpath(d, src)
    if rel.startswith(os.path.join('src', 'public')):
        continue
    for f in files:
        r = os.path.normpath(os.path.join(rel, f))
        if f in SKIP_FILES or r.endswith('ChatVisualTest.java') or any(r.endswith(h) for h in HIDDEN):
            continue
        if any(r == os.path.normpath(os.path.join(PKG, p)) for p in PRIVATE + PREMIUM):
            continue
        os.makedirs(os.path.join(dst, rel), exist_ok=True)
        shutil.copy2(os.path.join(d, f), os.path.join(dst, r))

# заглушки — на место настоящих классов
for p in PRIVATE:
    shutil.copy2(os.path.join(src, 'src', 'public', 'java', 'net', 'primlixe', 'aurorachat', p), os.path.join(dst, PKG, p))

for p in PREMIUM:
    shutil.copy2(os.path.join(STUBS, 'net', 'primlixe', 'aurorachat', p), os.path.join(dst, PKG, p))

# build.gradle: всегда публичная редакция, без подмены исходников
g = open(os.path.join(dst, 'build.gradle'), encoding='utf-8').read()
g = g.replace("def publicEdition = (project.findProperty('edition') ?: 'private') == 'public'", "def publicEdition = true // GitHub-версия всегда публичная")
g = re.sub(r"// Public edition: the Mineblaze-only features.*?\n}\n", "", g, flags=re.S)
open(os.path.join(dst, 'build.gradle'), 'w', encoding='utf-8').write(g)

# тестовый fabric.mod.json без ChatVisualTest
t = os.path.join(dst, 'src', 'gametest', 'resources', 'fabric.mod.json')
s = open(t, encoding='utf-8').read().replace('"net.primlixe.aurorachat.test.ChatVisualTest", ', '').replace(', "net.primlixe.aurorachat.test.PremiumTest"', '')
open(t, 'w', encoding='utf-8').write(s)
# README, .gitignore, шаблоны issues — из AuroraChat/github
gh = os.path.join(src, 'github')
for d, dirs, files in os.walk(gh):
    rel = os.path.relpath(d, gh)
    os.makedirs(os.path.join(dst, rel), exist_ok=True)
    for f in files:
        shutil.copy2(os.path.join(d, f), os.path.join(dst, rel, f))
# готовый jar Aurora Lib — чтобы проект собирался без соседней папки AuroraLib
lib = os.path.join(os.path.dirname(src), 'AuroraLib', 'build', 'libs')
os.makedirs(os.path.join(dst, 'libs'), exist_ok=True)
for f in os.listdir(lib):
    if f.startswith('aurora-lib-1.1.0+mc') and f.endswith('.jar') and 'sources' not in f:
        shutil.copy2(os.path.join(lib, f), os.path.join(dst, 'libs', f))
g = open(os.path.join(dst, 'build.gradle'), encoding='utf-8').read()
g = g.replace("flatDir { dirs '../AuroraLib/build/libs' }", "flatDir { dirs 'libs', '../AuroraLib/build/libs' } // libs/ — готовый jar Aurora Lib")
open(os.path.join(dst, 'build.gradle'), 'w', encoding='utf-8', newline='').write(g)
print('ok', dst)
