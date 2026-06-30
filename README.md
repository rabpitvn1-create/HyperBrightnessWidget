# HyperBrightnessWidget

Android app for Redmi/HyperOS screen brightness control.

Current scope:

- Manual raw brightness control: 20/30/40/50/60%.
- Quick Settings tile cycles brightness levels.
- Background service can auto-start after boot or package update.
- Light sensor is used only for too-dark / too-bright handling and one-time return to normal.

## Raw brightness levels

- 20% = raw 11
- 30% = raw 17
- 40% = raw 26
- 50% = raw 38
- 60% = raw 49

## Light sensor hysteresis

State `NORMAL`:

- `lux < 35` -> state `TOO_DARK`, apply 20%, raw 11.
- `lux >= 800` -> state `TOO_BRIGHT`, apply 60%, raw 49.
- `35 <= lux < 800` -> do nothing, user can adjust manually.

State `TOO_DARK`:

- `lux > 70` -> return to `NORMAL`, apply 30%, raw 17 once.
- `lux <= 70` -> keep current state.

State `TOO_BRIGHT`:

- `lux < 550` -> return to `NORMAL`, apply 30%, raw 17 once.
- `lux >= 550` -> keep current state.

## Build on GitHub

Open **Actions** -> **Build APK** -> **Run workflow**.

The output APK is uploaded as artifact:

`RedmiScreenBrightness-debug-apk`

APK path inside the artifact:

`app-debug.apk`

## Permission

The app requires Android's special **Modify system settings** permission before it can write brightness values. Open the app once after installation and grant the permission.
