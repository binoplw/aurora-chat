"""Заглушки платных классов Aurora Chat+ для GitHub-версии (там их код не публикуется).
Берёт скомпилированные классы (gradlew compileJava -Pedition=public) и по javap пишет классы с тем же API
и пустыми телами в build/premium-stubs/. make_public_repo.py кладёт их на место настоящих.
    python pack/gen_premium_stubs.py"""
import os, re, subprocess, sys, glob

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CLASSES = os.path.join(ROOT, "build", "classes", "java", "main")
OUT = os.path.join(ROOT, "build", "premium-stubs")
PKG = "net.primlixe.aurorachat"
# платные классы: путь от пакета мода
PREMIUM = ["chat/Translator", "chat/ExtraWindows", "gui/PlayerWheelScreen", "chat/WheelEffects", "chat/AfkResponder",
           "chat/Reminders", "chat/LaterSend", "chat/Templates", "chat/AutoGreet", "chat/AutoJoin", "chat/NickComplete",
           "chat/ColorPicker", "chat/ChatLogScreen", "chat/LinkInfo"]


def javap_exe():
    for c in [os.path.join(os.environ.get("JAVA_HOME", ""), "bin", "javap.exe"), r"C:\Program Files\Java\jdk-21.0.12\bin\javap.exe", "javap"]:
        if c == "javap" or os.path.exists(c):
            return c


JAVAP = javap_exe()


def javap(cls, extra=()):
    r = subprocess.run([JAVAP, "-package", *extra, "-cp", CLASSES + os.pathsep + classpath(), cls], capture_output=True, text=True, encoding="utf-8")
    if r.returncode:
        raise SystemExit(r.stderr)
    return r.stdout


_cp = None


def classpath():
    """Классы Minecraft — чтобы javap видел методы родителей (Screen)."""
    global _cp
    if _cp is None:
        jars = glob.glob(os.path.join(os.path.expanduser("~"), ".gradle", "caches", "fabric-loom", "minecraftMaven", "net", "minecraft",
                                      "minecraft-merged-deobf", "26.1.2", "*.jar"))
        _cp = os.pathsep.join(j for j in jars if "sources" not in j)
    return _cp


def T(t):
    return t.replace("$", ".")


PRIM = {"boolean": "false", "int": "0", "long": "0L", "float": "0f", "double": "0d", "short": "(short) 0", "byte": "(byte) 0", "char": "'\\0'"}


def default(rtype, params):
    raw = re.sub(r"<.*>", "", rtype)
    if rtype in PRIM:
        return PRIM[rtype]
    for i, p in enumerate(params):  # тот же тип, что у аргумента: вернуть его (originalOf(x) → x)
        if p == rtype:
            return f"a{i}"
    if raw.endswith("[]"):
        base, dims = raw.split("[", 1)[0], raw.count("[]")
        return f"new {base}[0]" + "[]" * (dims - 1)
    if raw == "java.lang.String":
        return '""'
    if raw == "net.minecraft.network.chat.Component":
        return "net.minecraft.network.chat.Component.empty()"
    if raw in ("java.util.List", "java.util.Collection"):
        return "java.util.List.of()"
    if raw == "java.util.Set":
        return "java.util.Set.of()"
    if raw == "java.util.Map":
        return "java.util.Map.of()"
    if raw == "java.util.Optional":
        return "java.util.Optional.empty()"
    if raw == "java.util.concurrent.CompletableFuture":
        return "java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException(\"Aurora Chat+\"))"
    return "null"


def split_params(s):
    out, depth, cur = [], 0, ""
    for ch in s:
        if ch == "<": depth += 1
        if ch == ">": depth -= 1
        if ch == "," and depth == 0:
            out.append(cur.strip()); cur = ""
        else:
            cur += ch
    if cur.strip():
        out.append(cur.strip())
    return out


MEMBER = re.compile(r"^\s*((?:public|protected|private|static|final|abstract|synchronized|native|default|transient|volatile)\s+)*(<[^()]*>\s+)?([\w.$<>\[\], ?]+?)\s+(\w+)\(([^)]*)\)(?:\s+throws\s+[\w.$, ]+)?;$")
CTOR = re.compile(r"^\s*((?:public|protected|private)\s+)?([\w.$]+)\(([^)]*)\);$")
FIELD = re.compile(r"^\s*((?:public|protected|private|static|final|transient|volatile)\s+)*([\w.$<>\[\], ?]+?)\s+(\w+);$")


def parent_methods(sup):
    """Имена методов родителя (и его родителей) — их не переопределяем."""
    # события ввода приходят из интерфейсов (ContainerEventHandler) — их тоже не трогаем
    names, cls = {"mouseClicked", "mouseReleased", "mouseDragged", "mouseScrolled", "mouseMoved", "keyPressed", "keyReleased", "charTyped"}, sup
    while cls and cls != "java.lang.Object":
        txt = javap(cls, ("-public",) if False else ())
        head = txt.splitlines()[1] if txt.startswith("Compiled") else txt.splitlines()[0]
        for l in txt.splitlines():
            m = MEMBER.match(l)
            if m:
                names.add(m.group(4))
        m = re.search(r"extends ([\w.$]+)", head)
        cls = m.group(1) if m else None
    return names


def stub(cls, nested_level=0, outer=None):
    txt = javap(cls)
    lines = [l for l in txt.splitlines() if l.strip() and not l.startswith("Compiled from")]
    head = lines[0]
    name = cls.rsplit(".", 1)[1].split("$")[-1]
    mods = "public " + ("static " if nested_level else "")
    ext = re.search(r"extends ([\w.$<>, ]+?)(?: implements| \{)", head)
    sup = ext.group(1).strip() if ext else None
    impl = re.search(r"implements ([\w.$<>, ]+) \{", head)
    ind = "\t" * nested_level
    out = []

    if sup == "java.lang.Record":
        ctor = next(CTOR.match(l) for l in lines if CTOR.match(l))
        types = split_params(ctor.group(3))
        comps = [MEMBER.match(l).group(4) for l in lines if MEMBER.match(l) and MEMBER.match(l).group(5) == ""
                 and MEMBER.match(l).group(4) not in ("toString", "hashCode")]
        out.append(f"{ind}{mods.replace('static ', '')}record {name}(" + ", ".join(f"{T(t)} {c}" for t, c in zip(types, comps)) + ") {")
        out.append(f"{ind}}}")
        return out
    if sup and sup.startswith("java.lang.Enum"):
        consts = [FIELD.match(l).group(3) for l in lines if FIELD.match(l) and T(FIELD.match(l).group(2)) == T(cls)]
        out.append(f"{ind}{mods}enum {name} {{")
        out.append(f"{ind}\t" + ", ".join(consts) + ";")
        out.append(f"{ind}}}")
        return out

    skip = parent_methods(re.sub(r"<.*>", "", sup)) if sup and sup != "java.lang.Object" else set()
    kind = "interface" if " interface " in head else "class"
    fin = "final " if " final class " in head else ""
    absm = "abstract " if " abstract class " in head else ""
    decl = f"{ind}{mods}{absm}{fin}{kind} {name}"
    if sup and sup != "java.lang.Object":
        decl += f" extends {T(sup)}"
    if impl:
        decl += f" implements {T(impl.group(1))}"
    out.append(decl + " {")
    is_screen = bool(skip)
    for l in lines[1:]:
        l = l.rstrip()
        if l.strip() in ("}", "static {};"):
            continue
        m = CTOR.match(l)
        if m and T(m.group(2)) == T(cls):
            ps = split_params(m.group(3))
            if nested_level and "static" not in head and ps and T(ps[0]) == T(outer or ""):
                ps = ps[1:]  # внутренний класс: первый параметр — внешний объект
            body = "super(net.minecraft.network.chat.Component.empty());" if is_screen else ""
            out.append(f"{ind}\t{(m.group(1) or '').strip()} {name}(" + ", ".join(f"{T(p)} a{i}" for i, p in enumerate(ps)) + f") {{ {body} }}")
            continue
        m = MEMBER.match(l)
        if m:
            mname = m.group(4)
            if mname in skip or mname.startswith("lambda$") or mname.startswith("access$"):
                continue
            modifiers = re.findall(r"\b(public|protected|static|final|abstract|synchronized)\b", l.split("(")[0].rsplit(" ", 2)[0] if m.group(1) else "")
            modifiers = [x for x in modifiers if x not in ("abstract", "synchronized")]
            tparams = (m.group(2) or "").strip()
            rtype = T(m.group(3).strip())
            ps = [T(p) for p in split_params(m.group(5))]
            sig = " ".join(modifiers + ([tparams] if tparams else []) + [rtype, mname]) + "(" + ", ".join(f"{p} a{i}" for i, p in enumerate(ps)) + ")"
            if kind == "interface":
                out.append(f"{ind}\t{sig};")
            else:
                body = "" if rtype == "void" else f"return {default(rtype, ps)};"
                out.append(f"{ind}\t{sig} {{ {body} }}".replace("{  }", "{ }"))
            continue
        m = FIELD.match(l)
        if m:
            modifiers = re.findall(r"\b(public|protected|static|final|transient|volatile)\b", l)
            ftype = T(m.group(2).strip())
            init = f" = {default(ftype, [])}" if "final" in modifiers else ""
            out.append(f"{ind}\t{' '.join(modifiers)} {ftype} {m.group(3)}{init};")
            continue
        print("  ? пропущено:", l.strip(), file=sys.stderr)
    if is_screen and not any(f" {name}(" in x for x in out[1:]):
        out.append(f"{ind}	private {name}() {{ super(net.minecraft.network.chat.Component.empty()); }}")
    # вложенные классы (кроме анонимных $1)
    base = os.path.join(CLASSES, *cls.split("."))
    for f in sorted(glob.glob(base + "$*.class")):
        n = os.path.basename(f)[:-6].split("$")
        if len(n) != len(cls.split("$")) + 1 or n[-1].isdigit():
            continue
        out.append("")
        out += stub(cls + "$" + n[-1], nested_level + 1, cls)
    out.append(f"{ind}}}")
    return out


def premium_stub():
    """Premium для GitHub: тот же API, has() всегда false, ключи не принимаются, проверки подписи нет."""
    st = open(os.path.join(ROOT, "src", "main", "java", "net", "primlixe", "aurorachat", "premium", "Premium.java"), encoding="utf-8").read()
    st = st.replace("public static final boolean BUILT_IN = true;", "public static final boolean BUILT_IN = false;")
    st = re.sub(r'\t/\*\* Открытый ключ проверки подписи.*?\n\tprivate static final String PUBLIC_KEY = "[^"]*";\n', "", st, flags=re.S)
    st = re.sub(r"\t/\*\* Платные функции открыты\..*?\n\t}\n",
                "\t/** Версия с GitHub: платные функции не собраны. */\n\tpublic static boolean has() { return false; }\n", st, count=1, flags=re.S)
    st = re.sub(r"\t/\*\* Ввести ключ\..*?\n\t}\n\n\t/\*\* Проверить ключ.*?\n\t}\n",
                "\t/** Версия с GitHub: ключи вводятся в полной версии с Modrinth. */\n"
                "\tpublic static String activate(String key) { return \"Это сборка с GitHub без Aurora Chat+ — скачай полную версию с Modrinth\"; }\n",
                st, count=1, flags=re.S)
    st = re.sub(r"\t/\*\* Прочитать сохранённый ключ.*?\n\t}\n", "\tpublic static void load() { }\n", st, count=1, flags=re.S)
    st = re.sub(r"\t/\*\* Ключ только что ввели.*?\n\t}\n", "", st, count=1, flags=re.S)
    st = st.replace(" * Ключи выдаёт pack/IssueKey.java (секретный ключ подписи — только у автора).",
                    " * Это версия для GitHub: код платных функций и проверки ключей не публикуется — вместо них заглушки.")
    for imp in ["java.nio.charset.StandardCharsets", "java.security.KeyFactory", "java.security.PublicKey", "java.security.Signature",
                "java.security.spec.X509EncodedKeySpec", "java.util.Base64", "java.nio.file.Files", "net.primlixe.aurorachat.AuroraChatMod"]:
        if st.count(imp.rsplit(".", 1)[1]) <= 1:
            st = st.replace(f"import {imp};\n", "")
    if "PUBLIC_KEY" in st or "Signature" in st or "has() { return false; }" not in st:
        raise SystemExit("заглушка Premium собрана неправильно")
    dst = os.path.join(OUT, "net", "primlixe", "aurorachat", "premium", "Premium.java")
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    open(dst, "w", encoding="utf-8", newline="\n").write(st)
    print("stub premium/Premium")


def main():
    premium_stub()
    for p in PREMIUM:
        cls = PKG + "." + p.replace("/", ".")
        body = "\n".join(stub(cls))
        pkg = cls.rsplit(".", 1)[0]
        src = (f"package {pkg};\n\n/** Заглушка: эта функция входит в Aurora Chat+, её код не публикуется. Полная версия — modrinth.com/mod/aurora-ryizen */\n"
               f"@SuppressWarnings(\"all\")\n{body}\n")
        dst = os.path.join(OUT, "net", "primlixe", "aurorachat", *p.split("/")) + ".java"
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        open(dst, "w", encoding="utf-8", newline="\n").write(src)
        print("stub", p)


if __name__ == "__main__":
    main()
