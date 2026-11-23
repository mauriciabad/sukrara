# /// script
# requires-python = ">=3.9"
# dependencies = ["freetype-py==2.5.1", "pillow>=10"]
# ///
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""The data field's fonts and its bell, per screen class (contracts/watchface/app/SPEC.md §14: the atlas set), built with
Sukrara Aura's renderer (apps/garmin/aura/tools/fonts.py: the same font, FreeType without hinting, the same atlas format
as the watch app's, apps/garmin/app/tools/fonts.py).

  uv run apps/garmin/field/tools/fonts.py          writes apps/garmin/field/classes/<class>/ (resources: fonts, the bell;
                                                   source: FieldFontTable.mc) and the watches' block of monkey.jungle
  uv run apps/garmin/field/tools/fonts.py --check  fails if the committed output differs from a fresh build

Only the big numbers (the value, LO / HI, "---"): the words are in the watch's own fonts (SPEC §14.1). Their sizes are
every size of the class's atlas set up to the largest the reference draws (field_sizes.js: app.js's S.dfSlot and
S.dfAlert on the class): the field's value takes the largest that fits its slot, and a watch has more layouts than the
reference's five. Each watch builds only its class's folder (monkey.jungle), so a watch carries its own sizes only.
"""
import base64
import importlib.util
import json
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
FIELD = os.path.dirname(HERE)
GARMIN = os.path.dirname(FIELD)
ROOT = os.path.dirname(os.path.dirname(GARMIN))
spec = importlib.util.spec_from_file_location("aura_fonts", os.path.join(GARMIN, "aura", "tools", "fonts.py"))
A = importlib.util.module_from_spec(spec)
spec.loader.exec_module(A)

CLASSES = os.path.join(FIELD, "classes")
JUNGLE = os.path.join(FIELD, "monkey.jungle")
MANIFEST = os.path.join(FIELD, "manifest.xml")
DEVICES = os.path.expanduser(os.environ.get("CIQ_DEVICES", "~/Library/Application Support/Garmin/ConnectIQ/Devices"))
NUMS = A.DIGITS + ".:LOHI-"
# app.js ICON.bell (24-unit path, stroke 2.2)
BELL = "M6 16.5V11a6 6 0 0 1 12 0v5.5l1.8 1.8H4.2zM10 20.5a2 2 0 0 0 4 0"
# the screen class of each deviceFamily (the reference's CLS)
FAMILY = {"round-360x360": "r360", "round-390x390": "r390", "round-416x416": "r416", "round-454x454": "r454",
          "round-466x466": "r466", "round-218x218": "m218", "round-240x240": "m240", "round-260x260": "m260",
          "round-280x280": "m280", "rectangle-320x360": "sq2", "rectangle-448x486": "x1"}
HEAD = ("Sukrara — https://github.com/mauriciabad/sukrara", "Copyright (C) 2026 Maurici Abad",
        "SPDX-License-Identifier: GPL-3.0-or-later")


def sizes():
    return json.loads(subprocess.run(["node", os.path.join(HERE, "field_sizes.js")], check=True, capture_output=True,
                                     text=True).stdout)


def keys(c):
    """The class's fonts: [(key, px, weight, chars)]."""
    out = []
    top = round(max(c["nums"]))
    nums = sorted(set([p for p in c["atlas"] if p <= top] + [round(p) for p in c["nums"]]))
    for px in nums:
        out.append(("n%d" % px, px, 700, NUMS))
    seen, uniq = set(), []
    for k in out:
        if k[0] not in seen:
            seen.add(k[0])
            uniq.append(k)
    return uniq


def bell_png(px):
    box = px + 2 * A.PAD
    svg = ('<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d"><g transform="translate(%d %d) '
           'scale(%s)"><path d="%s" fill="none" stroke="#FFFFFF" stroke-width="2.2" stroke-linecap="round" '
           'stroke-linejoin="round"/></g></svg>' % (box, box, box, box, A.PAD, A.PAD, repr(px / 24.0), BELL))
    import io
    from PIL import Image
    img = Image.open(io.BytesIO(A.rsvg(svg, box))).convert("RGBA")
    white = Image.new("RGBA", img.size, (255, 255, 255, 255))
    white.putalpha(img.getchannel("A"))
    return A.png_bytes(white), box


def plain_watches():
    """{watch: (class, plain)}: plain = no enhanced graphics (no tinted bitmap parts, no alpha: the Forerunner 255
    family), whose class's fonts are Connect IQ font resources drawn with drawText (the "<class>f" folders)."""
    ids = re.findall(r'<iq:product id="([^"]+)"', open(MANIFEST, encoding="utf-8").read())
    out = {}
    for i in ids:
        c = json.load(open(os.path.join(DEVICES, i, "compiler.json"), encoding="utf-8"))
        out[i] = (FAMILY[c["deviceFamily"]], not c.get("enhancedGraphicSupport"))
    return out


def fnt(key, px, weight, glyphs, pos, sheet, base):
    """A BMFont file of the atlas (app/tools/fonts.py's format)."""
    line = int(round(px * (A.ASCENT + A.DESCENT)))
    lines = [
        'info face="AtkinsonHyperlegibleNext-%d" size=%d bold=0 italic=0 charset="" unicode=1 stretchH=100 smooth=1 aa=1 '
        'padding=0,0,0,0 spacing=1,1' % (weight, px),
        "common lineHeight=%d base=%d scaleW=%d scaleH=%d pages=1 packed=0" % (line, base, sheet.width, sheet.height),
        'page id=0 file="%s.png"' % key,
        "chars count=%d" % len(glyphs)]
    for (ch, img, left, top, adv, w, h), (gx, gy) in zip(glyphs, pos):
        lines.append("char id=%d x=%d y=%d width=%d height=%d xoffset=%d yoffset=%d xadvance=%d page=0 chnl=15"
                     % (ord(ch), gx, gy, w, h, left, base - top, int(round(adv))))
    return ("\n".join(lines) + "\n").encode()


def class_files(cls, c, plain=False):
    files = {}
    folder = cls + ("f" if plain else "")
    res = os.path.join(CLASSES, folder, "resources")
    xml = ["<!-- %s -->" % h for h in HEAD] + [
        "<!-- Generated by apps/garmin/field/tools/fonts.py: Atkinson Hyperlegible Next (OFL, apps/garmin/fonts/OFL.txt). -->",
        "<resources>"]
    table = []
    for key, px, weight, chars in keys(c):
        glyphs = A.render(px, weight, chars)
        sheet, pos = A.pack(glyphs)
        base = int(round(px * A.ASCENT))
        bs = []
        for (ch, img, left, top, adv, w, h), (gx, gy) in zip(glyphs, pos):
            a = int(round(adv * 16))
            yo = base - top
            assert 0 <= w < 256 and 0 <= h < 256 and -128 <= left < 128 and -128 <= yo < 128, (cls, key, ch)
            bs += [gx >> 8, gx & 255, gy >> 8, gy & 255, w, h, left & 255, yo & 255, a >> 8, a & 255]
        files[os.path.join(res, "fonts", key + ".png")] = A.png_bytes(sheet)
        table.append((key, base))
        if plain:
            files[os.path.join(res, "fonts", key + ".fnt")] = fnt(key, px, weight, glyphs, pos, sheet, base)
            xml.append('    <font id="%s" filename="%s.fnt" antialias="true"/>' % (key, key))
            continue
        files[os.path.join(res, "fonts", key + ".json")] = (json.dumps(
            [chars, base64.b64encode(bytes(bs)).decode(), base], ensure_ascii=False) + "\n").encode()
        xml.append('    <bitmap id="%s" filename="%s.png" dithering="none" packingFormat="png"/>' % (key, key))
        xml.append('    <jsonData id="%s_m" filename="%s.json"/>' % (key, key))
    png, box = bell_png(c["bell"])
    files[os.path.join(res, "fonts", "bell.png")] = png
    xml.append('    <bitmap id="bell" filename="bell.png" dithering="none" packingFormat="png"/>')
    xml.append("</resources>")
    files[os.path.join(res, "fonts", "fonts.xml")] = ("\n".join(xml) + "\n").encode()
    mc = ["// %s" % h for h in HEAD] + [
        "// Generated by apps/garmin/field/tools/fonts.py (do not edit): the data field's fonts on the %s class%s." % (
            cls, " (font resources: these watches draw no tinted bitmap parts)" if plain else ""),
        "import Toybox.Lang;", "import Toybox.WatchUi;", "",
        "module FieldFontTable {",
        "    // the class (app.js CLS): layout units → px, the 64-colour palette, a rectangle, the atlas set (SPEC §14.1)",
        '    const CLS = "%s";' % cls,
        "    const R390 = %s; // the design's class: its approved sizes and strokes are drawn as they are" % ("true" if cls == "r390" else "false"),
        "    const S = %r;" % float(c["s"]),
        "    const MIP = %s;" % ("true" if c["mip"] else "false"),
        "    const RECT = %s;" % ("true" if c["rect"] else "false"),
        "    const ATLAS = [%s];" % ", ".join(str(p) for p in c["atlas"]),
        "    // the bell's tile: its size (px) and the tile with its margin",
        "    const BELL = %d;" % c["bell"],
        "    const BELL_BOX = %d;" % box,
        "",
        "    // key (\"n<px>\" a big number) → [atlas bitmap, glyph table] or [font, baseline px];",
        "    // [] = not on this class",
        "    function atlas(key as String) as Array {",
        "        var i = [%s].indexOf(key);" % ", ".join(A.mc_string(k) for k, _ in table),
        "        if (i < 0) {",
        "            return [];",
        "        }",
        ("        return [[%s][i], [%s][i]];" % (", ".join("Rez.Fonts.%s" % k for k, _ in table),
                                              ", ".join(str(b) for _, b in table))) if plain else
        ("        return [[%s][i], [%s][i]];" % (", ".join("Rez.Drawables.%s" % k for k, _ in table),
                                              ", ".join("Rez.JsonData.%s_m" % k for k, _ in table))),
        "    }",
        "",
        "    function bell() as ResourceId {",
        "        return Rez.Drawables.bell;",
        "    }",
        "}", ""]
    files[os.path.join(CLASSES, folder, "source", "FieldFontTable.mc")] = "\n".join(mc).encode()
    return files


def jungle_block():
    """monkey.jungle's block: each watch of the manifest builds its class's folder."""
    lines = ["# BEGIN screen classes (apps/garmin/field/tools/fonts.py)",
             "# each watch builds its screen class's fonts and table (classes/<class>)"]
    for i, (cls, plain) in plain_watches().items():
        cls += "f" if plain else ""
        lines.append("%s.sourcePath = $(%s.sourcePath);classes/%s/source" % (i, i, cls))
        lines.append("%s.resourcePath = $(%s.resourcePath);classes/%s/resources" % (i, i, cls))
    lines.append("# END screen classes")
    return "\n".join(lines)


def outputs():
    out = {}
    plain = {cls for cls, p in plain_watches().values() if p}
    for cls, c in sizes().items():
        out.update(class_files(cls, c))
        if cls in plain:
            out.update(class_files(cls, c, True))
    j = open(JUNGLE, encoding="utf-8").read()
    block = jungle_block()
    if "# BEGIN screen classes" in j:
        j = re.sub(r"# BEGIN screen classes.*?# END screen classes", lambda m: block, j, flags=re.S)
    else:
        j = j.rstrip("\n") + "\n" + block + "\n"
    out[JUNGLE] = j.encode()
    return out


def main(argv):
    out = outputs()
    if "--check" in argv:
        bad = [p for p, b in out.items() if not os.path.exists(p) or open(p, "rb").read() != b]
        if bad:
            print("field fonts.py: out of date (run uv run apps/garmin/field/tools/fonts.py):\n  "
                  + "\n  ".join(os.path.relpath(p, ROOT) for p in bad))
            return 1
        print("field fonts.py: up to date")
        return 0
    for root, _, fs in os.walk(CLASSES):
        for f in fs:
            if os.path.join(root, f) not in out:
                os.remove(os.path.join(root, f))
    for p, b in out.items():
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, "wb") as fh:
            fh.write(b)
    print("field fonts.py: wrote %d files" % len(out))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
