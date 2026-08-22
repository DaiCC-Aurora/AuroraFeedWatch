# podcast-watch — 英语听力 Wear OS 手表应用

面向高中生的英语听力训练手表应用：自动同步云端节目、仅 Wi-Fi + 充电时下载最新 5 期、离线播放、字幕随进度高亮、系统"正在播放"卡片控制。

技术栈：Kotlin + Jetpack Compose for Wear OS 1.3.0 + Room + Retrofit/Gson + OkHttp + WorkManager + Media3 ExoPlayer + MediaBrowserServiceCompat + DataStore。

## 目录结构

```
podcast-watch/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── app/
│   ├── build.gradle.kts            # 依赖与 BASE_URL 注入
│   └── src/main/
│       ├── AndroidManifest.xml     # 权限、Launcher Activity、媒体服务、MediaButtonReceiver
│       ├── res/                    # 字符串、矢量图标（通知按钮）
│       └── java/com/aurora/podcast/
│           ├── PodcastApplication.kt      # 单例仓库/播放器 + 启动调度
│           ├── MainActivity.kt            # 极简三屏导航 + MediaBrowser 连接
│           ├── data/
│           │   ├── db/                    # EpisodeEntity / EpisodeDao / AppDatabase (Room)
│           │   ├── network/               # ApiService / Dtos / NetworkModule (Retrofit)
│           │   ├── settings/              # DataStore 设置（保留期数、仅 Wi-Fi）
│           │   ├── model/                 # SubtitleCue / SubtitleParser (VTT/LRC/纯文本)
│           │   └── repository/PodcastRepository.kt  # 云端同步 + 下载状态 + 清理
│           ├── work/                      # DownloadWorker / CleanupWorker / Scheduler / FileDownloader
│           ├── playback/                  # PlayerManager (ExoPlayer) / PlaybackService (MediaBrowserService)
│           └── ui/
│               ├── theme/Theme.kt
│               ├── screen/                # 节目列表 / 播放页 / 设置页
│               └── viewmodel/             # Episodes / Player / Settings ViewModel
```

## 一、构建（Android Studio）

1. 环境要求：**Android Studio Hedgehog 及以上**、JDK 17、Android SDK 34 (compileSdk)。
2. 用 Android Studio 打开本项目根目录（`podcast-watch/`），等待 Gradle 同步完成。
   - 首次构建会自动下载 Gradle 8.7 与依赖（需科学上网或配置镜像）。
3. 把云端 Feed 地址写入构建配置：
   - 方式 A（推荐）：命令行 `./gradlew assembleDebug -PFEED_BASE_URL=https://你的项目.vercel.app/`
   - 方式 B：编辑 `gradle.properties` 中的 `FEED_BASE_URL=https://your-app.vercel.app/`，或用 Android Studio 的 Build Variants 面板。
4. 运行：连接 Wear OS 设备/模拟器（如 Wear OS 4+ 模拟器，含 Watch Face/API 34），点击 Run（`podcast-watch` 模块）。
   - 模拟器镜像：`system-images;android-34;wear;x86_64`（Android Studio SDK Manager 安装）。

## 二、使用流程

1. 打开应用 → 自动从云端拉取节目列表（列表顶部显示下载状态）。
2. 点击"未下载"节目 → 加入下载队列（Worker 在 Wi-Fi + 充电时执行，满足后自动下载音频+字幕）。
3. 点击"已下载"节目 → 进入播放页：
   - 中部字幕区随播放进度自动切换当前行（高亮当前句）。
   - 底部 ◀◀ / ▶❚❚ / ▶▶ 控制上一首、播放/暂停、下一首。
4. 长按表冠/侧键呼出系统"正在播放"卡片 → 可播放/暂停、上一首/下一首（MediaSession）。
5. 设置页：离线保留期数（默认 10）、仅 Wi-Fi 下载开关、立即清理缓存。
6. 无网络时：列表只展示已下载节目并正常本地播放（含字幕）。

## 三、关键实现说明

| 规格要求 | 实现位置 |
|---|---|
| Room 数据模型（字段与规格完全一致） | `data/db/EpisodeEntity.kt` |
| Retrofit 拉取 Feed | `data/network/ApiService.kt`（GET /api/feed?limit=50） |
| 下载 Worker：充电 + 仅 Wi-Fi + OkHttp 断点续传 | `work/DownloadWorker.kt` + `work/FileDownloader.kt` |
| 清理 Worker：每日 03:00，保留最近 N 期、跳过当前播放 | `work/CleanupWorker.kt` + `work/Scheduler.kt` |
| ExoPlayer 本地播放 + 字幕解析（VTT/LRC/纯文本） | `playback/PlayerManager.kt` + `data/model/SubtitleParser.kt` |
| MediaBrowserService + MediaSession + PlaybackStateCompat | `playback/PlaybackService.kt` |
| ScalingLazyColumn 列表 / 播放页 / 设置页 | `ui/screen/*.kt` |

> 说明：规格给出的 ExoPlayer 2.19.1 对应现维护的 **Media3 ExoPlayer 1.4.1**（androidx.media3），API 更现代、兼容 targetSdk 34；已按等价替换，功能不受影响。

## 四、与云端对接

- 手表通过 `BuildConfig.BASE_URL + "api/feed?limit=50"` 拉取节目（JSON 字段 snake_case 与云端一致）。
- 云端见 [`../podcast-cloud/`](../podcast-cloud/README.md)，部署后把 Base URL 填到这里（含结尾 `/`）。

## 五、排错

- **列表一直为空**：检查 BASE_URL 是否可访问（`curl https://xxx.vercel.app/api/feed`），以及模拟器网络。
- **下载不执行**：Worker 要求充电 + Wi-Fi（模拟器需连"未计量"网络，或用设置页手动点击、或 `adb shell dumpsys jobscheduler` 查看）。用户主动点击下载只要求有网络。
- **通知不显示**：Android 13+ 需在设置中授予通知权限（应用已请求）。
- **正如任何 Wear 应用**：正式上架前请补充自适应图标（mipmap-anydpi-v26）等素材；当前使用矢量占位图标。