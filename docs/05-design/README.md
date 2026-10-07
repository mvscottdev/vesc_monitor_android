# Design kit

Everything visual in one place, in formats an AI coding session can read, grep, render and compare against.

```
docs/05-design/
  design-system.md    rules, components, states, screen inventory (read this first)
  tokens.json         source of every colour, font, size, radius, grid and motion value
  mockups/            HTML mockups, one file per area, one .frame#id per screen state
    index.html        gallery of all screens (thumbnails from shots/)
    kit.css / kit.js  mockup components; class names = component names in design-system.md
    tokens.css        GENERATED from tokens.json
    icons.js          Lucide sprite (same icon names as lucide-react-native)
    fonts/ fonts.css  vendored OFL fonts, so mockups render offline
  shots/<frame-id>.png  rendered frames at 915 x 412 (1 px = 1 dp)
scripts/design.mjs    tokens | check | shots [filter] | icons <dir>
```

## Commands
| Command | Does |
|---|---|
| `node scripts/design.mjs tokens` | Rebuild `mockups/tokens.css` after editing `tokens.json` |
| `node scripts/design.mjs check` | Fails if `tokens.css` is stale (add to `scripts/check`) |
| `node scripts/design.mjs shots` | Re-render every frame to `shots/`. Filter: `shots dash-` or `shots history` |
| `node scripts/design.mjs icons <lucide-static>/icons` | Rebuild the icon sprite after adding a name to `ICONS` in the script |

Shots need Playwright: `npm i -D playwright`, or a global install with `NODE_PATH=$(npm root -g)`.

## Using it while coding
- **Build a screen:** use the `build-screen` skill. In short: read the frame's annotations in its mockup file, take values only from tokens, build it with the component names from `design-system.md`, then screenshot the app (replay data) and compare with `shots/<frame-id>.png`.
- **Look at a screen without a browser:** Read `shots/<frame-id>.png` (the Read tool shows images).
- **Find a component's look:** grep its kebab name in `mockups/kit.css`, e.g. `\.bibar` for PowerBar.
- **Screenshot the app for comparison:** `adb exec-out screencap -p > /tmp/app.png` with the phone in landscape and the app on `ReplayTransport`.

## Changing the design
1. Change `tokens.json` for a value, `kit.css` for a component's look, or the mockup file for a layout.
2. `node scripts/design.mjs tokens && node scripts/design.mjs shots`.
3. Update `design-system.md` if a rule or component changed. Commit tokens, mockups and shots together.

## Adding a screen or state
Add a `<section class="spec">` with a `.frame#<area>-<state>` to the right mockup file, list its annotations below the frame, add a thumbnail to `mockups/index.html` and a row to the screen inventory in `design-system.md`, then render shots. Frame ids are kebab-case and unique across all files.

## Mockup conventions
- Sample values are realistic and consistent across screens (same ride, same pack), and never describe a real person's vehicle.
- Show 1-controller layouts by default; add a 2-controller state where the screen changes.
- Charts, gauges and sparklines are drawn by `kit.js` from `data-*` attributes that mirror the future component props. Sample series are deterministic, so shots are stable.
