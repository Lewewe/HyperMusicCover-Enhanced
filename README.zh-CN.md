<p align="center"><a href="README.md">English</a> · <strong>简体中文</strong></p>

<div align="center">
  <img src="docs/images/app-icon.png" alt="HyperMusicCover-Enhanced 应用图标" width="128" height="128" />
  <h1>HyperMusicCover-Enhanced</h1>
  <p><strong>为 HyperOS 4 锁屏带来大封面、歌词和音频可视化。</strong></p>
  <p>
    <a href="https://github.com/Lewewe/HyperMusicCover-Enhanced/releases"><img alt="下载" src="https://img.shields.io/github/v/release/Lewewe/HyperMusicCover-Enhanced?style=flat-square&amp;label=Download&amp;color=f38caf" /></a>
    <a href="https://t.me/HyperMusicCoverEnhanced"><img alt="官方 Telegram 群组" src="https://img.shields.io/badge/Telegram-Official_group-26A5E4?style=flat-square&amp;logo=telegram" /></a>
    <a href="LICENSE"><img alt="AGPL-3.0 许可证" src="https://img.shields.io/badge/License-AGPL--3.0-ffaaa3?style=flat-square" /></a>
  </p>
</div>

基于 [HyperMusicCover](https://github.com/zyl6932/HyperMusicCover) 的 LSPosed 模块分支。

## 功能

| | |
| --- | --- |
| 🎨 **大封面** | 全屏封面，切歌过渡流畅，也可使用自己的壁纸作背景 |
| 🎤 **歌词** | 同步歌词、逐词歌词、翻译、字体包和歌词效果 |
| 📊 **音频可视化** *(Beta)* | 超级岛和播放器上随音乐跳动的频谱，静音时同样有效 |
| 🎛️ **播放器外观** | 封面形状、背景、材质、配色和歌名滚动 |
| 🎬 **Spotify Canvas** | 通过可选扩展 [HyperCanvas](https://github.com/Lewewe/HyperCanvas) 显示视频背景 |
| 🔔 **通知与 AOD** | 适配通知堆叠、胶囊模式和息屏显示 |

完整更新内容见[发布页](https://github.com/Lewewe/HyperMusicCover-Enhanced/releases)。

## 安装

**要求：** 已 Root 的 HyperOS 4（Android 15+），以及支持现代 Xposed API 的 LSPosed。

1. 从 [Releases](https://github.com/Lewewe/HyperMusicCover-Enhanced/releases) 下载并安装 APK。
2. 在 LSPosed 中启用模块并勾选推荐作用域，然后重启。
3. 打开应用进行设置。

使用 Spotify Canvas：进入 **Extensions → HyperCanvas → Download**，按应用内提示完成设置。

## 反馈

主要在 Xiaomi 17 Pro 上测试。欢迎在 [Telegram 群组](https://t.me/HyperMusicCoverEnhanced) 交流，或[提交 Issue](https://github.com/Lewewe/HyperMusicCover-Enhanced/issues)，并附上设备、HyperOS 版本、模块版本和音乐应用。

## 构建

```sh
git clone --recurse-submodules https://github.com/Lewewe/HyperMusicCover-Enhanced.git
cd HyperMusicCover-Enhanced
./gradlew :app:assembleRelease :hyper-canvas:assembleRelease
```

需要 Android SDK 37。HyperMusicCover-Enhanced 与 HyperCanvas 必须使用相同签名（`keystore.properties`）。

## 致谢

- [Lewewe](https://github.com/Lewewe)：分支开发
- [puhboo](https://github.com/puhboo)：歌词功能及 HyperCanvas 渲染器贡献
- [zyl6932](https://github.com/zyl6932/HyperMusicCover)：原始 HyperMusicCover 项目
- [juren233 / HyperLyrics-Enhanced](https://github.com/juren233/HyperLyrics-Enhanced)：播放器外观功能基础（[详情](docs/media-card-customization.md)）
- [miuix](https://github.com/compose-miuix-ui/miuix) 及应用内致谢页列出的其他库
- [Material Design Icons](https://github.com/google/material-design-icons)：歌词按钮图标中的音符（Apache-2.0）

支持开发：[Ko-fi](https://ko-fi.com/yzc26623)（国际）· [爱发电](https://afdian.com/a/yzc26623)（中国大陆）· 许可证：[AGPL-3.0](LICENSE)
