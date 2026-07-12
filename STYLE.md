# Kryptos — Visual Style Guide

## Theme
Sci-fi / high-tech. Think: precision-engineered alloy hulls, cold energy cores,
minimal ornamentation. Kryptos content should feel like it was built by a
faction with far more advanced fabrication tech than the vanilla factions —
clean edges, glowing seams, no rust or grime.

## Color Palette

| Role              | Hex       | Use |
|--------------------|-----------|-----|
| Hull base (dark)   | `#2b2f3a` | Primary armor plating |
| Hull base (light)  | `#454b5c` | Panel highlights, bevels |
| Hull outline       | `#14151c` | Standard Mindustry 1px black-ish outline |
| Energy accent      | `#4ce0ff` | Vents, weapon glow, power conduits |
| Energy accent (hot)| `#8ff5ff` | Core glow, muzzle flash, bright highlights |
| Warning / weapon   | `#ff5a4c` | Heat sinks, danger markers, projectile trims |
| Structural steel   | `#7b8494` | Secondary metal, joints, treads |

Rule of thumb: ~70% dark hull tones, ~20% structural steel, ~10% cyan energy
accent used only where the object is functionally "active" (vents, cores,
optics, weapon barrels). Never let the accent color cover large flat areas —
it reads best as thin lines, small glow points, or a single core/eye.

## Sprite Conventions (match vanilla Mindustry rules)
- **Format:** PNG, 32-bit RGBA (no 16-bit — causes pixmap decode errors).
- **Block sizing:** `32px * size` per side (e.g. a 2x2 block = 64x64).
- **Units:** base body roughly 24–40px depending on tier; weapons as separate
  layered sprites if using outline-style multi-part units.
- **Outline:** 1px dark outline (`#14151c`-ish, not pure black) around all
  silhouettes, consistent with vanilla content.
- **Folder mapping:** first subfolder under `sprites/` sets the texture atlas
  page — keep `sprites/blocks`, `sprites/units`, `sprites/items` separate so
  nothing lags the atlas packer.

## Tiering (for naming/progression consistency)
1. **Tier 1 — "Probe"**: scrappy, small, mostly steel with thin cyan seams.
2. **Tier 2 — "Sentinel"**: full hull plating, visible energy core, symmetric design.
3. **Tier 3 — "Warden"**: heavier silhouette, glowing weapon ports, layered armor.
4. **Tier 4 — "Apex"**: capital-scale, dominant cyan glow, asymmetric detailing allowed.

## Naming conventions
- Items: short, mineral/element-inspired or synthetic-sounding (`Voidsteel`, `Cryonite`).
- Blocks: functional + faction word (`Kryptos Smelter`, `Cryo Vault`).
- Units: single evocative word per tier list above, prefixed if needed (`Kryptos Sentinel`).

## What to avoid
- No rust, dirt, or organic textures — this faction fabricates, it doesn't scavenge.
- No pure black or pure white — always slightly tinted to keep it cohesive with vanilla's palette.
- Don't overuse the red warning accent — reserve it for heat/danger cues only.
