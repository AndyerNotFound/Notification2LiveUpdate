# Notification 2 Live Update

把任意应用的**横幅通知（Heads-up Notification）**转换成 Android 16 的 **Live Update（实时更新）**通知。

> 一个纯本地运行的 Android 小工具：监听通知 → 解析内容 → 以本应用身份重新发布为可提升的常驻进度通知。不联网、不上传、不需要 root。

**[⬇️ 下载最新 APK](https://github.com/AndyerNotFound/Notification2LiveUpdate/releases/latest)**

[![Android CI](https://github.com/AndyerNotFound/Notification2LiveUpdate/actions/workflows/android.yml/badge.svg)](https://github.com/AndyerNotFound/Notification2LiveUpdate/actions/workflows/android.yml)
[![License: AGPL-3.0](https://img.shields.io/badge/License-AGPL--3.0-blue.svg)](LICENSE)

---

## 功能

- **横幅 → 实时更新**：把外卖、打车、下载、导航、跑分、录音等横幅或常驻进度通知，转换为系统级 Live Update 形态。
- **进度映射**：读取源通知的 `EXTRA_PROGRESS / EXTRA_PROGRESS_MAX / EXTRA_PROGRESS_INDETERMINATE`，映射为 `Notification.ProgressStyle`。
- **胶囊文字**：自动生成状态栏胶囊文字（百分比 / 应用名 / 标题，可配置，最多 7 个字符）。
- **按应用选择**：默认只转换你勾选的应用，也可以对所有应用生效。
- **触发规则**：仅横幅 / 横幅 + 常驻进度 / 全部通知，三档可选。
- **计时器透传**：源通知使用 chronometer 时，转换结果继续保持计时。
- **跟随源通知**：源通知更新时原地刷新，源通知消失时自动移除（可关闭）。
- **前台隐藏**：检测到你打开源应用（如外卖、下载器）时收起对应的实时更新，退出应用后自动恢复，避免自己挡自己。
- **查看后自动关闭**：在聊天类应用里停留 6 秒即视为已读，直接移除对应的实时更新；导航、下载等常驻任务不受影响，仍只在前台期间隐藏。
- **完成即清除**：进度到达最大值（倒计时类进度不会被误判），或标题/正文出现「完成 / 已结束 / 已送达 / 已到达 / completed / finished / done / delivered」等完成语义时，8 秒后自动移除，不再长期占着状态栏。
- **替换模式**：可选在转换成功后移除原始通知，避免通知栏出现两份内容。
- **忽略动作**：通知上自带「忽略」按钮，30 分钟内不再转换该条通知。
- **诊断面板**：显示通知使用权、通知权限、实时更新资格，以及最近一次转换结果是否具备提升特征。
- **一键测试**：内置 20 秒进度演示，用来确认系统是否真的把通知放进了 Live Update。

## 系统要求

| 项目 | 要求 |
| --- | --- |
| Android | 8.0 (API 26) 及以上可安装；**Live Update 形态需要 Android 16 (API 36)**，QPR1 (36.1) 起改用权限 + 显式请求模型 |
| 权限 | 通知使用权（Notification Listener）+ 通知权限（POST_NOTIFICATIONS）；「前台隐藏」「查看后自动关闭」额外需要**使用情况访问**（Usage Access，特殊应用权限，需手动授予） |
| Root / Shizuku | 不需要 |

在 Android 16 以下，转换结果仍会以**常驻进度通知**的形式出现，只是不会进入系统的 Live Update 区域，也没有状态栏胶囊。

## 安装

1. 打开仓库的 **Actions** 页面，进入最近一次成功的 `Android CI` 构建，下载 `Notification2LiveUpdate-debug` 工件。
2. 或者从 **Releases** 下载 `Notification2LiveUpdate-*-debug.apk`。
3. 安装 APK（需要允许「安装未知来源应用」）。

每个 Release 都附带 `SHA256SUMS.txt`，下载后可先校验：

```bash
sha256sum -c SHA256SUMS.txt
```

> 从 **v0.3.0** 起，CI 使用项目固定签名密钥（保存在 GitHub Actions Secrets）签署 APK，后续版本可直接覆盖安装。v0.1.0 / v0.2.0 是每次构建随机生成的 debug 签名，从这两个版本升级需要先卸载一次。
>
> 签名证书 SHA-256：`F5:7F:0B:36:0B:07:56:63:3A:10:CC:4C:30:9C:61:F4:48:C9:01:03:74:35:01:BA:1A:D5:FD:15:FC:A6:41:4F`

## 使用

1. 打开应用，点击「通知使用权 → 去授权」，在系统列表里允许 **Notification 2 Live Update**。
2. 允许通知权限。
3. 打开右上角总开关。
4. 选择转换范围：
   - 打开「对所有应用生效」，或
   - 关闭后进入「选择应用」，勾选需要转换的应用。
5. 点击「发送测试 Live Update」，观察通知栏与状态栏胶囊。

### 关于「实时更新资格」

Android 16 不允许应用自己设置 `FLAG_PROMOTED_ONGOING`，只能由系统判定。而 Android 16 前后有两个不同的规则集合：

**36.0（Android 16 初版）** —— `hasPromotableCharacteristics()` 的旧分支：

- `setOngoing(true)`
- 不是 group summary、没有自定义 RemoteViews
- 有标题
- **`setColorized(true)`**（必须彩色）
- 样式为 `ProgressStyle` / `BigTextStyle` / 持续的 `CallStyle`
- 渠道重要度 > `IMPORTANCE_MIN`

**36.1+（Android 16 QPR1）** —— 改为显式申请：

- 在 manifest 声明 `android.permission.POST_PROMOTED_NOTIFICATIONS`（`normal|appop`，安装即授予，用户可在系统设置里关闭）
- 通过 `EXTRA_REQUEST_PROMOTED_ONGOING`（即 `Notification.Builder#setRequestPromotedOngoing(true)`）请求提升
- `setOngoing(true)`、有标题、样式为 `Standard / BigText / Call / Progress / Metric`
- 不是 group summary、没有自定义 RemoteViews
- **不能 `setColorized(true)`**（QPR1 明确禁止彩色）
- 渠道重要度 > `IMPORTANCE_MIN`

本应用不会写死版本号：启动时构造一条探测通知调用 `Notification#hasPromotableCharacteristics()`，由系统自己告诉我们当前生效的是哪套规则（也兼容 QPR1 特性开关未开启的设备），然后按对应模式组装通知。转换结果会显示 `已转换 · 可提升` 或 `已转换`，状态卡片也会标注当前是「QPR1 请求模式」还是「旧版彩色模式」。

如果系统仍未授予资格，可点击状态卡片里的「去开启」跳转到系统的实时更新设置页（`Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS`）。

## 工作原理

```
其他应用 ──post──> NotificationManager
                        │
                        ▼
              NluListenerService（通知使用权）
                        │  解析标题/正文/进度/计时器/渠道重要度
                        ▼
                  ConversionPolicy（过滤：媒体/通话/闹钟/系统/群组摘要）
                        │
                        ▼
                  Reposter（以本应用身份重建通知）
                        │  setOngoing + setColorized + ProgressStyle + 胶囊文字
                        ▼
              NotificationManager ──> SystemUI（状态栏胶囊 / 锁屏实时更新区）
```

- 转换后的通知使用本应用的通知渠道 `n2lu_live_updates`，静音、不震动、不重复提醒。
- 源通知与本应用通知通过 `(tag, id)` 一一对应：源通知更新即原地刷新，源通知移除即取消。
- 监听服务忽略本应用自身的通知，避免循环转换。

## 权限说明

| 权限 | 用途 |
| --- | --- |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | 系统绑定通知监听服务（在系统设置中由用户授予） |
| `POST_NOTIFICATIONS` | 发布转换后的通知 |
| `POST_PROMOTED_NOTIFICATIONS` | Android 16 QPR1 起发布「实时更新」所需的普通权限（安装时授予，可在系统设置关闭） |
| `PACKAGE_USAGE_STATS` | 只用于判断「当前前台应用是哪一个」，支撑「源应用在前台时隐藏」和「查看后自动关闭」；权限本身需在系统设置的「使用情况访问」里手动开启 |
| `QUERY_ALL_PACKAGES` | 读取应用列表、应用名称与图标，供选择与展示 |

应用不申请网络权限，所有数据（设置、最近 60 条转换记录）都保存在本机 `SharedPreferences` 中。

## 构建

要求：JDK 21、Android SDK Platform 36、Gradle 8.14.6（仓库自带 wrapper）。

```bash
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

如需使用固定密钥签名（CI 即如此），在构建前设置：

```bash
export ANDROID_KEYSTORE_PATH=/path/to/release.p12
export ANDROID_KEYSTORE_PASSWORD=...
export ANDROID_KEY_ALIAS=n2lu
export ANDROID_KEY_PASSWORD=...
./gradlew assembleDebug
```

未设置这些变量时回退到 AGP 自动生成的 debug 签名。CI 在 `.github/workflows/android.yml` 中从 Secrets 解码密钥、构建、校验签名，并在推送 `v*` 标签时自动创建 Release（含 `SHA256SUMS.txt`）。

## 目录结构

```
app/src/main/java/com/andyer/n2lu/
├── N2LuApp.kt                 应用入口：日志、通知渠道
├── data/
│   ├── Prefs.kt               设置存储（Compose 状态 + SharedPreferences）
│   └── EventLog.kt            最近转换记录（JSON 持久化）
├── service/
│   ├── NluListenerService.kt  通知监听与调度
│   ├── SourceNotification.kt  源通知解析 + 过滤策略
│   ├── CompletionPolicy.kt    完成语义判定（进度 / 关键字）
│   ├── ChipText.kt            胶囊文字生成
│   ├── Reposter.kt            通知重建与发布
│   ├── ActionReceiver.kt      「忽略」动作
│   └── DemoPoster.kt          演示通知
├── ui/                        Compose 界面
└── util/                      系统状态、应用列表、图标加载、前台应用检测
```

## 已知限制

- **实时更新由系统决定**：即使满足全部特征，系统仍可能因为渠道设置、勿扰模式、应用资格、OEM 额外策略等原因不提升。此时会退化为普通常驻通知。
- **无法转发原始点击意图**：点击转换后的通知会打开源应用的主入口（`getLaunchIntentForPackage`），而不是源通知原本的深层页面。
- **依赖通知内容**：只转换包含标题/正文的常规通知；纯自定义 RemoteViews 通知无法解析，会被跳过。
- **替换模式会移除源通知**：开启后原应用的通知会消失，其操作按钮（回复、暂停等）也会一并丢失，默认关闭。
- **媒体与通话不转换**：`CATEGORY_TRANSPORT`（媒体）、通话、闹钟、系统服务类通知默认跳过，避免与系统媒体控件冲突。
- **不同 OEM 行为不同**：HyperOS / ColorOS / One UI 等对通知与常驻通知有自己的策略，Live Update 胶囊可能不显示；OEM 也可以在系统层面追加资格限制。
- **已读判定是启发式的**：只能通过「源应用在前台持续停留 ≥ 6 秒」推断你已经看过，并不是真正的已读回执；快速切进切出（不足 6 秒）会恢复而不是关闭。仅对非常驻通知生效（聊天消息类），导航、下载、录音等常驻任务只做前台隐藏。
- **完成判定是启发式的**：只认「进度到达最大值（且不是倒计时进度）」和常见完成关键字（完成 / 已结束 / 已送达 / 已到达 / completed / finished / done / delivered）。出现「未完成」「尚未」等否定词时不会触发；「完成率 30%」「已完成 3/10」「complete 45%」这类进度描述也不会被当成完成。若某个应用的完成文案不在列表里，它就不会被自动清除，可在「高级选项」里关掉该功能。清除后同一条通知不会因为源应用继续刷新而反复弹出，直到内容真正变化或源通知被移除。
- **前台检测依赖系统事件**：使用情况访问未授权、屏幕熄灭或系统限制用量统计时，「前台隐藏」和「查看后自动关闭」都不会生效（实时更新照常显示），不会影响其它功能。

## 常见问题

**Q：选项卡/开关点了没反应，或者一松手就弹回原样？**
A：0.4.1 修掉了这个界面问题。此前设置值虽然已经写进 `SharedPreferences`（转换逻辑确实生效），但界面读的是普通字段，Compose 认为参数没变就跳过了重组，于是按钮有涟漪、显示却不更新。现在设置值本身就是 Compose 快照状态，任何界面改动都会立即反映到所有读取它的地方。

**Q：某些跳转按钮点了没反应？**
A：0.3.0 起所有跳转失败都会弹 Toast 说明原因；系统确实没有对应设置页时，会退回到应用通知设置/应用信息页。如果连 Toast 都没有，请把「诊断信息」卡片截图反馈。

**Q：系统设置里找不到本应用的「实时更新」开关？**
A：那个开关由系统按两个条件决定（AOSP `PromotedNotificationsPreferenceController.isAvailable()`）：
1. 应用 manifest 请求了 `POST_PROMOTED_NOTIFICATIONS`（0.3.0 起满足，看诊断卡片「提升权限声明」）；
2. 系统是 Android 16 QPR1 且 `ui_rich_ongoing` 特性开启（看诊断卡片「提升设置页」）。

在 36.0 或没有该页面的 ROM 上，没有可供手动开启的名单，提升完全由系统按通知特征自动判定，此时应用会退化为普通常驻通知。


**Q：打开源应用后实时更新消失了？**
A：这是「应用在前台时隐藏」在起作用：你已经在应用里看到了进度，状态栏不必重复。退出应用后几秒内会自动恢复。如果不想这样，可在「高级选项」里关掉；第一次使用需要先在「系统状态 → 使用情况访问」里授予权限，否则该功能不会生效。

**Q：消息已经点开看过了，实时更新还挂着？**
A：0.6.0 起默认开启「查看后自动关闭」（老版本升级后也是开启状态）。两种处理方式：源应用自己清掉原通知时，本应用会立刻一起移除（前提是「源通知消失后保留」为关闭，默认如此）；如果源通知一直不清，则在 0.6.0 及以后版本里，只要你在该应用里停留 6 秒，「查看后自动关闭」就会移除它，且相同内容在 60 秒内不会重复弹出，不要求手动划掉。这项判断同样依赖「使用情况访问」权限，未授权时不会生效。

**Q：通知完成后没有自动消失？**
A：自动清除依据两类信号：进度到达最大值，或标题/正文包含完成关键字。倒计时式进度、只有「正在处理」而没有进度的通知不会被判定完成，系统无从判断任务是否结束，只能保留。可在「高级选项」里确认「完成后自动清除」处于开启状态。

**Q：状态栏没有出现胶囊？**
A：先看「实时更新资格」是否为绿色；再看「最近转换」是否显示 `已转换 · 可提升`。若资格为否，点击「去开启」在系统设置中允许本应用使用实时更新。

**Q：为什么有的横幅没有被转换？**
A：默认只转换「横幅」或「横幅 + 常驻进度」。聊天类通知常常是群组摘要或媒体样式，会被过滤。可把触发时机切到「全部通知」再试。

**Q：卸载重装后通知使用权没了？**
A：Android 会在卸载时撤销该权限，重新授权即可。

## 许可

本项目基于 [AGPL-3.0](LICENSE) 发布。
