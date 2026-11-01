# Tower Android 实验原型

这是独立的早期迁移实验，不是原版 Tower 的完整移植或正式发布版本。

- 手动粘贴部分 SS、Trojan 或 VLESS 节点 URI，或含此类 URI 的 Base64 文本；不下载订阅 URL。
- 遇到不支持的协议或参数时拒绝整批，不静默丢失参数。
- 生成的是 mihomo `proxies` YAML 片段，**不是完整可直接使用的 Clash 配置**；未经过 mihomo 或客户端导入验证。
- 不提供 VPN、规则组、测速、订阅同步、节点保存、节点勾选或第三方规则数据。
- 文件含明文代理凭据。仅在用户主动选择的位置导出，不应上传或公开。
- 构建和测试状态以实际执行结果为准；不要将实验版视为安全或兼容性已经验证的成品。

本目录不重新分发上游 `Tower/Resources` 中的第三方资源。仓库源码许可见根目录 LICENSE。

## CI 构建

GitHub Actions 使用 Java 17、Gradle 8.11.1 执行 `testDebugUnitTest assembleDebug`；成功后可在对应运行页面下载 `tower-android-debug` artifact。Debug APK 仅供测试，签名不是正式发布签名。
