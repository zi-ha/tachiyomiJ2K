本地漫画阅读器改造技术路线（TachiyomiJ2K｜路线B：彻底剥离在线能力）

目标与范围
- 目标：在主分支上彻底剥离在线内容获取能力（扩展、网络源、在线更新、网络下载等），保留并强化“本地图源 + 本地库 + 阅读器 + 进度记录”，仅为“阅读进度同步到第三方平台”保留最小联网能力（OAuth 与追番同步 API）。
- 保留：本地图源扫描与索引、资料库/分类、阅读器、封面与缓存、备份/恢复（仅本地文件）、阅读进度记录、第三方平台进度同步、基础统计、仅用于授权的 WebView。
- 移除：扩展生态与在线源、扩展安装/更新、在线章节更新与下载队列、与扩展/在线源相关的前后台 Job；浏览/扩展相关 UI 与入口；与扩展安装/广泛包查询等权限。

总体策略
- 路线B（主分支直接改造）：从代码与构建系统层面移除在线能力与扩展生态，精简 Manifest 与依赖；仅保留追番同步所需的网络栈与授权能力。删除相关源代码与资源，避免条件编译与多风味分叉，降低维护复杂度。在处理时可适当加快效率。


阶段性步骤
1) 构建与权限收敛（主分支） [已完成]
- 在 [app/build.gradle.kts](file:///d:/Desktop/tachiyomiJ2K/app/build.gradle.kts) 移除在线相关依赖与插件：
  - 删除抓站/脚本与在线生态依赖：jsoup、quickjs、Shizuku、扩展相关库等。
  - 保留最小网络栈仅用于追番同步：OkHttp/Okio、kotlinx-serialization、WorkManager（仅用于追番延迟同步）。
  - 如无崩溃上报需求，移除 Google 服务插件与 Firebase 相关依赖。
- Manifest 最小化：
  - 保留用于追番同步与授权的基础网络权限：[AndroidManifest.xml](file:///d:/Desktop/tachiyomiJ2K/app/src/main/AndroidManifest.xml) 中的 INTERNET、ACCESS_NETWORK_STATE。
  - 移除与扩展安装/广泛包查询等无关权限（REQUEST_INSTALL_PACKAGES、UPDATE_PACKAGES_WITHOUT_USER_ACTION、QUERY_ALL_PACKAGES、ACCESS_WIFI_STATE 等）与对应组件声明（扩展安装 Activity/Receiver、Shizuku Provider、扩展深链等）。
  - 保留必要的通知/唤醒权限仅在确有本地任务需要时（如追番同步提醒，可按需评估）。
- 编译常量与开关：
  - 固定或移除 INCLUDE_UPDATER，并删除更新模块引用与相关代码路径。

2) 依赖注入与源管理精简 [已完成]
- Injekt 注册收敛：[AppModule.kt](file:///d:/Desktop/tachiyomiJ2K/app/src/main/java/eu/kanade/tachiyomi/AppModule.kt)
  - 保留：DatabaseHelper、ChapterCache、CoverCache、PreferencesHelper、MangaShortcutManager、Json、NetworkHelper、TrackManager。
  - 移除：JavaScriptEngine、ExtensionManager、DownloadManager（网络下载）等与在线相关的单例注册与工厂。
- 源管理器仅暴露 LocalSource 并删除在线路径：
  - 在 [SourceManager.kt](file:///d:/Desktop/tachiyomiJ2K/app/src/main/java/eu/kanade/tachiyomi/source/SourceManager.kt) 初始化时仅注册 LocalSource，删除 extensionsFlow 相关逻辑、delegatedSources、getOnlineSources 等 API；移除对 HttpSource/DelegatedHttpSource 的依赖。

3) 本地图源能力保留与增强 [已完成]
- 关键文件：[LocalSource.kt](file:///d:/Desktop/tachiyomiJ2K/app/src/main/java/eu/kanade/tachiyomi/source/LocalSource.kt)
- 路径兼容：沿用 “<AppName>/local” 与 “Tachiyomi/local” 兼容目录，首次启动引导选择目录并建立。
- 支持格式：zip/cbz、rar/cbr、epub、章节文件夹；解析 info.json/ComicInfo.xml 元数据（完善字段优先级）。
- 扫描策略：提供全量扫描 + 基于 mtime 的增量扫描；UI 提供“手动刷新”入口与“启动时索引更新”选项。
- 下载管理器：禁用网络下载；若保留预读，仅针对本地文件顺序读取并缓存。

4) UI 导航与页面调整 [已完成]
- 移除“浏览/扩展”入口：
  - 删除菜单项 nav_browse：[bottom_navigation.xml](file:///d:/Desktop/tachiyomiJ2K/app/src/main/res/menu/bottom_navigation.xml)、[side_navigation.xml](file:///d:/Desktop/tachiyomiJ2K/app/src/main/res/menu/side_navigation.xml)。
  - 在 [MainActivity.kt](file:///d:/Desktop/tachiyomiJ2K/app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt) 内删除所有指向 BrowseController/BrowseSourceController/RepoController 的分支与入口。
- 浏览/扩展相关 UI/Controller 下线：
  - 目录：ui/source/browse、ui/source/browse/repos、ui/extension 整体删除；保留用于第三方授权登录的 WebViewActivity（追番授权仍需）。
- 资料库与本地整合：
  - 来源列表仅展示 LocalSource；或直接在资料库提供“选择本地目录/刷新”按钮。
  - 首启引导：弹窗选择本地目录（SAF），成功后触发首次索引。
- 追番/第三方登录 UI 保留：
  - 保留 Manifest 中登录/授权 Activity（MAL、Anilist、Shikimori、Bangumi 等）：[AndroidManifest.xml](file:///d:/Desktop/tachiyomiJ2K/app/src/main/AndroidManifest.xml)。
- 更新/扩展角标与入口移除：
  - 删除 MainActivity 中扩展更新/扩展 API 逻辑。

5) 后台任务与服务清理 [已完成]
- 禁用网络相关 WorkManager 任务：
  - LibraryUpdateJob：依赖 HttpSource 拉取章节；直接删除该 Job 与入口。[LibraryUpdateJob.kt](file:///d:/Desktop/tachiyomiJ2K/app/src/main/java/eu/kanade/tachiyomi/data/library/LibraryUpdateJob.kt)
  - ExtensionUpdateJob、AppUpdateChecker/Notifier：完全下线。
  - NotificationReceiver 中与在线更新/下载有关的 Action 移除。
- 前台服务类型：
  - 若无持续后台任务，删除 FOREGROUND_SERVICE_DATA_SYNC 与对应 service 声明。

6) 设置页与偏好项
- 设置分组最小化：存储/本地目录、阅读器、外观、备份、隐私与安全。
- 移除：扩展、网络缓存/代理、在线更新计划、下载（网络）设置；保留追番账户与授权、仅用于授权的 WebView 设置。
- 默认行为：首次安装必须选择本地目录；可变更目录；提供“扫描策略/过滤/排序/元数据优先级”。

7) 阅读器与媒体栈
- 保留阅读器及图像解码栈（Coil、SubsamplingScaleImageView 等），不依赖网络即可工作。[依赖参考](file:///d:/Desktop/tachiyomiJ2K/app/build.gradle.kts)
- 预读取/缓存仅针对本地文件；移除 Http 拦截器/网络配置。
- 优化大图与长条阅读（内存策略、磁盘缓存上限），保障本地海量章节体验。

8) 追番与第三方集成保留
- 保留 data/track 模块（MAL/Anilist/Kitsu/Shikimori/Bangumi/Kavita/Komga 等），确保 OAuth/Token 刷新与 API 调用可用。[目录索引](file:///d:/Desktop/tachiyomiJ2K/app/src/main/java/eu/kanade/tachiyomi/data/track)
- 保留 Manga 详情页中的跟踪 UI（如 TrackSearchItem）；保留后台自动同步（如 DelayedTrackingUpdateJob、UnattendedTrackService）。

9) 在线源与扩展生态剥离
- 删除/禁用 source/online 与 extension：
  - 源：HttpSource、ParsedHttpSource、DelegatedHttpSource 及具体实现。[online 包](file:///d:/Desktop/tachiyomiJ2K/app/src/main/java/eu/kanade/tachiyomi/source/online)
  - 扩展：ExtensionManager、ExtensionInstaller、相关 UI 与广播接收器。[ExtensionManager.kt](file:///d:/Desktop/tachiyomiJ2K/app/src/main/java/eu/kanade/tachiyomi/extension/ExtensionManager.kt)
- Manifest 清理：扩展安装 Activity、Receiver、Shizuku Provider、扩展深链等统一删除。[AndroidManifest.xml](file:///d:/Desktop/tachiyomiJ2K/app/src/main/AndroidManifest.xml)

10) 构建系统与依赖精简
- 直接在主分支裁剪依赖：
  - 去除抓站与脚本相关依赖（jsoup、quickjs）、扩展生态相关库；保留 OkHttp/Okio 用于追番同步。
  - 如不需要上报与分析，移除 Google 服务插件与 Firebase。
- ProGuard/R8：
  - 删除不再使用的 keep 规则；仅保留阅读器、数据库、图片解码相关规则。
- 编译常量：
  - 移除或固定 INCLUDE_UPDATER，并删除更新模块引用。

11) 数据与目录迁移
- 数据库/偏好：
  - 原库含在线源相关字段时，可保留表结构但 UI 隐藏；或提供数据库迁移脚本置空/删除无用字段，避免空指针与冗余查询。
- 目录迁移：
  - 保持 LocalSource 兼容 “Tachiyomi/local” 与 “<AppName>/local”；首次运行提供迁移说明并可自动复制/合并（可选）。

12) 验收清单
- 构建与安装：
  - 主分支构建成功，产物不包含扩展/抓站/QuickJS/Firebase 等；保留 OkHttp/Okio 以支持追番同步。
- 权限：
  - 系统权限页仅保留互联网访问与网络状态（用于进度同步）；无扩展安装等权限。
- UI：
  - 导航无“浏览”；设置中无“扩展/在线源/更新/下载（网络）”，保留“追番账户/同步”。
- 功能：
  - 首次启动选择本地目录 → 扫描 → 资料库出现本地漫画。
  - 本地图源的搜索/排序/最新基于本地 mtime/名称。
  - 阅读器手势、封面生成与缓存正常。
- 作业与服务：
  - WorkManager 无在线更新/扩展更新任务；通知仅用于本地动作。
- 网络行为：
  - 仅存在追番同步相关网络请求（可通过抓包或系统统计验证）；无其他网络请求。

可选增强
- 增量扫描与目录监听：基于 SAF/DocumentFile 或 MediaStore + 轮询，提供“监听本地目录变化”的选项。
- 元数据优先级：优先采用 info.json/ComicInfo.xml；无则使用章节首图作为封面。
- 批量导入向导：自动将“图片直接在根目录”的情况重排为“章节文件夹”。
- 高级筛选排序：按语言、集数、最近新增/修改、文件大小等。
- 隐私模式：不记录最近阅读、可隐藏封面/历史。



建议的提交/PR 切分
- PR1：删除在线源与扩展生态（source/online、extension）、移除相关入口与资源；Manifest/权限裁剪、导航菜单精简（移除“浏览/扩展”）。
- PR2：精简 Injekt 注册（移除 JavaScriptEngine/ExtensionManager/DownloadManager）、[SourceManager](file:///d:/Desktop/tachiyomiJ2K/app/src/main/java/eu/kanade/tachiyomi/source/SourceManager.kt) 仅保留 LocalSource。
- PR3：设置页精简与首次目录选择引导；补齐本地扫描/索引入口与提示。
- PR4：后台 Job/Service 清理；通知与前台服务调整（仅保留本地/追番相关）。
- PR5：构建依赖与 ProGuard/R8 精简；验证包体与运行时网络行为（仅追番同步可联网）。

关键参考位置
- 构建脚本：[app/build.gradle.kts](file:///d:/Desktop/tachiyomiJ2K/app/build.gradle.kts)
- 权限与组件：[AndroidManifest.xml](file:///d:/Desktop/tachiyomiJ2K/app/src/main/AndroidManifest.xml)
- 注入模块：[AppModule.kt](file:///d:/Desktop/tachiyomiJ2K/app/src/main/java/eu/kanade/tachiyomi/AppModule.kt)
- 源管理与本地源：
  - [SourceManager.kt](file:///d:/Desktop/tachiyomiJ2K/app/src/main/java/eu/kanade/tachiyomi/source/SourceManager.kt)
  - [LocalSource.kt](file:///d:/Desktop/tachiyomiJ2K/app/src/main/java/eu/kanade/tachiyomi/source/LocalSource.kt)
- 导航与页面：
  - [bottom_navigation.xml](file:///d:/Desktop/tachiyomiJ2K/app/src/main/res/menu/bottom_navigation.xml)
  - [side_navigation.xml](file:///d:/Desktop/tachiyomiJ2K/app/src/main/res/menu/side_navigation.xml)
  - [MainActivity.kt](file:///d:/Desktop/tachiyomiJ2K/app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt)
- Job/Work：
  - [LibraryUpdateJob.kt](file:///d:/Desktop/tachiyomiJ2K/app/src/main/java/eu/kanade/tachiyomi/data/library/LibraryUpdateJob.kt)
- 在线模块（准备禁用/删除）：
  - [source/online](file:///d:/Desktop/tachiyomiJ2K/app/src/main/java/eu/kanade/tachiyomi/source/online)
  - [extension](file:///d:/Desktop/tachiyomiJ2K/app/src/main/java/eu/kanade/tachiyomi/extension)

落地建议
- 先完成 PR1-PR2，确保应用可正常构建、安装、启动，本地扫描可用；随后逐步精简设置与后台任务；最后做依赖与包体收尾。全过程保持 small step + 验收清单逐项打勾。 
