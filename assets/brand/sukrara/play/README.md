<!-- Sukrara — https://github.com/mauriciabad/sukrara -->
<!-- Copyright (C) 2026 Maurici Abad -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Sukrara — Google Play feature graphic

`feature-graphic.html` is the source of the store listing's feature graphic (1024 × 500, no alpha): the brand
gradient, the cube glyph with its live dot, the wordmark and a glucose card. The card's value and curve are
synthetic (a smooth made-up curve that stays in range), never a person's data. The only word is the name, so en and
es share one graphic. Colours come from `../brand.json`; the fonts are the Mac's SF Pro / SF Pro Rounded.

Render it on the Mac with headless Chrome (from the repo root) and check it has no alpha channel:

```sh
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new --hide-scrollbars \
  --force-device-scale-factor=1 --allow-file-access-from-files --window-size=1024,500 \
  --screenshot="$HOME/Pictures/sukrara-play/feature-graphic.png" \
  "file://$PWD/assets/brand/sukrara/play/feature-graphic.html"
sips -g hasAlpha ~/Pictures/sukrara-play/feature-graphic.png   # hasAlpha: no
```

The PNG stays outside the repo with the store screenshots (`~/Pictures/sukrara-play/`).
