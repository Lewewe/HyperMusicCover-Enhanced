<p align="center"><a href="README.md">English</a> · <strong>简体中文</strong></p>

<div align="center">
  <img src="docs/images/app-icon.png" alt="HyperMusicCover-Enhanced 应用图标" width="128" height="128" />
  <h1>HyperMusicCover-Enhanced</h1>
  <p><strong>为 HyperOS 4 带来更流畅的锁屏音乐封面体验。</strong></p>
  <p>大封面、丰富的歌词效果、可自定义的播放器，以及随真实音频变化的可视化效果。</p>
  <p>
    <a href="https://github.com/Lewewe/HyperMusicCover-Enhanced/releases"><img alt="下载" src="https://img.shields.io/github/v/release/Lewewe/HyperMusicCover-Enhanced?style=flat-square&amp;label=Download&amp;color=f38caf" /></a>
    <a href="https://t.me/HyperMusicCoverEnhanced"><img alt="官方 Telegram 群组" src="https://img.shields.io/badge/Telegram-Official_group-26A5E4?style=flat-square&amp;logo=telegram" /></a>
    <a href="LICENSE"><img alt="AGPL-3.0 许可证" src="https://img.shields.io/badge/License-AGPL--3.0-ffaaa3?style=flat-square" /></a>
  </p>
  <p>
    <a href="https://github.com/Lewewe/HyperMusicCover-Enhanced/releases">下载 APK</a> ·
    <a href="https://t.me/HyperMusicCoverEnhanced">官方 Telegram 群组</a> ·
    <a href="https://github.com/Lewewe/HyperMusicCover-Enhanced/issues">反馈问题</a> ·
    <a href="https://afdian.com/a/yzc26623">支持开发</a>
  </p>
</div>

---

这是基于 [HyperMusicCover](https://github.com/zyl6932/HyperMusicCover) 独立维护的 LSPosed 分支，主要面向 HyperOS 4。在原有锁屏音乐体验的基础上，改进了封面切换、歌词功能，以及与通知、AOD 和 HyperOS 材质的配合。

## 1.0.0 — 迄今为止最大的一次更新

本次更新带来了完整的播放器外观设置、随真实音频变化的可视化效果、通过 HyperCanvas 实现的 Spotify Canvas 背景、内置扩展管理，以及壁纸背景模式。

**相比 0.3.5 新增：**

- 自定义播放器封面、材质、配色和背景动画，支持圆形封面的形状过渡动画。
- 可单独为通知栏／锁屏播放器和展开后的岛内播放器开启歌曲信息滚动，方便查看较长的歌名与歌手名。
- 超级岛及可选的播放器可视化效果随真实播放音频变化，支持零音量采集、蓝牙时间补偿和低帧率 AOD 显示。
- Spotify Canvas 可与封面、歌词控件协同工作，支持最多三个视频的缓存，并可选择 TextureView 或实验性的 OpenGL ES 渲染器。
- 在扩展列表中下载、更新和卸载 HyperCanvas，查看安装及 Hook 状态。
- 大封面和歌词可使用壁纸作为背景，并调节暗化程度。
- 更新应用图标和关于页面外观，修复封面圆角、背景错图和 Canvas／AOD 切换等问题。

下载 [v1.0.0](https://github.com/Lewewe/HyperMusicCover-Enhanced/releases/tag/v1.0.0)，或查看[完整更新说明（英文）](docs/releases/1.0.0.md)，了解全部变更及兼容性信息。0.3.5 已有的 NextLyrics 和歌词功能仍然保留。

## 功能介绍

### HyperCanvas · 可选扩展

通过独立的 [HyperCanvas](https://github.com/Lewewe/HyperCanvas) APK，将 Spotify Canvas 用作动态锁屏背景。可直接在 **Extensions（扩展）** 页面下载和管理。

- 有 Canvas 的歌曲可在视频背景上显示展开的播放器；没有 Canvas 的歌曲保持普通封面背景。
- 打开大封面或歌词时，Canvas 会平滑地模糊并暗化，播放器上提供独立的歌词切换按钮。
- 暂停音乐时暂停视频；进入 AOD 时冻结并模糊画面；进入密码界面时暂停并模糊。视频和冻结画面不参与系统的 AOD 缩放，避免拉伸和唤醒时的缩放闪烁。
- 调节暗化和模糊程度，选择是否在胶囊模式下显示 Canvas，并保留之前的封面／歌词／播放器状态。
- 最多缓存三个视频，关闭扩展后清空缓存。
- 可使用默认的 TextureView 渲染器，或带有 GPU 模糊及 TextureView 回退机制的实验性 OpenGL ES 渲染器。
- 从 GitHub Releases 下载和更新，查看安装及 Hook 状态，并在设置中通过 Root 卸载扩展。扩展列表显示扩展自身的图标。

**HyperCanvas 仅支持 HyperMusicCover-Enhanced。** 需要单独为 Spotify 和 SystemUI 启用 LSPosed 作用域。安装和兼容性说明见[扩展 README](https://github.com/Lewewe/HyperCanvas#readme)。

### 真实音频可视化 · Beta

将超级岛原有的装饰性波形替换为随播放音频变化的频谱柱。

- 五个频段，使用封面配色渐变，各柱宽度一致。
- 可用可视化效果替换音频输出设备按钮，锁屏、通知栏播放器和展开后的超级岛分别提供开关。
- 内部音频采集可在媒体音量为零时继续运行，无需麦克风；必要时回退到输出混音 Visualizer。
- 支持蓝牙时间补偿和手动微调，在 AOD 中降低帧率。
- 可隐藏超级岛的耳机图标，并在自动切歌后恢复中断的采集。

采集效果和时间同步取决于设备及音频输出方式，因此该功能仍标记为 Beta。

### 歌词与 NextLyrics 运行时

独立的 **Lyrics settings（歌词设置）** 页面集中管理搜索、显示和翻译。

- **Original / Extended 搜索模式：**选择原有的来源优先级及解析行为，或扩展后的歌词来源流程。
- **NextLyrics：**复用歌词缓存，并在有播放队列信息时预加载相邻歌曲。缓存区分搜索模式、来源配置和翻译设置，避免切换设置后复用不匹配的结果。
- **Gentle lyric flow：**将快速切换的歌词保留并分组显示，每组最多四行，提高可读性。
- 支持同时演唱的歌词、逐词时间格式，以及普通同步歌词。
- 可选的 **Alive lyrics** 效果、封面配色高亮、翻译和罗马音工具。
- 提供 LibreTranslate、Google Cloud 和 DeepL 等在线翻译选项，部分服务需要自行配置或填写 API 密钥。
- 可安装歌词字体包，并单独启用或停用，无需删除。
- 无歌词时可显示随节奏变化的颜文字，并根据较长的时钟和播放器位置调整布局。

歌词、翻译和逐词时间取决于所选来源及歌曲。详见[歌词显示（英文）](docs/lyric-effects.md)和[在线翻译（英文）](docs/online-lyric-translation.md)。

### 播放器外观

自定义锁屏／通知栏播放器及展开后的 Hyper Island 播放器。

- 背景主题和材质，支持动态样式及封面配色。
- 默认、圆形、旋转圆形或隐藏封面，可隐藏封面来源和音频输出设备图标。
- 封面拼贴、模糊封面、径向／线性渐变和柔化封面背景，可设置跟随系统／浅色／深色主题、模糊强度及背景动画。
- 通知栏／锁屏播放器和展开后的岛内播放器使用独立外观配置，设置分为 **Audio visualization（音频可视化）** 和外观两个选项卡。
- **Appearance（外观）** 中的 **Scrolling song information（歌曲信息滚动）** 分别控制两类播放器。较长的歌名和歌手名在可见时滚动，短文本保持静止，AOD 中暂停滚动。默认关闭，修改后立即生效。
- 大封面与圆形播放器封面之间平滑切换形状和圆角，切回 Default 时恢复系统圆角。
- 关闭自定义后保留系统原有主题、圆角和 AOD 布局。

外观功能基于 [HyperLyrics-Enhanced](https://github.com/juren233/HyperLyrics-Enhanced) 适配。详见[实现与致谢（英文）](docs/media-card-customization.md)。

### 更完善的大封面体验

- 切歌时自动更新封面，支持 YouTube Music 音乐／视频模式切换时更新对应图片。
- 为方形和横向封面调整时钟大小、间距及展开／收起动画。
- 切歌时使用轻度模糊和暗化过渡，封面准备完成并展开时逐渐消退。
- 改进快速切歌和上一曲处理，避免旧封面混入，以及重新播放或拖动进度时触发不必要的切歌效果。
- 针对 Spotify、网易云音乐和 Salt Player 的兼容性修复。
- 使用单张全屏封面背景，并辅以从封面提取的色彩区域。
- 可选 **Use wallpaper background（使用壁纸背景）**，为大封面和歌词后的壁纸添加可调暗化。暗化层在 AOD 中保持全屏，开启此模式会暂时停用 HyperCanvas。
- 有歌词时在大封面旁显示歌词按钮；开启点击手势后，无歌词的歌曲可单击收回播放器。

### 通知、胶囊模式与 AOD

- 大封面与通知堆叠／列表手势协调：打开列表时暂时收起，合适时恢复用户选择的显示状态。
- 避免通知堆叠回弹、快速手势及 AOD 唤醒时出现多余的收放动画。
- 在大封面模式下从音乐胶囊展开播放器时，模糊逐渐消退；点击通知组胶囊不会触发该效果。
- 可调胶囊模糊强度和亮度，兼容 HyperOS 4 的 Soft Glass 与 Frosted Glass。
- 支持低位屏下指纹的设备可使用紧凑通知胶囊，并调整大小及下移距离。
- 说明通知下沉选项的作用，并在修改后提供重启 SystemUI 的按钮。
- 适配用户调整大小的时钟和长时钟样式；在 AOD 中，若大封面与时钟重叠，则收回播放器。
- 改进日期居中及音乐背景下的状态栏图标对比度。
- 标记滑块默认值，回到默认位置时提供触觉反馈。

## 安装

1. 使用已 Root 的 **HyperOS 4** 设备，以及支持现代 Xposed API 的 LSPosed。APK 要求 **Android 15 或更新版本**。
2. 从 [GitHub Releases](https://github.com/Lewewe/HyperMusicCover-Enhanced/releases) 下载 APK。
3. 安装后在 LSPosed 中启用模块，并选择相关的推荐作用域，重启受影响的进程或设备。
4. 打开应用，配置封面、歌词、播放器外观及其他功能。
5. 使用 Spotify Canvas 时，进入 **Extensions → HyperCanvas → Download**，安装 HyperCanvas 0.1.1-beta 或更新版本，然后点击条目下方的 **Request hook permissions**。在 LSPosed 中启用模块并允许 Spotify／SystemUI 作用域，再点击 **Restart hooks to finish setup**。重启后的 SystemUI 确认激活后，设置才会解锁；随后在其中启用 Canvas。也可从[扩展发布页](https://github.com/Lewewe/HyperCanvas/releases)手动安装。

应用包名为 `com.yzc26623.HyperMusicCoverEnhanced`。从使用其他包名的旧模块迁移时，请先备份设置，并停用旧模块的作用域，再启用本模块。

**本页功能列表描述当前源码。** 下载版本包含的功能以对应更新说明为准；`main` 上的新功能可能尚未包含在最新 APK 中。

## 兼容性与反馈

开发主要在运行 **HyperOS 4 的 Xiaomi 17 Pro** 上测试。其他设备上的时钟样式、指纹位置、SystemUI 版本、音乐应用和其他 Hook 模块可能影响行为。

欢迎加入[官方 Telegram 群组](https://t.me/HyperMusicCoverEnhanced)，或[提交 Issue](https://github.com/Lewewe/HyperMusicCover-Enhanced/issues)，提供设备、HyperOS 版本、模块版本、播放器名称和复现步骤。对于偶发动画问题，简短录屏或相关日志会更有帮助。

## 从源码构建

克隆时包含 HyperCanvas 子模块：

```sh
git clone --recurse-submodules https://github.com/Lewewe/HyperMusicCover-Enhanced.git
cd HyperMusicCover-Enhanced
```

使用项目附带的 Gradle wrapper 和 Android SDK 37，在本地 `local.properties` 中配置 `sdk.dir`。项目的 Gradle daemon 配置会选择 Java 工具链。

```sh
./gradlew :app:assembleRelease
./gradlew :hyper-canvas:assembleRelease
```

两个模块均支持本地、不纳入版本控制的 `keystore.properties`，包含 `storeFile`、`storePassword`、`keyAlias` 和 `keyPassword`。未配置 release 签名时，构建使用本地 debug 密钥。**HMC-Enhanced 与 HyperCanvas 必须使用相同签名证书**，通信桥接才能工作；更新 APK 时也必须使用已安装应用的签名密钥。

已有工作目录可运行：

```sh
git submodule update --init --recursive
```

## 致谢

- [Lewewe](https://github.com/Lewewe) — Enhanced 分支的开发与维护。
- [puhboo](https://github.com/puhboo) — 歌词相关的重要贡献，包括扩展搜索、翻译、解析、逐词时间、缓存及歌词效果；参与 HyperCanvas 渲染器开发，包括不透明 TextureView 和 OpenGL ES 基础实现。
- [zyl6932](https://github.com/zyl6932/HyperMusicCover) — 原始 HyperMusicCover 项目。
- [juren233 / HyperLyrics-Enhanced](https://github.com/juren233/HyperLyrics-Enhanced) — 播放器自定义功能的基础。
- [miuix](https://github.com/compose-miuix-ui/miuix) 及应用内列出的其他项目 — UI、渲染、歌词数据及支持库。

应用内的 **Credits（致谢）** 页面及项目文档保留了详细贡献说明和第三方许可证信息。

## 许可证

[GNU Affero General Public License v3.0](LICENSE)。引入的组件保留其原有版权声明和许可证。
