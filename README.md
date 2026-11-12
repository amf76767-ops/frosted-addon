# Frosted

Meteor Client addon for donutsmp.net. Fabric 1.21.11, Java 21, client side only.
Four categories (all with an animated frosted header): **Frosted Basehunting**,
**Frosted Combat**, **Frosted Util**, **Frosted Visuals**.

## Basehunting
- `sus-chunks` – zones whose growth (amethyst, kelp, bamboo, berries, vines, dripstone) proves long load time.
- `chunk-finder-v2` – chunks with construction evidence: misplaced deepslate, end stone, stripped geodes, farms.
- `deep-player-finder` – chunks with zeroed underground light.
- `light-debug` – highlights artificial light underground.
- `ghost-esp` – remembers chests, barrels, ender chests, shulkers, spawners (optional: furnaces, hoppers, dispensers) while their chunk is loaded and keeps drawing them as ghosts after it unloads. Saved per server in `.minecraft/frosted/`.

## Combat
`autoclicker`, `trigger-bot`, `aim-assist`, `w-tap`, `auto-jump-reset`, `key-pearl`.
`aim-assist` can prioritise by distance or by crosshair, softens its approach with `ease`,
and pauses while you mine or use an item.

## Util
`death-coords`, `staff-list`, `region-map`, plus the camera modules `freecam-plus` and `freelook-plus`.

## Visuals
- `cs-knife` – CS-style knife overlay drawn as real polygons. Styles: Karambit, Bayonet, Flip, Huntsman, Gut,
  Butterfly, Stiletto, twin Daggers. Finishes: Frost, Glacier, Aurora, Obsidian, Ruby, Custom.
  Animations: idle sway (grows while walking), slash on attack, inspect on right click, and a spin on the
  swap-hands key (F). The spin key is swallowed before vanilla sees it, so nothing goes to your offhand.
  The knife only shows while you hold a sword (`holding-only`). Your own arm stays visible (`show-arm`): the sword
  is swapped for the bare arm, the knife overlay takes its place.
- `frosted-ambience` – themes Tundra, Blizzard, Aurora, Glacier, Midnight. Snowfall with turning wind and gusts,
  ice sparkles, a frosted screen edge, and sky effects (tint, stars, aurora, light rays, whiteout streaks).
  Stars and aurora are placed by direction, so they slide as you turn and show when you look up.
  The sky and cloud colours go through Meteor's `ambience` module and are restored when you turn this off.
- `jump-circles` – growing snowflake with thick arms, a hex web and sparkling tips.
- `hit-particles` – crit, snowflake burst, expanding ice ring, glitter and an optional icy hit sound.
- `frosted-effects` – ice burst when a totem pops, snow dust while running, visible breath outdoors,
  glittering trails on ender pearls and arrows.
- `custom-crosshair` – Plus, Dot, X, Ring, Snowflake; outline, thickness, gap, dynamic spread while moving/attacking.

## Updater
`frosted-updater` (Util) updates the addon from GitHub releases.
1. Put your repository into the `repo` setting (`owner/name`). Releases must contain the jar as an asset.
2. On startup (if `auto-check` is on) it looks for a newer release. On the title screen it asks
   *Update now / Not now*. Click the module itself to check on demand.
3. After a yes it downloads `frosted-X.Y.Z.jar.update` into your mods folder, checks that the file is really
   this mod for your Minecraft version (and its SHA-256 when GitHub provides one), then asks
   *Restart now / Later*.
4. When the game closes, a small helper moves the old jar to `*.jar.bak` and puts the new one in place;
   *Restart now* also starts the game again (Linux/macOS; on Windows start it yourself).

Safety: nothing is installed without your yes, only `https://github.com/<repo>/releases/download/...` assets are
accepted, and the running jar is never touched. The update lives in a `.update` file Fabric ignores, so you
can never end up with two Frosted jars. One backup is kept. Whoever controls the repo controls what gets
installed, so protect that account.

Publish a release: `fish publish.fish` (needs the GitHub CLI `gh`, logged in).

## Textures
`Frosted-Textures.zip` is a separate resource pack (put it in `.minecraft/resourcepacks`): icy iron/diamond/netherite
pickaxes, swords, axes, shovels, hoes and armor icons, totem of undying, elytra (inventory icon), ender pearl and
the golden apples. Worn armor and the worn elytra are not included.

## HUD
`frosted-hud` (Util) draws frosted panels: coords, session, ping, armour, staff and watermark.
Open chat, hold left click on a panel and drag it; the position is saved in the module settings.
Each panel can be switched on/off, the look (corner radius, padding, alpha, snow cap, shimmer, accent) is shared.
The staff panel shows the names from `staff-list` and only appears while someone is online.

## Build
```bash
gradle build
# build/libs/frosted-1.4.0.jar
```
Put the jar into `.minecraft/mods` next to Meteor Client for 1.21.11.

## Layout
```
src/main/java/com/manu/frosted/
  FrostedAddon.java      entrypoint, categories, registration
  modules/hud/           frosted-hud (draggable panels)
  update/                self-updater (GitHub releases, download, verify, swap)
  modules/basehunt/      chunk and underground scanners
  modules/combat/        combat helpers
  modules/camera/        freecam, freelook
  modules/util/          death coords, staff list, region map
  modules/visual/        cosmetic modules
  mixin/                 camera, crosshair, knife, category header
  modules/visual/knife/  knife models, finishes and the polygon renderer
  util/                  AimUtil, Rand, Draw2D
```

## Notes
- Mixins are non-fatal (`defaultRequire: 0`): if one stops applying after a Meteor or
  Minecraft update, that effect disappears but the game still starts.
- `SnowCategoryMixin` reads WWindow's `header` through reflection; if that fails it falls back to a text-height guess.
  Besides the animated header it draws an icy frame, a soft glow and a snow drift on every Frosted window.
- Servers such as DonutSMP may forbid combat automation. Check the rules before enabling it.
