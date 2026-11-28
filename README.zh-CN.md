<p align="center"><a href="README.md">English</a> · <strong>简体中文</strong></p>

<div align="center">
  <img src="docs/images/app-icon.png" alt="HyperMusicCover-Enhanced 应用图标" width="128" height="128" />
  <h1>HyperMusicCover-Enhanced</h1>
  <p><strong>为 HyperOS 4 锁屏带来大封面、歌词和音频可视化。</strong></p>
  <p>
    <a href="https://github.com/Lewewe/HyperMusicCover-Enhanced/releases"><img alt="下载" src="https://img.shields.io/github/v/release/Lewewe/HyperMusicCover-Enhanced?style=flat-square&amp;label=Download&amp;color=f38caf" /></a>
    <a href="https://t.me/HyperMusicCoverEnhanced"><img alt="官方 Telegram 群组" src="https://img.shields.io/badge/Telegram-Official_group-26A5E4?style=flat-square&amp;logo=telegram" /></a>
    <a href="https://t.me/HyperMusicCoverEnhancedChannel"><img alt="官方 Telegram 频道" src="https://img.shields.io/badge/Telegram-Official_channel-26A5E4?style=flat-square&amp;logo=telegram" /></a>
    <a href="LICENSE"><img alt="AGPL-3.0 许可证" src="https://img.shields.io/badge/License-AGPL--3.0-ffaaa3?style=flat-square" /></a>
  </p>
</div>

一个把锁屏变成音乐播放界面的 LSPosed 模块，最初分支自 [HyperMusicCover](https://github.com/zyl6932/HyperMusicCover)。

## 功能

| | |
| --- | --- |
| 🎨 **大封面** | 封面铺满全屏，切歌时平滑过渡。也可以换成自己的壁纸垫在后面 |
| 🕐 **时钟显示在日期左侧** | 展开大封面或歌词时，时钟缩小到日期左边，息屏时也保持这样 |
| 🌈 **流动背景** | 封面里的颜色在大封面后面慢慢流动，可选渐变或网格渐变 |
| 🎤 **歌词** | 同步歌词、逐词歌词、翻译、字体包和歌词效果 |
| (・ω・) **歌词伙伴颜文字** | 没有歌词也没开大封面时，会有个颜文字跟着节拍晃 |
| 📊 **音频可视化** *(Beta)* | 胶囊和播放器上的频谱跟着音乐跳，音量调到零也照样动 |
| 📱 **iPhone-ify** *(A/B)* | 长按音乐或其他实时活动的胶囊，摄像头位置会弹出 iPhone 风格的卡片，盖在其他胶囊上面 |
| 🎛️ **播放器外观** | 封面形状、背景、材质、配色和歌名滚动 |
| 🔔 **通知与 AOD** | 和通知堆叠、HyperOS 与 ColorOS 风格胶囊、息屏显示都能配合 |
| 📲 **折叠屏** | 小米 MIX Flip 的内外两块屏幕都能显示大封面和背景 |
| ⬆️ **一键更新** | 有新版本时，主页卡片直接帮你装好；有 Root 的话还会顺手重启作用域 |

每次的改动都写在[发布页](https://github.com/Lewewe/HyperMusicCover-Enhanced/releases)。本页跟着 `main` 分支走，这里列出的功能可能要到下一个版本才有。

## 扩展

扩展是独立的应用，负责模块本身做不了的事，直接在应用里安装。

| | |
| --- | --- |
| <img src="docs/images/extensions/hypercanvas.png" alt="" width="24" height="24" align="center" /> **[HyperCanvas](https://github.com/Lewewe/HyperCanvas)** | Spotify 锁屏动态背景 |
| <img src="docs/images/extensions/hyperimmersive.png" alt="" width="24" height="24" align="center" /> **[HyperImmersive](https://github.com/Lewewe/HyperImmersive)** *(Alpha)* | 待机显示：手机横放充电时显示全屏时钟、音乐与小组件 |
| <img src="docs/images/extensions/hyperrear.png" alt="" width="24" height="24" align="center" /> **[HyperRear](https://github.com/Lewewe/HyperRear)** *(Alpha)* | 小米 17 Pro 系列背屏：为所有音乐应用提供同一个播放器，带 HMC-E 的歌词与频谱 |
| <img src="docs/images/extensions/hyperocean.png" alt="" width="24" height="24" align="center" /> **HyperOcean** *(即将推出)* | 在锁屏下半部分显示 OriginOS 风格面板 |

## 安装

**要求：** 已 Root 的 HyperOS 4（Android 15+），以及支持现代 Xposed API 的 LSPosed。

1. 从 [Releases](https://github.com/Lewewe/HyperMusicCover-Enhanced/releases) 下载并安装 APK。
2. 在 LSPosed 中启用模块并勾选推荐作用域，然后重启。
3. 打开应用进行设置。

安装扩展：打开 **Extensions** 页，选 HyperCanvas、HyperImmersive 或 HyperRear，点 **Download**，再按提示操作。

## 反馈

平时主要在小米 17 Pro 和小米 MIX Flip 上测试，别的机型可能会遇到我们没见过的问题。更新消息发在 [Telegram 频道](https://t.me/HyperMusicCoverEnhancedChannel)，有问题可以去 [Telegram 群组](https://t.me/HyperMusicCoverEnhanced) 问。报 bug 请[提交 Issue](https://github.com/Lewewe/HyperMusicCover-Enhanced/issues)，写上设备、HyperOS 版本、模块版本和音乐应用。

## 构建

```sh
git clone --recurse-submodules https://github.com/Lewewe/HyperMusicCover-Enhanced.git
cd HyperMusicCover-Enhanced
./gradlew :app:assembleRelease :hyper-canvas:assembleRelease
```

需要 Android SDK 37。[HyperImmersive](https://github.com/Lewewe/HyperImmersive) 和 [HyperRear](https://github.com/Lewewe/HyperRear) 在各自的仓库里构建。四个应用要用同一个签名（`keystore.properties`），因为模块只给签名和自己一样的扩展提供数据。

## 致谢

- [Lewewe](https://github.com/Lewewe)：分支开发
- [puhboo](https://github.com/puhboo)：歌词功能及 HyperCanvas 渲染器贡献
- [zyl6932](https://github.com/zyl6932/HyperMusicCover)：原始 HyperMusicCover 项目
- [juren233 / HyperLyrics-Enhanced](https://github.com/juren233/HyperLyrics-Enhanced)：播放器外观功能基础（[详情](docs/media-card-customization.md)）
- [miuix](https://github.com/compose-miuix-ui/miuix) 及应用内致谢页列出的其他库
- [Material Design Icons](https://github.com/google/material-design-icons)：歌词按钮图标中的音符（Apache-2.0）

支持开发：[Ko-fi](https://ko-fi.com/yzc26623)（国际）· [爱发电](https://afdian.com/a/yzc26623)（中国大陆）· 许可证：[AGPL-3.0](LICENSE)
