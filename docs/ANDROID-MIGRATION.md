# Android 迁移调研与实施方案

基线：上游提交 `b99c845dca27efe63b0242ca2077cb1a5d45049e`。这是调研方案，尚未实现或验证 Android 版本。

## 项目现状

Tower 是 SwiftUI 原生应用，负责在本地管理订阅、生成配置，不提供代理连接。不能直接将 iOS UI 工程编译为 Android APK。

- `Tower/Services/SubscriptionParser.swift`：2,613 行，包括下载服务、请求控制、URI、Clash YAML 等解析；文件依赖 Foundation 和 Apple Network。
- `Tower/Services/ConfigurationGenerator.swift`：5,577 行，生成客户端配置；文件直接导入 Foundation，仍依赖项目中其他类型，不能认定可直接独立移植。
- `Tower/Models/DomainModels.swift`：2,245 行，模型包含节点、协议、客户端目标等，导入 CryptoKit、Darwin 和 Foundation。
- `TowerTests/SubscriptionParserTests.swift`、`ConfigurationGeneratorTests.swift` 及相关协议测试可用来构造跨实现对照样例；应确认测试输入不含真实订阅或凭据。
- 原有模型包含 Shadowsocks、VMess、VLESS、Trojan、Hysteria 等；客户端目标包含 Clash、sing-box 等，但具体协议与各目标之间的支持矩阵必须逐项核验。

业务代码规模和平台依赖说明“一键转换”不现实；Swift 在 Android 的工具链、互操作和 Apple 特有模块均需另外验证，不能先假设复用 Swift 比移植更省事。

## 推荐方案

在同一仓库增加独立 `android/` 目录，保留现有 iOS 项目不变。使用 Kotlin 原生 Android，界面可采用 Jetpack Compose；业务核心尽量是纯 Kotlin 模块，避免直接依赖 Android UI。

建议模块边界：

1. `core-model`：订阅来源、节点、规则、导出结果；明确版本化的数据模型。
2. `core-parser`：订阅文本/Base64、节点 URI 和所选输入格式解析，处理失败时明确列出不支持或跳过的内容。
3. `core-export`：先实现单一目标客户端配置；业务规则和序列化分离，添加脱敏 golden fixtures 与上游输出对照。
4. `data`：HTTP、持久化、导入导出；订阅 URL 和节点凭据不写入日志，审查备份策略、文件访问和分享权限。
5. `app`：Android 界面、系统文件选择器和分享入口。

具体模块数量可先合并以降低首版构建复杂度，边界比 Gradle 子项目数量更重要。

## 第一阶段范围

- 用户粘贴订阅链接，下载并解析，展示节点及明确的错误/不支持信息。
- 选择节点，生成一种已验证可由目标 Android 客户端导入的配置，使用系统文件选择器导出。
- 首先依据真实需求确定目标客户端及其版本，选择上游已有的对应格式，再做端到端导入验证；不承诺所有 Clash 或 sing-box 客户端之间格式完全兼容。
- 不内置代理内核、VpnService、iCloud、Tailscale、地图和测速；也不宣称协议全覆盖。
- 测试使用虚构数据，覆盖 Base64、字符编码、URL 转义、IPv6、重复节点、异常输入和配置字段；复用上游脱敏测试用例并核对导出一致性。

## 后续阶段

逐步补齐协议和传输参数、规则与策略组、订阅刷新、其他客户端导出、节点测速和地图。每增加一项都要更新输入、输出兼容矩阵及测试；协议无法转换时禁止静默丢失关键安全参数。

## 许可与构建

`LICENSE` 对源码采用 MIT，保留版权与许可声明；`Tower/Resources/` 中第三方规则及 IP 数据库按各自许可处理，打包前核对 `THIRD-PARTY-NOTICES.md` 及相关资源声明。

本调研设备的 Android doctor 检测为 `STATUS=READY`（Java 17、Gradle、Android 34、AAPT2、D8、APK 签名工具）。这只证明 Java Android 基础工具可用，**不证明 Kotlin/Compose 项目能成功构建**；实施前须完成最小工程构建，必要时使用标准 Linux CI。

## 尚待确认

目标 Android 客户端及版本、首版必须支持的输入格式和节点协议、规则资源分发许可，以及每种格式的测试基准。尚未完成完整依赖审计、工期评估、Android APK 构建或真机验证。
