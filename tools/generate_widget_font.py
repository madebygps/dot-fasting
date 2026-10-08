"""Generate the widget's native timer font from the shared, MIT-adapted DotArt.

Requires fonttools: python -m pip install fonttools
Run from any directory: python tools/generate_widget_font.py
"""

from pathlib import Path
import re

from fontTools.fontBuilder import FontBuilder
from fontTools.pens.cu2quPen import Cu2QuPen
from fontTools.pens.ttGlyphPen import TTGlyphPen


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/madebygps/dotfasting/domain/DotArt.kt"
OUTPUT = ROOT / "app/src/main/res/font/widget_digits.ttf"


def generate():
    patterns = {
        character: re.findall(r'"([01]+)"', rows)
        for character, rows in re.findall(r"'(.)' to listOf\((.*?)\)", SOURCE.read_text())
    }
    # Android may localize chronometer digits. Keep the dotted appearance in these locales too.
    for start in (0x0660, 0x06F0, 0x0966, 0x09E6, 0x0E50, 0xFF10):
        for digit in range(10):
            patterns[chr(start + digit)] = patterns[str(digit)]
    patterns["-"] = patterns["\u2014"]
    patterns[" "] = ["000"] * 5
    patterns["\u2212"] = patterns["-"]
    order = [".notdef"] + [f"uni{ord(character):04X}" for character in patterns]
    glyphs = {".notdef": TTGlyphPen(None).glyph()}
    for character, rows in patterns.items():
        outlines = TTGlyphPen(None)
        pen = Cu2QuPen(outlines, max_err=1)
        for row_index, row in enumerate(rows):
            for column, bit in enumerate(row):
                if bit != "1":
                    continue
                x, y, radius = 100 + column * 200, 100 + (4 - row_index) * 200, 84
                control = round(radius * 0.552285)
                pen.moveTo((x + radius, y))
                pen.curveTo((x + radius, y + control), (x + control, y + radius), (x, y + radius))
                pen.curveTo((x - control, y + radius), (x - radius, y + control), (x - radius, y))
                pen.curveTo((x - radius, y - control), (x - control, y - radius), (x, y - radius))
                pen.curveTo((x + control, y - radius), (x + radius, y - control), (x + radius, y))
                pen.closePath()
        glyphs[f"uni{ord(character):04X}"] = outlines.glyph()
    builder = FontBuilder(1000, isTTF=True)
    builder.setupGlyphOrder(order)
    builder.setupCharacterMap({ord(character): f"uni{ord(character):04X}" for character in patterns})
    builder.setupGlyf(glyphs)
    builder.setupHorizontalMetrics({name: (800, 16) for name in order})
    builder.setupHorizontalHeader(ascent=1000, descent=0)
    builder.setupNameTable({
        "familyName": "Dot Fasting Timer",
        "styleName": "Regular",
        "uniqueFontIdentifier": "DotFastingTimer-Regular-1",
        "fullName": "Dot Fasting Timer Regular",
        "psName": "DotFastingTimer-Regular",
        "version": "Version 1.0",
        "copyright": "Copyright (c) 2026 Gwyneth Pena-Siguenza",
        "licenseDescription": "MIT-adapted Dot Habits numeric glyphs. See THIRD_PARTY_NOTICES.md.",
    })
    builder.setupOS2(sTypoAscender=1000, sTypoDescender=0, usWinAscent=1000, usWinDescent=0)
    builder.setupPost()
    # Fixed timestamps keep regeneration reproducible.
    builder.font["head"].created = builder.font["head"].modified = 3850000000
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    builder.save(OUTPUT)


if __name__ == "__main__":
    generate()
