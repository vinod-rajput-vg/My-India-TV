from pathlib import Path

path = Path("app/src/main/java/com/myindiatv/MainActivity.kt")
text = path.read_text(encoding="utf-8")

replacements = {
    '"Imfotainment"': '"Infotainment"',
    '"Musics"': '"Music"',
    'Channel("&TV HD", "http://skyfilex.fun:80/live/5axHnPxfJG/automatic8meet/98852.ts", iconResId = iconId("and_tv_hd"))\n            Channel("Sony Television HD"':
    'Channel("&TV HD", "http://skyfilex.fun:80/live/5axHnPxfJG/automatic8meet/98852.ts", iconResId = iconId("and_tv_hd")),\n            Channel("Sony Television HD"',
}

for old, new in replacements.items():
    text = text.replace(old, new)

path.write_text(text, encoding="utf-8")
