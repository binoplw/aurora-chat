"""Русские строки интерфейса → нумерованный список (ключи для словаря en.json).
python extract_ui.py <src root> <out.txt>
Ключ — строка без ведущих §-кодов и пробелов по краям (так ищет I18n.t)."""
import re, os, sys

root, out = sys.argv[1], sys.argv[2]
SCOPE = ["gui/ChatSettingsScreen.java", "ui/Widgets.java", "ui/UI.java", "chat/ChatHud.java", "chat/ChatMenu.java",
         "mixin/ChatScreenMixin.java", "chat/ChatLogScreen.java", "gui/ConsoleScreen.java", "chat/ColorPicker.java",
         "chat/Translator.java", "AuroraChatMod.java", "config/ChatThemes.java", "config/UiThemes.java",
         "gui/PlayerWheelScreen.java", "chat/LinkInfo.java", "chat/ExtraWindows.java", "chat/Reminders.java",
         "chat/LaterSend.java", "chat/AfkResponder.java", "compat/Compat.java", "ui/BgImage.java", "chat/ChatCore.java",
         "chat/WheelEffects.java", "chat/AntiSpam.java", "chat/AutoJoin.java", "chat/ChatToasts.java", "config/ChatConfig.java"]
pat = re.compile(r'"((?:[^"\\]|\\.)*[Ѐ-ӿ](?:[^"\\]|\\.)*)"')
lead = re.compile(r'^(?:\s|§[0-9a-fk-or])+')
seen, keys = set(), []
for rel in SCOPE:
    p = os.path.join(root, rel)
    if not os.path.exists(p):
        continue
    skip = False
    for l in open(p, encoding='utf-8').read().split('\n'):
        s = l.strip()
        if rel.endswith('ChatSettingsScreen.java'):
            if 'private void initModeration' in l or 'private void initStaff' in l or 'header("Викторины в чате"' in l:
                skip = True
            elif skip and ('private void init' in l or 'header("Автоприветствия"' in l):
                skip = False
            if skip:
                continue
        if rel.endswith('ChatConfig.java') and not re.match(r'^\s*[A-Z_]+\(.*"[^"]*[Ѐ-ӿ]', l):
            continue  # из конфига — только названия вариантов перечислений
        if s.startswith('//') or s.startswith('*') or s.startswith('/*') or 'Pattern.compile' in l or 'Regex.of' in l:
            continue
        for m in pat.findall(l):
            k = lead.sub('', m.encode('utf-8').decode('unicode_escape').encode('latin-1').decode('utf-8') if '\\u' in m else m).strip()
            k = k.replace('\\n', '\n').replace('\\"', '"')
            if len(k) < 2 or k in seen:
                continue
            seen.add(k)
            keys.append(k)
with open(out, 'w', encoding='utf-8') as f:
    for i, k in enumerate(keys):
        f.write(f"{i}\t{k.replace(chr(10), '⏎')}\n")
print(len(keys), "строк,", sum(len(k) for k in keys), "символов")
