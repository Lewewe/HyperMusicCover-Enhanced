# HyperMusicCover island demos

Two seamless Remotion loops show the lock-screen island styles at 1140 x 336 px
(380 x 112 dp at 3x), 60 fps. Pixels outside the 60 px rounded frame are transparent.
Rest geometry uses disc diameter 0.42 * H, row centre 0.56 * H (188.16 px),
20 dp side margins and 22 dp gaps. The background is a dark gradient with soft glow.
A translucent finger appears only for press, move and release gestures.
Music uses orange-to-pink artwork, two bars and a pause glyph; messages use a green
avatar and two bars. The small HyperOS notification island is a plain blue disc.

| Composition | Duration | Frames |
| --- | --- | --- |
| `IslandColorOS` | 7 seconds | 420 |
| `IslandHyperOS` | 5.4 seconds | 324 |

ColorOS scenes:

1. Music capsule with brackets (0-1 s).
2. Incoming message: inward width dip, spring recovery, outgoing scale/fade/blur,
   incoming focus, then a scrolling long title and `3+` badge (1-3 s).
3. Finger swipes right to music (3-3.7 s), holds (3.7-4.3 s), then swipes left
   to messages (4.3-5 s) and holds (5-5.6 s). Swipes keep the original goo geometry
   and cubic-bezier(0.3, 0, 0.15, 1).
4. Swipe right to music (5.6-6.3 s), then rest matching the opening (6.3-7 s).

HyperOS scenes:

1. Music alone (0-0.8 s).
2. Spring narrowing and small notification appearance about its own centre:
   scale 0.6 to 1, content blur 8 dp to 0, fade in (0.8-1.7 s).
3. Tap the small notification to promote it (1.7-2.3 s); its long title scrolls
   with `3+` while music takes the small slot (2.3-3.1 s).
4. Tap to swap back (3.1-3.7 s), settling until 4 s.
5. Reverse the small island appearance and widen music (4-4.7 s), then rest
   matching the opening (4.7-5.4 s).

Springs use Remotion `spring()` with Android damping/response values converted to
mass-one parameters. `src/colorOsPill.ts` remains the geometry source; shared capsule
content lives in `src/IslandCards.tsx`. The app plays the rendered files in
`IslandDemo.kt` on the lock screen super island page.

```sh
npm run studio
npm run render
```

Rendering requires ffmpeg on PATH with `libwebp_anim` support. The render script
bundles the project, writes PNG frames to `out/IslandColorOS/frame-%04d.png` and
`out/IslandHyperOS/frame-%04d.png`, then creates looping WebP files with alpha:

- `out/island_demo_coloros.webp`
- `out/island_demo_hyperos.webp`

After rendering, copy these two `.webp` files into `../../app/src/main/res/raw/`. Copying is manual; the script writes only inside this project.
No rendered assets are included.

`src/colorOsPill.ts` ports the module's geometry and constants under AGPL-3.0-only.
SVG compound paths replace path unions; the ear/capsule intersections use finely
sampled arcs instead of Android's path boolean engine. The original 48-sample
metaball trace is preserved. JavaScript uses double precision instead of JVM floats.

Type-check with `npx tsc --noEmit`. Geometry, all 744 scene frame trees, and exact loop endpoints can be checked without rendering:

```sh
node check.mjs
```
