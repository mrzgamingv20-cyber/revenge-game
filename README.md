# Revenge

Game Android: visual-novel bercabang (skor "humanity", C++ via JNI) yang diselingi
level aksi FPS bergaya **raycasting** (kayak Doom/Pink Valley) tiap kali cerita
berpindah lokasi. Semua render 3D-nya murni Kotlin + `Canvas` (algoritma DDA
klasik), tidak butuh OpenGL atau aset gambar — tekstur dinding & sprite musuh
digambar procedural di kode.

## Alur game

1. Node cerita `0` (keputusan awal) tampil seperti biasa.
2. Sebelum node `1` muncul → level aksi **"Gudang Pabrik Handoko"**: bunuh 3
   penjaga untuk lanjut.
3. Node `1` tampil, lalu sebelum node `2` → level aksi **"Rumah Handoko"**: 4
   pengawal.
4. Node `2` (konfrontasi final) tampil, pilihan mengarah ke ending (native,
   tidak berubah).

Kalau player mati di tengah level, level itu auto-restart (posisi & musuh
direset) — tidak menghukum jalur cerita, supaya alur tetap jalan terus.

## Kontrol di layar aksi

- **Joystick virtual** (tap & drag di separuh kiri layar): jalan maju/mundur/
  geser samping.
- **Swipe di separuh kanan layar**: menoleh/putar arah pandang.
- **Tombol TEMBAK** (kanan bawah): tembak lurus ke arah pandang (hitscan,
  butuh musuh ada dalam sudut & jarak tertentu + tidak terhalang dinding).

## Cara jalanin dari Termux

```bash
cd revenge-game
git init
git add .
git commit -m "init revenge game"
git remote add origin https://github.com/mrzgamingv20-cyber/revenge-game.git
git push -u origin main
```

Lalu buka tab **Actions** di GitHub, tunggu workflow selesai, download APK dari
artifact `revenge-debug-apk`.

## Cara nambah cerita baru (native)

Semua node ada di `story_engine.cpp`, fungsi `buildStory()`. Setiap node:

```cpp
nodes[ID] = StoryNode{
    ID,
    "Teks cerita di layar ini...",
    {
        {"Teks tombol pilihan 1", TARGET_ID_1, humanityDelta1},
        {"Teks tombol pilihan 2", TARGET_ID_2, humanityDelta2},
    }
};
```

- `humanityDelta` positif = pilihan lebih manusiawi/damai, negatif = lebih kejam.
- Node dengan id `999` (`FINAL_GATE_ID`) itu penanda "hitung ending sekarang" —
  arahkan pilihan terakhir ke `999`, dan edit ambang batas skor di
  `buildEndingText()` untuk menambah/mengubah ending.

## Cara nambah/edit level aksi

Map level ada di `Levels.kt`, digambar pakai string grid (`#` = dinding bata,
`=` = dinding kayu/crate, `.` = lantai kosong), plus titik spawn musuh dan
posisi awal player. `MainActivity.kt` yang menentukan node cerita mana yang
memicu level mana (lihat `proceedToCurrentNode()`), jadi kalau nambah node
cerita baru dan mau kasih level aksi baru sebelum node itu tampil, tambahkan
kondisi baru di situ + map baru di `Levels.kt`.

## Struktur singkat

- `story_engine.h/.cpp`, `native_bridge.cpp` — cerita & skor (C++ via JNI, tidak diubah)
- `MainActivity.kt` — orkestrasi: kapan tampil cerita vs kapan mainkan level aksi
- `activity_main.xml` — layout root (story container + game container)
- `Raycaster.kt` — mesin raycasting (DDA), render dinding bertekstur + sprite musuh billboard
## Developer Tools

Tombol **DEV · Tools** di layar cerita membuka:

1. **Cutscene Maker** — susun shot (teks, durasi, karakter), simpan JSON, export **MP4** ke folder Download.
2. **Asset Settings** — ganti sprite musuh, senjata, karakter cerita, tekstur dinding (`#`/`=`/`~`); bisa import PNG.
3. **Map Editor** — paint tile, drag player/enemy, putar map 90°, putar arah player; simpan sebagai map custom level 1/2.

- `GameModel.kt` — data class `Player`, `Enemy`, `LevelMap`
- `Levels.kt` — layout map & spawn musuh tiap level aksi
- `GameView.kt` — loop game, kontrol sentuh (joystick/swipe/tembak), AI musuh sederhana, HUD
