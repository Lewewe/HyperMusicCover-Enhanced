<p align="center"><strong>English</strong> · <a href="README.zh-CN.md">简体中文</a></p>

<div align="center">
  <img src="docs/images/app-icon.png" alt="HyperMusicCover-Enhanced app icon" width="128" height="128" />
  <h1>HyperMusicCover-Enhanced</h1>
  <p><strong>Big artwork, lyrics and audio visualizers on the HyperOS 4 lock screen.</strong></p>
  <p>
    <a href="https://github.com/Lewewe/HyperMusicCover-Enhanced/releases"><img alt="Download" src="https://img.shields.io/github/v/release/Lewewe/HyperMusicCover-Enhanced?style=flat-square&amp;label=Download&amp;color=f38caf" /></a>
    <a href="https://t.me/HyperMusicCoverEnhanced"><img alt="Official Telegram group" src="https://img.shields.io/badge/Telegram-Official_group-26A5E4?style=flat-square&amp;logo=telegram" /></a>
    <a href="LICENSE"><img alt="License AGPL-3.0" src="https://img.shields.io/badge/License-AGPL--3.0-ffaaa3?style=flat-square" /></a>
  </p>
</div>

An LSPosed module forked from [HyperMusicCover](https://github.com/zyl6932/HyperMusicCover).

## Features

| | |
| --- | --- |
| 🎨 **Big cover art** | Full-screen artwork with smooth track changes, or your own wallpaper behind it |
| 🎤 **Lyrics** | Synced and word-timed lyrics, translation, font packs and effects |
| 📊 **Audio visualizer** *(beta)* | Super Island and player bars that follow the music, even at zero volume |
| 🎛️ **Player styling** | Artwork shape, backgrounds, materials, colors and scrolling titles |
| 🎬 **Spotify Canvas** | Video backgrounds through the optional [HyperCanvas](https://github.com/Lewewe/HyperCanvas) extension |
| 🔔 **Notifications & AOD** | Works with the notification stack, pill mode and always-on display |

Full change list: [release notes](https://github.com/Lewewe/HyperMusicCover-Enhanced/releases).

## Install

**Requires:** rooted HyperOS 4 (Android 15+) with LSPosed (modern Xposed API).

1. Download the APK from [Releases](https://github.com/Lewewe/HyperMusicCover-Enhanced/releases) and install it.
2. Enable the module in LSPosed with its recommended scopes, then reboot.
3. Open the app to set it up.

For Spotify Canvas: **Extensions → HyperCanvas → Download**, then follow the steps shown in the app.

## Feedback

Tested mainly on Xiaomi 17 Pro. Ask in the [Telegram group](https://t.me/HyperMusicCoverEnhanced) or [open an issue](https://github.com/Lewewe/HyperMusicCover-Enhanced/issues) with your device, HyperOS version, module version and music app.

## Build

```sh
git clone --recurse-submodules https://github.com/Lewewe/HyperMusicCover-Enhanced.git
cd HyperMusicCover-Enhanced
./gradlew :app:assembleRelease :hyper-canvas:assembleRelease
```

Needs Android SDK 37. HyperMusicCover-Enhanced and HyperCanvas must be signed with the same key (`keystore.properties`).

## Credits

- [Lewewe](https://github.com/Lewewe): fork development
- [puhboo](https://github.com/puhboo): lyrics and HyperCanvas renderer contributions
- [zyl6932](https://github.com/zyl6932/HyperMusicCover): original HyperMusicCover
- [juren233 / HyperLyrics-Enhanced](https://github.com/juren233/HyperLyrics-Enhanced): player styling foundation ([details](docs/media-card-customization.md))
- [miuix](https://github.com/compose-miuix-ui/miuix) and the other libraries listed in the app's Credits page
- [Material Design Icons](https://github.com/google/material-design-icons): the music note in the lyrics button's icon (Apache-2.0)

Support development: [Ko-fi](https://ko-fi.com/yzc26623) (international) · [Afdian](https://afdian.com/a/yzc26623) (mainland China) · License: [AGPL-3.0](LICENSE)
