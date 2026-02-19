# TachiyomiJ2K 开发指南

本文档旨在帮助新加入的开发者快速了解 TachiyomiJ2K 的项目架构、核心模块及开发流程。

## 1. 项目简介

TachiyomiJ2K 是基于 [Tachiyomi](https://github.com/tachiyomiorg/tachiyomi) 的一个分支版本（Fork），专注于提供更现代化的 UI/UX 设计和增强功能。

- **核心语言**: Kotlin
- **最低兼容版本**: Android 8.0 (API 26)
- **构建系统**: Gradle Kotlin DSL

## 2. 环境搭建

### 前置要求
- **JDK**: JDK 17 (项目编译目标为 JVM 17)
- **Android Studio**: 推荐最新稳定版 (如 Ladybug 或更新版本)
- **Android SDK**:
  - Compile SDK: 35
  - Target SDK: 35
  - Min SDK: 26

### 导入项目
1. Clone 仓库:
   ```bash
   git clone https://github.com/Jays2Kings/tachiyomiJ2K.git
   ```
2. 在 Android Studio 中打开项目根目录。
3. 等待 Gradle Sync 完成。

### 构建变体 (Build Variants)
项目主要包含以下变体：
- **standard**: 标准版，包含所有功能。
- **dev**: 开发版，通常用于测试新功能（可能有特定的后缀或签名配置）。
- **debug/release**: 调试与发布构建类型。

## 3. 架构概览

TachiyomiJ2K 主要沿用了 Tachiyomi 的架构，大部分模块使用 **MVP (Model-View-Presenter)** 模式，部分新模块（如阅读器）开始转向 **MVVM**。

### 核心技术栈
- **UI 框架**: [Conductor](https://github.com/bluelinelabs/Conductor) (替代 Fragment 进行页面导航) + XML Layouts + ViewBinding。
- **依赖注入**: [Injekt](https://github.com/Injekt/Injekt) (轻量级 Service Locator)。
- **数据库**: [StorIO](https://github.com/pushtorefresh/storio) (SQLite 封装) + [Requery](https://github.com/requery/requery) (底层 SQLite 支持)。
- **网络**: OkHttp 5 + Kotlinx Serialization (JSON 解析) + Jsoup (HTML 解析)。
- **图片加载**: [Coil](https://coil-kt.github.io/coil/)。
- **异步处理**: Coroutines (主导) + RxJava (部分遗留代码)。

### 目录结构
```
app/src/main/java/eu/kanade/tachiyomi/
├── data/               # 数据层
│   ├── database/       # 数据库表定义、查询与 Mapper
│   ├── preference/     # SharedPreferences 定义与辅助类
│   ├── network/        # 网络请求封装
│   ├── cache/          # 章节与封面缓存
│   └── library/        # 书库管理逻辑
├── source/             # 漫画源接口与实现 (LocalSource, HttpSource)
├── ui/                 # 界面层 (按功能模块划分)
│   ├── main/           # MainActivity (应用入口)
│   ├── library/        # 书库界面 (MVP)
│   ├── reader/         # 阅读器 (MVVM, 独立 Activity)
│   ├── warehouse/      # 本地书库管理 (新功能)
│   ├── setting/        # 设置界面
│   └── base/           # 基类 (BaseController, BasePresenter)
├── util/               # 工具类 (文件、系统、视图扩展)
├── widget/             # 自定义 View
└── App.kt              # Application 类，负责初始化 Injekt 等
```

## 4. 核心模块解析

### 4.1 UI 导航 (Conductor)
项目不使用 Fragment，而是使用 `Controller`。
- **BaseController**: 所有页面的基类，处理 ViewBinding、生命周期和 `CoroutineScope`。
- **Router**: 管理 Controller 的堆栈与切换。
- **生命周期**: `onViewCreated`, `onDestroyView`, `onChangeStarted` (转场动画)。

### 4.2 书库 (Library)
- **Controller**: `LibraryController`
- **Presenter**: `LibraryPresenter`
- **功能**: 展示收藏的漫画，支持分组（按状态、标签等）、排序和筛选。
- **数据流**: Presenter 从 Database 加载 `LibraryManga`，转换为 `LibraryItem` (FlexibleAdapter 的 Item) 给 RecyclerView 展示。

### 4.3 阅读器 (Reader)
阅读器是一个独立的 `Activity` (`ReaderActivity`)，为了提供沉浸式体验和独立的内存管理。
- **架构**: MVVM (`ReaderViewModel`)
- **Loader**:
  - `ChapterLoader`: 负责加载章节信息。
  - `PageLoader`: 策略模式，负责加载具体页面（`HttpPageLoader`, `ZipPageLoader`, `DirectoryPageLoader`）。
- **Viewer**:
  - `PagerViewer`: 左右翻页模式 (Right-to-Left, Left-to-Right, Vertical)。
  - `WebtoonViewer`: 条漫模式 (RecyclerView 实现)。

### 4.4 漫画源 (Source)
- **SourceManager**: 管理所有已安装的扩展源。
- **LocalSource**: 特殊源，ID 为 0。用于读取本地文件。
- **CatalogueSource**: 支持搜索和浏览目录的源接口。

### 4.5 本地书库管理 (Warehouse) - *New*
新增加的模块，用于管理 `LocalSource` 的多个扫描目录。
- **Controller**: `WarehouseController`
- **模型**: `LocalLocation` (path, name, enabled)
- **存储**: `PreferencesHelper` 中的 `local_locations` Key (JSON 格式)。
- **逻辑**: 用户通过系统文件选择器添加目录 -> 保存到 Prefs -> `LocalSource` 读取列表并聚合扫描所有目录下的漫画。

## 5. 开发规范

### 代码风格
- 遵循 Kotlin 官方编码规范。
- 使用 `ktlint` 或 Android Studio 默认格式化。
- 命名:
  - XML ID: `snake_case` (e.g., `recycler_view`, `btn_confirm`)
  - Kotlin: `camelCase` (e.g., `libraryItems`, `getMangaDetails`)

### 新增功能流程
1. **定义 UI**: 在 `src/main/res/layout` 创建 XML，或在 `ui` 包下创建 Compose UI (如适用)。
2. **创建 Controller**: 继承 `BaseController`，绑定 View。
3. **业务逻辑**:
   - 简单逻辑直接写在 Controller。
   - 复杂逻辑创建 Presenter (MVP) 或 ViewModel (MVVM)。
4. **数据存取**:
   - 数据库操作通过 `DatabaseHelper`。
   - 配置存取通过 `PreferencesHelper`。
5. **注册**: 如果是新的一级页面，可能需要在 `MainActivity` 的导航逻辑中注册。

## 6. 常见问题与调试

### 日志
项目使用 **Timber** 进行日志记录。
```kotlin
Timber.d("Debug message: %s", variable)
Timber.e(exception, "Error occurred")
```

### 数据库调试
使用 Android Studio 的 **App Inspection** -> **Database Inspector** 可以实时查看和修改 SQLite 数据库内容。

### 本地源调试
1. 在模拟器/真机创建文件夹 `/sdcard/Tachiyomi/local/TestManga`。
2. 放入图片文件 (`01.jpg`, `02.jpg`)。
3. 在 App 中刷新本地书库。
4. 现在支持在“书库”页添加任意文件夹作为本地源。

### 依赖注入 (Injekt)
获取单例对象的方法：
```kotlin
// 注入属性
val db: DatabaseHelper by injectLazy()
val prefs: PreferencesHelper by injectLazy()

// 直接获取
val sourceManager = Injekt.get<SourceManager>()
```

## 7. 贡献指南
- 提交 PR 前请确保运行 `./gradlew lintDebug` 检查代码问题。
- 保持 Commit Message 清晰简洁。
