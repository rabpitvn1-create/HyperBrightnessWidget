from pathlib import Path
import base64
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / "build"
DIST = ROOT / "dist"
BUILD.mkdir(exist_ok=True)
DIST.mkdir(exist_ok=True)

base_apk = BUILD / "RedmiScreenBrightness_base.apk"
manifest_xml = BUILD / "AndroidManifest_patched.xml"
classes3_dex = BUILD / "classes3.dex"
unsigned_apk = DIST / "RedmiScreenBrightness_sensor_hysteresis_35_70_550_800_unsigned.apk"

base_apk.write_bytes(base64.b64decode((ROOT / "base" / "RedmiScreenBrightness.apk.b64").read_text().strip()))
manifest_xml.write_bytes(base64.b64decode((ROOT / "patches" / "AndroidManifest_patched.xml.b64").read_text().strip()))
classes3_dex.write_bytes(base64.b64decode((ROOT / "patches" / "classes3.dex.b64").read_text().strip()))

with zipfile.ZipFile(base_apk, "r") as zin, zipfile.ZipFile(unsigned_apk, "w", zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        if item.filename.startswith("META-INF/"):
            continue
        if item.filename in {"AndroidManifest.xml", "classes3.dex"}:
            continue
        data = zin.read(item.filename)
        zi = zipfile.ZipInfo(item.filename)
        zi.date_time = item.date_time
        zi.compress_type = zipfile.ZIP_DEFLATED
        zout.writestr(zi, data)

    zout.writestr("AndroidManifest.xml", manifest_xml.read_bytes())
    zout.writestr("classes3.dex", classes3_dex.read_bytes())

print(f"Wrote {unsigned_apk}")
