"""Кадры ShowcaseTest → картинки витрины (github/docs/media).
    set AURORA_SHOWCASE=1 && gradlew runClientGameTest -Pedition=public
    python pack/make_showcase.py
Нужен Pillow (pip install pillow)."""
import glob, os, re, shutil
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHOTS = os.path.join(ROOT, "build", "run", "clientGameTest", "screenshots")
OUT = os.path.join(ROOT, "github", "docs", "media")
os.makedirs(OUT, exist_ok=True)


def frames(tag):
    fs = [f for f in glob.glob(os.path.join(SHOTS, "*.png")) if re.search(r"_" + tag + r"(_\d+)?\.png$", f)]
    return sorted(fs, key=lambda f: int(os.path.basename(f).split("_")[0]))


def gif(tag, out, ms, hold_last=0, shared=True):
    fs = frames(tag)
    if not fs:
        raise SystemExit("нет кадров " + tag)
    imgs = [Image.open(f).convert("RGB") for f in fs]
    # общая палитра на все кадры — без мерцания цветов между кадрами
    if shared:
        pal = imgs[len(imgs) // 2].quantize(colors=255, method=Image.Quantize.MEDIANCUT)
        q = [im.quantize(palette=pal, dither=Image.Dither.NONE) for im in imgs]
    else:  # кадры разные (темы) — у каждого своя палитра
        q = [im.quantize(colors=255, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE) for im in imgs]
    dur = [ms] * len(q)
    if hold_last:
        dur[-1] = hold_last
    q[0].save(os.path.join(OUT, out), save_all=True, append_images=q[1:], duration=dur, loop=0, optimize=True, disposal=1)
    print(out, len(q), "кадров,", os.path.getsize(os.path.join(OUT, out)) // 1024, "КБ")


gif("sc_theme", "themes.gif", 1400, shared=False)
gif("sc_live", "messages.gif", 50, hold_last=1500)
for tag, name in [("sc_translate", "translate.png"), ("sc_settings_themes", "settings-themes.png"),
                  ("sc_settings_features", "settings-features.png"), ("sc_settings_colors", "settings-colors.png"),
                  ("sc_theme", "chat.png")]:
    src = frames(tag)[0 if tag != "sc_theme" else 1]
    Image.open(src).convert("RGB").save(os.path.join(OUT, name), optimize=True)
    print(name)
# каждая тема отдельно — для галереи тем (порядок как в ShowcaseTest.THEMES)
os.makedirs(os.path.join(OUT, "themes"), exist_ok=True)
for f, name in zip(frames("sc_theme"), ["discord", "aurora", "telegram", "neon", "sunset", "imessage", "terminal", "business"]):
    Image.open(f).convert("RGB").save(os.path.join(OUT, "themes", name + ".png"), optimize=True)
print("themes/*.png")
shutil.copy2(os.path.join(ROOT, "src", "main", "resources", "assets", "aurorachat", "icon.png"), os.path.join(OUT, "icon.png"))
