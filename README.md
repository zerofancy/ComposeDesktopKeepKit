# ComposeDesktopKeepKit

为 Compose Desktop 的 Release ProGuard 自动添加依赖 keep 规则。按最终解析出的依赖坐标和版本匹配内置规则库，包含传递依赖。

默认只需应用插件。生成的 `.pro`、报告和临时文件位于 **Gradle 用户缓存**，不会写入接入方项目目录（包括 `build`）。不扫描 SPI、内嵌规则、字节码或应用资源；规则库之外的依赖直接跳过。

## 接入

插件 ID：`io.github.zerofancy.compose-desktop-keep-kit`。

通过 JitPack 接入，无需克隆仓库。在接入方 `settings.gradle.kts` 中配置插件仓库与坐标映射：

```kotlin
pluginManagement {
    repositories {
        maven("https://jitpack.io") {
            content { includeGroup("com.github.zerofancy") }
        }
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "io.github.zerofancy.compose-desktop-keep-kit") {
                useModule("com.github.zerofancy:ComposeDesktopKeepKit:${requested.version}")
            }
        }
    }
}
```

在 Compose Desktop application 模块应用插件：

```kotlin
plugins {
    id("org.jetbrains.compose") // 沿用项目原有版本
    id("io.github.zerofancy.compose-desktop-keep-kit") version "v0.1.1"
}
```

版本包含 Git 标签的 `v` 前缀。使用 `useModule` 直接解析实现模块，不依赖 JitPack 上的 Gradle plugin marker。以上是一次性的仓库接入设置，keep 规则不需要配置。

远端发布状态和实测结果见 [JitPack 验证记录](docs/jitpack-validation.html)。首次请求可能需要等待 JitPack 构建；可在 [JitPack 项目页](https://jitpack.io/#zerofancy/ComposeDesktopKeepKit) 查看状态。尚未发布到 Gradle Plugin Portal / Maven Central。

源码开发仍可在 `pluginManagement` 使用 `includeBuild("/path/to/ComposeDesktopKeepKit")`，此时插件声明不写版本。

执行原来的 `runRelease` / Release 打包命令即可。插件不会改变是否启用 ProGuard、优化或混淆，也不会修改已有用户规则。

不需要 `keepKit` 配置块。只有想禁用某个坐标时才添加：

```kotlin
keepKit {
    excludedDependencies.add("io.coil-kt.coil3:coil-core-jvm")
}
```

禁用按精确 `group:module` 匹配，不含版本、不支持通配符。只影响这个坐标；禁用所有 Coil 条目时需要同时列出 `coil-core-jvm`、`coil-network-okhttp-jvm` 和 `coil-svg-jvm`。

## 规则与版本选择

当前内置规则版本：

| 依赖坐标 | 规则版本 | 用途 |
| --- | --- | --- |
| `io.coil-kt.coil3:coil-core-jvm` | 3.3.0 | 保留 Coil 服务接口及动态实现 |
| `io.coil-kt.coil3:coil-network-okhttp-jvm` | 3.3.0 | 同上，网络 fetcher |
| `io.coil-kt.coil3:coil-svg-jvm` | 3.3.0 | 同上，SVG decoder |
| `com.squareup.okhttp3:okhttp` | 4.12.0 | 可选 TLS / 平台引用警告及 PublicSuffixDatabase 名称 |
| `com.squareup.okio:okio-jvm` | 3.15.0 | 防止 ProGuard 优化 Kotlin 多文件门面后的 VerifyError |

同一坐标只选一个规则版本：优先选择 **不高于实际版本的最高版本**；若没有，则选择 **高于实际版本的最低版本**。采用 Maven `ComparableVersion` 3.9.9，包含数字段和预发布排序；不设置版本区间，也不阻断跨主版本回退。版本回退不代表所有版本都经过运行验证。

每次 Release 构建都会打印应用日志，生成任务命中缓存时也会打印：

```text
[KeepKit] 自动为依赖 io.coil-kt.coil3:coil-core-jvm:3.3.0 添加了 keep 规则（规则版本：3.3.0，精确匹配）
```

未知依赖、被禁用的依赖不打印自动添加日志。相同规则片段去重，各依赖应用记录保留。

## 输出与诊断

缓存位置：`<GRADLE_USER_HOME>/caches/compose-desktop-keep-kit/<plugin-version>/work/<build-scope>/<content-key>/`。不同项目和不同匹配结果隔离，原子写入并加锁。清空缓存后自动重建。

- `generateKeepKitRules`：生成规则和 JSON。
- `keepKitReport`：按需生成 HTML，并打印可打开的绝对路径。
- `announceKeepKitRules`：Release 自动调用的日志步骤，通常无需手动运行。

消费项目的 Gradle / Compose 自己仍会产生正常的构建输出；“项目外输出”指本插件生成的文件。

## 开发与验证

```bash
./gradlew check
./gradlew -p samples/coil-desktop runRelease
./gradlew -p samples/coil-kmp runRelease
./gradlew publishAllPublicationsToLocalBuildRepository
# 从 JitPack 下载已发布插件，不使用 includeBuild 或 mavenLocal：
./gradlew -p samples/jitpack-consumer runRelease
```

两个样例通过本地 HTTP 请求 PNG 和 SVG，在优化和混淆开启的真实 Release JAR 中断言获取与解码成功；成功后结束进程，避免自动创建的 OkHttp 客户端空闲线程拖延退出。它们是无窗口运行烟测，不验证 GUI 或原生安装包。

负例（预期失败，确认 Coil 自动规则确实必要）：

```bash
./gradlew -p samples/coil-desktop runRelease -PkeepKitNegative=true
```

当前 Linux 验证基线：Gradle 8.14.3、Kotlin 2.2.0、Compose 1.9.3、ProGuard 7.7.0，插件/样例字节码目标 JDK 17，运行 JDK 21。支持 JVM SourceSet 和单 JVM 目标 KMP 的 Compose application；选中的 `application.from(SourceSet/target)` 由 Compose 模型适配器读取。自定义 `fromFiles` 输入不支持，会明确失败。该适配器涉及 Compose 内部模型，其他 Compose 版本需要验证。

TestKit 覆盖最终版本冲突/传递依赖、插件顺序、禁用、KMP/指定 SourceSet、未知依赖跳过、配置缓存复用、UP-TO-DATE、FROM-CACHE 和项目外输出。CI 配置了 Linux/Windows/macOS 验证，未完成的远端平台检查不能视为已通过。

本地 Maven 发布产物位于 `build/repository`（插件项目自己的发布目录），包含 plugin marker 和实现模块；这不是消费项目的生成规则目录。

设计文档：[spec](docs/spec.html)。规则贡献请阅读 [贡献说明](CONTRIBUTING.md)。

## 许可证

Apache-2.0；规则来源及第三方组件见 [NOTICE](NOTICE)。

## JitPack 发布

仓库根目录的 `jitpack.yml` 固定 JDK 17，仅执行 `publishPluginMavenPublicationToMavenLocal`，发布插件实现及源码。JitPack 环境使用其 `GROUP` / `VERSION` 生成 Maven 坐标；常规本地构建仍使用 `io.github.zerofancy` 坐标。完整测试由仓库 CI 执行。

新版本提交通过验证后创建新的 Git 标签，不移动已有发布标签。请求对应 JitPack POM 触发构建，再用 `samples/jitpack-consumer` 验证真实插件下载、Release 收缩及 PNG/SVG 加载。
