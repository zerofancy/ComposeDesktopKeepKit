# 规则贡献

规则位于 `src/main/resources/keepkit/`。修改 `catalog.json` 中精确 `group:module` 对应的版本列表，每个版本提供规则文件名和固定上游来源链接。

1. 使用 JVM 最终解析坐标，不使用仅作为 KMP 元数据的坐标。
2. 版本标记表示这份规则的基准依赖版本。选择遵循向下最近优先、否则向上最近，不叠加历史版本。
3. 使用 Maven ComparableVersion 3.9.9。它按数字段而非字符串比较，理解常见 alpha/beta/rc/snapshot 限定符；未知限定符遵循 Maven 比较语义。比较器认为相等的版本不能重复发布。
4. 添加真实收缩运行 fixture。证明规则缺失时场景失败，添加后成功；记录依赖、Gradle/Kotlin/Compose/ProGuard 和 JDK 的固定版本。
5. 优先精确入口或框架接口，不全包 keep。兼容补丁应尽量保留 shrinking/obfuscation，只限制必要优化。警告规则注明可选引用依据。
6. 不添加输入/输出 JAR、include、报告输出路径或全局关闭 shrinking/obfuscation/optimization 的规则。内置规则作为受审查的项目资源发布。
7. 所有内置版本列表非空，规则文件必须存在且非空。规则引擎不访问远端，也不读取用户依赖 JAR 内的规则。
8. 运行 `./gradlew check` 和两个 Coil Release 样例。规则变化在 README 与验证记录中更新。

公开仓库贡献不应包含本机路径、密钥或消费项目的业务代码。
