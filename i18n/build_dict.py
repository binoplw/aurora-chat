"""ui_ru.txt + en_*.txt → en.json (ключ — русская строка). Проверяет подстановки и §-коды."""
import json, re, sys, glob, os

d = os.path.dirname(os.path.abspath(__file__))
ru = {}
for line in open(os.path.join(d, 'ui_ru.txt'), encoding='utf-8'):
    i, t = line.rstrip('\n').split('\t', 1)
    ru[int(i)] = t.replace('⏎', '\n')
en = {}
for f in sorted(glob.glob(os.path.join(d, 'en_*.txt'))):
    for line in open(f, encoding='utf-8'):
        if not line.strip():
            continue
        i, t = line.rstrip('\n').split('\t', 1)
        en[int(i)] = t.replace('⏎', '\n')
fmt = re.compile(r'%[-.\d]*[sdfX]')
sect = re.compile(r'§[0-9a-fk-or]')
out, bad = {}, 0
for i, t in en.items():
    k = ru.get(i)
    if k is None:
        print('нет ключа', i); bad += 1; continue
    if sorted(fmt.findall(k)) != sorted(fmt.findall(t)):
        print('ПОДСТАНОВКИ', i, repr(k), '→', repr(t)); bad += 1
    if sorted(sect.findall(k)) != sorted(sect.findall(t)):
        print('§-коды', i, repr(k), '→', repr(t)); bad += 1
    if k.count('\n') != t.count('\n'):
        print('переносы', i); bad += 1
    if t != k:
        out[k] = t
missing = [i for i in ru if i not in en]
print('переведено', len(out), '| без перевода', len(missing), '| ошибок', bad)
json.dump(out, open(sys.argv[1], 'w', encoding='utf-8'), ensure_ascii=False, indent=1, sort_keys=True)
