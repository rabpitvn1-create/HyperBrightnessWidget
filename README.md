# HyperBrightnessWidget

Clean rebuild from zero for Redmi Turbo 5 Max / Chinese HyperOS.

## Current scope

- Manual raw brightness control only
- Tested raw levels: `11, 17, 26, 38, 49`
- Hard cap: `55`
- Quick Settings Tile cycles through the tested raw levels
- Main app screen is only for permission, status, and manual next-level apply
- Uses `WRITE_SETTINGS`

## Not included yet

- No lux curve
- No light sensor policy
- No auto brightness algorithm
- No learning
- No AI / LiteRT

## Baseline behavior

1. Open the app and grant `WRITE_SETTINGS`.
2. Add the Quick Settings tile named `Hyper Brightness`.
3. Tap the tile to move to the next raw level: `11 -> 17 -> 26 -> 38 -> 49 -> 11`.
4. Every write forces system brightness mode to manual before writing `SCREEN_BRIGHTNESS`.

Goal: build a stable raw baseline before adding any multi-light-sensor or auto-brightness logic.
