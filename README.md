<p align="center"><strong>English</strong> · <a href="README.zh-CN.md">简体中文</a></p>

<div align="center">
  <img src="docs/images/app-icon.png" alt="HyperMusicCover-Enhanced app icon" width="128" height="128" />
  <h1>HyperMusicCover-Enhanced</h1>
  <p><strong>Big artwork, lyrics and audio visualizers on the HyperOS 4 lock screen.</strong></p>
  <p>
    <a href="https://github.com/Lewewe/HyperMusicCover-Enhanced/releases"><img alt="Download" src="https://img.shields.io/github/v/release/Lewewe/HyperMusicCover-Enhanced?style=flat-square&amp;label=Download&amp;color=f38caf" /></a>
    <a href="https://t.me/HyperMusicCoverEnhanced"><img alt="Official Telegram group" src="https://img.shields.io/badge/Telegram-Official_group-26A5E4?style=flat-square&amp;logo=telegram" /></a>
    <a href="https://t.me/HyperMusicCoverEnhancedChannel"><img alt="Official Telegram channel" src="https://img.shields.io/badge/Telegram-Official_channel-26A5E4?style=flat-square&amp;logo=telegram" /></a>
    <a href="LICENSE"><img alt="License AGPL-3.0" src="https://img.shields.io/badge/License-AGPL--3.0-ffaaa3?style=flat-square" /></a>
  </p>
</div>

An LSPosed module that turns the lock screen into a now-playing screen. It began as a fork of [HyperMusicCover](https://github.com/zyl6932/HyperMusicCover).

## Features

| | |
| --- | --- |
| 🎨 **Big cover art** | The artwork fills the screen and crossfades when the track changes. Your own wallpaper can sit behind it instead |
| 🕐 **Clock beside the date** | The clock shrinks to sit left of the date while the big cover or lyrics are open, and stays that way on the always-on display |
| 🌈 **Moving backdrop** | Colours from the artwork drift slowly behind the big cover, as a gradient or a mesh gradient |
| 🎤 **Lyrics** | Synced and word-timed lyrics, translation, font packs and effects |
| (・ω・) **Kaomoji companion** | When neither lyrics nor the big cover are up, a little face bops to the beat |
| 📊 **Audio visualizer** *(beta)* | Bars on the capsule and the player move with the music, even with the volume at zero |
| 📱 **iPhone-ify** *(A/B)* | Long-press the music capsule, or any live activity, and an iPhone-style card opens at the camera on top of the other capsules |
| 🎛️ **Player styling** | Artwork shape, backgrounds, materials, colors and scrolling titles |
| 🔔 **Notifications & AOD** | Plays well with the notification stack, HyperOS and ColorOS-style capsules, and the always-on display |
| 📲 **Foldables** | On the Xiaomi MIX Flip the big cover and its backdrop work on both screens |
| ⬆️ **One-tap updates** | When a new release is out, a card on Home installs it. With root, it also restarts the scope for you |

Every change is in the [release notes](https://github.com/Lewewe/HyperMusicCover-Enhanced/releases). This page tracks `main`, so a feature listed here may only land in the next release.

## Extensions

Extensions are separate apps for things the module can't do on its own. You install them from inside the app.

| | |
| --- | --- |
| <img src="docs/images/extensions/hypercanvas.png" alt="" width="24" height="24" align="center" /> **[HyperCanvas](https://github.com/Lewewe/HyperCanvas)** | Spotify Canvas on your lock screen |
| <img src="docs/images/extensions/hyperimmersive.png" alt="" width="24" height="24" align="center" /> **[HyperImmersive](https://github.com/Lewewe/HyperImmersive)** *(alpha)* | StandBy: a full-screen clock, music and widgets while the phone charges on its side |
| <img src="docs/images/extensions/hyperrear.png" alt="" width="24" height="24" align="center" /> **[HyperRear](https://github.com/Lewewe/HyperRear)** *(alpha)* | Xiaomi 17 Pro series rear screen: one music player for every app, with HMC-E's lyrics and visualizer |
| <img src="docs/images/extensions/hyperocean.png" alt="" width="24" height="24" align="center" /> **HyperOcean** *(coming soon)* | An OriginOS-style panel for the lower half of the lock screen |

## Install

**Requires:** rooted HyperOS 4 (Android 15+) with LSPosed (modern Xposed API).

1. Download the APK from [Releases](https://github.com/Lewewe/HyperMusicCover-Enhanced/releases) and install it.
2. Enable the module in LSPosed with its recommended scopes, then reboot.
3. Open the app to set it up.

To add an extension, open the **Extensions** tab, pick HyperCanvas, HyperImmersive or HyperRear, tap **Download**, and follow the steps it shows.

## Feedback

Most testing happens on a Xiaomi 17 Pro and a Xiaomi MIX Flip, so other phones may turn up things we haven't seen. News goes out on the [Telegram channel](https://t.me/HyperMusicCoverEnhancedChannel). For questions, there's the [Telegram group](https://t.me/HyperMusicCoverEnhanced). To report a bug, [open an issue](https://github.com/Lewewe/HyperMusicCover-Enhanced/issues) and include your device, HyperOS version, module version and music app.

## Build

```sh
git clone --recurse-submodules https://github.com/Lewewe/HyperMusicCover-Enhanced.git
cd HyperMusicCover-Enhanced
./gradlew :app:assembleRelease :hyper-canvas:assembleRelease
```

You'll need Android SDK 37. [HyperImmersive](https://github.com/Lewewe/HyperImmersive) and [HyperRear](https://github.com/Lewewe/HyperRear) build from their own repositories. Sign all four apps with the same key (`keystore.properties`): the module only serves extensions signed like itself.

## Credits

- [Lewewe](https://github.com/Lewewe): fork development
- [puhboo](https://github.com/puhboo): lyrics and HyperCanvas renderer contributions
- [zyl6932](https://github.com/zyl6932/HyperMusicCover): original HyperMusicCover
- [juren233 / HyperLyrics-Enhanced](https://github.com/juren233/HyperLyrics-Enhanced): player styling foundation ([details](docs/media-card-customization.md))
- [miuix](https://github.com/compose-miuix-ui/miuix) and the other libraries listed in the app's Credits page
- [Material Design Icons](https://github.com/google/material-design-icons): the music note in the lyrics button's icon (Apache-2.0)

Support development: [Ko-fi](https://ko-fi.com/yzc26623) (international) · [Afdian](https://afdian.com/a/yzc26623) (mainland China) · License: [AGPL-3.0](LICENSE)
