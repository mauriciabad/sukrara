<!-- Sukrara — https://github.com/mauriciabad/sukrara -->
<!-- Copyright (C) 2026 Maurici Abad -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Sukrara — Mac App Store screenshots

`screenshots.html` lays out the Mac App Store screenshots: 2880 × 1800, Apple's largest Mac size, no alpha. Each one
puts real `sukrara-snap` renders on the brand gradient: the menu bar title and panel, windows and widgets. It adds a
caption (en or es), a plain window frame and a plain menu bar. The data in every picture is `sukrara-snap`'s synthetic
data: Marta and the made-up day. Never a person's. The six shots are `hero`, `alerts`, `follow`, `widgets`, `report`
and `dark`.

Make them on the Mac, from the repo root. First, the raw renders (a debug build, offline, nothing posted):

```sh
cd apps/macos && swift build --product sukrara-snap && SNAP="$(swift build --product sukrara-snap --show-bin-path)/sukrara-snap"
R=~/Pictures/sukrara-appstore/raw
for s in shell windows card; do "$SNAP" $s $R/en/$s; "$SNAP" $s-es $R/es/$s; done
cd ../..
```

Then the screenshots, with headless Chrome. Check that they have no alpha channel:

```sh
n=1; for s in hero alerts follow widgets report dark; do for l in en es; do
  mkdir -p ~/Pictures/sukrara-appstore/$l
  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new --hide-scrollbars \
    --force-device-scale-factor=2 --allow-file-access-from-files --virtual-time-budget=3000 --window-size=1440,900 \
    --screenshot="$HOME/Pictures/sukrara-appstore/$l/0$n-$s.png" \
    "file://$PWD/assets/brand/sukrara/appstore/screenshots.html?shot=$s&lang=$l&raw=file://$HOME/Pictures/sukrara-appstore/raw"
done; n=$((n+1)); done
sips -g pixelWidth -g pixelHeight -g hasAlpha ~/Pictures/sukrara-appstore/en/01-hero.png   # 2880, 1800, no
```

The PNGs stay outside the repo, like the Play media. They are uploaded in App Store Connect › 0.x › Mac, in this order,
for each language. A shot never shows the GitHub version's update row or an *Eat carbs* / *Take insulin* pill
(docs/20-publishing.md › *Mac App Store: review risk*). Render them again when the menu bar panel, Settings › Alerts,
the follow step, the widgets or the report change visibly.
