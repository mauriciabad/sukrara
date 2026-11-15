<!-- Sukrara — https://github.com/mauriciabad/sukrara · Copyright (C) 2026 Maurici Abad · SPDX-License-Identifier: GPL-3.0-or-later -->
# sukrara.mauri.app

The website: [Astro](https://astro.build) with [Starlight](https://starlight.astro.build), a static site published on
GitHub Pages by `.github/workflows/website.yml` from `main` (and again when a release is published). Starlight brings
the docs machinery (sidebar, search, English + Spanish routing with fallbacks, dark mode, SEO tags, sitemap, 404); the
site adds only its pages and a few components.

```sh
cd website
npm ci            # once (Node 24: .node-version)
npm run dev       # http://localhost:4321, live reload, docs/ included
npm run build     # → dist/  (SUKRARA_RELEASE=offline: no GitHub API lookup)
npm run check     # what CI checks on dist/ (below); `node scripts/check.mjs --external` also fetches outside links
```

## Where things are

| Path | What |
|---|---|
| `src/content/docs/*.md(x)` | The pages, English, at their path: `android.mdx` → `/android/`. Spanish: the same file in `es/` → `/es/android/` |
| `../docs/*.md` | The repository's docs, read where they live (`src/lib/repo-docs.ts`): `FEATURES.md` → `/developers/features/`, `09-brand.md` → `/research/09-brand/`, `docs/img` → `/img/`. Written for GitHub; their links are rewritten at build time (other docs → their page, the rest of the repo → GitHub at this commit). A new report appears by itself, in the sidebar and in `/developers/` |
| `src/content/i18n/{en,es}.json` | The components' words (`site.*`): every English key must exist in Spanish, or the build fails |
| `src/components/` | `Download` (the newest release's button, then one row of store-style badges: Google Play / Mac App Store as the stores give them, Obtainium and GitHub drawn to match; Homebrew; *Check it's the real Sukrara*), `Screenshot` / `Gallery`, `Logo` / `AppIcon` (brands' marks, apps' icons), `Invite` (`/join`), `SoundBoard`, `landing/*` (home page: `Sharing` is the drawing of how a reading travels), `overrides/*` (Starlight's hero, header links, banner, footer) |
| `src/lib/screens.ts` | Every app picture by name, with its alt text in both languages (below) |
| `src/lib/logos.ts`, `src/lib/apps.ts`, `src/assets/logos/` | Other brands' one-colour marks (Android, Apple, GitHub, Nostr…: Simple Icons, CC0) and the icons of the apps and services Sukrara works with (the Android app's own, plus AAPS's and Obtainium's); sources in `src/assets/logos/README.md` |
| `src/lib/image-service.ts` | Astro's image service with JPEG XL: every picture is served as JPEG XL, then AVIF, then PNG (never WebP). Sharp resizes; libjxl (WebAssembly, `@jsquash/jxl`) encodes the JPEG XL |
| `src/lib/release.ts` | The newest GitHub release, looked up at build time: the download buttons download its APK / dmg directly and show its version, size and the SHA-256 GitHub recorded (the deploy fails without it: `SUKRARA_RELEASE=required`) |
| `src/site.ts` | Domain (from `assets/brand/sukrara/brand.json`), repository, the store listings, what a download is checked against (the APK certificate from `tools/release/android-cert.sha256`, the Mac's Developer ID) |
| `src/assets/badges/` | The stores' official badges, black, en / es (Google Play: Google's PNG with its transparent margin trimmed; Mac App Store: Apple's SVG). Otherwise unmodified, as their guidelines ask; the trademark lines are in the footer |
| `src/styles/theme.css` | The brand on Starlight: the icon's cyan-to-navy gradient, navy greys, the lime "now" dot; Nunito (self-hosted) for headings |
| `src/pages/og/` | Each page's link preview (`/og/<page>.png`), rendered at build time; `src/route-data.ts` sets `og:image` |
| `public/` | Served as is: `appcast.xml` (the Mac update feed, written by `tools/release/mac-release.sh`), `.well-known/` (App Links, universal links), `sounds/` (the alert sounds as m4a), `robots.txt` |
| `scripts/check.mjs` | The post-build check (below) |

## URLs

The apps and other systems open some of them, so they don't move: `/android/#versions` (Android: Settings › About ›
*About the versions*), `/android/` and `/mac/` (each app's *Sukrara on other devices*), `/privacy` (both apps and the
store listings), `/join#<invite>` (every invite link; App Links and universal links claim `/join`), `/.well-known/*`,
`/appcast.xml` (the SUFeedURL of every installed Mac app). `npm run check` fails when a URL written in the apps' code
doesn't exist on the site.

## Pictures

From where they're already kept, never copied: the Android snapshot goldens (`apps/android/*/src/test/snapshots`,
light + dark, drawn by the app's code from synthetic data and kept current by its tests; the card is
`panel-in-range`, which leaves room for what the card draws past its edges), report pictures in `docs/img`, and the
Mac renders in `src/assets/mac/{en,es}` (`sukrara-snap`, synthetic data). A picture drawn edge to edge on a plain
ground gets a margin of that ground (`ground` in `screens.ts`), so nothing looks cut. Render those again when
the menu bar panel, the report or Add Treatment change visibly:

```sh
cd apps/macos && swift build --product sukrara-snap && SNAP="$(swift build --product sukrara-snap --show-bin-path)/sukrara-snap"
R=$(mktemp -d); "$SNAP" shell $R/en/shell; "$SNAP" windows $R/en/windows; "$SNAP" shell-es $R/es/shell; "$SNAP" windows-es $R/es/windows
for l in en es; do for t in light dark; do A=../../website/src/assets/mac/$l
  cp $R/$l/shell/panel-nearby-$t.png $A/panel-$t.png; cp $R/$l/windows/report-$t.png $A/report-$t.png
  cp $R/$l/windows/treatment-add-$t.png $A/treatment-add-$t.png; done; done; rm -rf $R
```

Never a person's data or a real phone's screenshot (CLAUDE.md). A picture is shown by name: `<Screenshot name="card"
width={360} />` shows the light or dark one with the viewer's theme, as JPEG XL (Chrome 155+, Firefox 158+, Safari
17+), else AVIF, else PNG.

## Languages

English and Spanish (Spain, *tú*, the apps' own words: *hidratos*, *Ajustes*, *seguidores*). Every page for people
using the apps has both; the developers' pages and reports are English only, and their Spanish URLs show the English
with Starlight's notice. The apps open the English pages; a browser that prefers Spanish gets a one-line hint to the
Spanish page (the × hides it for good). An anchor the apps open (`#versions`) exists on both languages' pages.

## What `npm run check` fails on

Every internal link and picture of every page leads to a file of the build, with its `#anchor` on the target page;
every `https://sukrara.mauri.app/…` the apps' code opens exists; `assetlinks.json` names the release key
(`tools/release/android-cert.sha256`); `apple-app-site-association` gives `/join` to the Mac app; the appcast has a
release; the sitemap, `robots.txt`, favicon and 404 exist. CI (`checks.yml`, job `website`) builds and checks every
change to `website/`, `docs/`, `assets/`, `contracts/` or `apps/`; the deploy runs the same check before publishing.

## Privacy

The site loads nothing from another host: fonts, search (Pagefind), pictures, logos and badges are its own; no
analytics, no cookies. The download buttons link GitHub. The privacy policy says so (*This website*): keep it true.

## Trust

Under each download, *Check it's the real Sukrara*: the APK's signing certificate in AppVerifier's format (the same for
the GitHub and the Google Play version; Android refuses an update with another one) and the `apksigner` command, or the
Mac's Developer ID and notarization with the `spctl` command; the file's SHA-256 as GitHub recorded it on upload, with
the commands to compare; `gh release verify-asset` (releases are immutable since 2026-11-15, so GitHub keeps a signed
record of each release's files); the same file on GitHub and the source at its tag.
