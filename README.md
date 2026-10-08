# Nusantara Arena ⚔️

MOBA 2D orisinal untuk Android — **5v5 full offline melawan bot**, dengan mekanisme ala MLBB (lobby → pick hero → battle → result), tapi semua hero, map, dan aset 100% orisinal.

## 🎮 Fitur

- **3 lane** (atas, tengah, bawah), 2 tower per lane per tim + Nexus
- **5v5**: kamu + 4 bot sekutu vs 5 bot musuh
- **3 hero orisinal**, masing-masing punya basic attack + 2 skill + ultimate:
  - 🔥 **Bara** — Warrior api (tebasan + dash + meteor)
  - 🌙 **Wulan** — Marksman (panah cepat + hujan panah)
  - 🌪️ **Bayu** — Mage angin (tornado + badai)
- **Bot AI**: farming minion, mundur saat sekarat, jaga jarak dari tower, pakai skill
- Minion wave, XP/level, respawn, minimap
- **Kontrol**: joystick kiri, tombol attack/skill kanan (landscape)
- SFX sintetis via `AudioTrack` — tanpa file audio
- 🔒 Full offline — tanpa izin `INTERNET`

## 🛠️ Build

```bash
./gradlew assembleDebug
```

APK hasil build ada di `app/build/outputs/apk/debug/`. CI (GitHub Actions) otomatis build tiap push ke `main`.

## 🗺️ Roadmap

- [ ] Hero & role tambahan (Tank, Assassin, Support)
- [ ] Bot AI lebih pintar (tingkat kesulitan, perilaku kayak pemain)
- [ ] Multiplayer LAN via WiFi hotspot (Nearby Connections API)
- [ ] Multiplayer online + fallback bot

## 📄 Lisensi

Kode © Hozin King. Semua hero, nama, dan desain orisinal — bukan tiruan aset game lain.
