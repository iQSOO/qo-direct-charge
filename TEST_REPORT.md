# QO 智能直驱 1.0.1 测试报告

对应包名 `com.iqsoo.qodirect`，versionCode 2。本公开报告基于已经执行的构建和检查，移除了构建机路径及环境标识；不把编译结果当成真机验证。

| 检查 | 结果 |
|---|---|
| API 36 编译、targetSdk 36、Debug APK 打包 | 通过 |
| JVM 单元测试 | **94 项，0 失败，0 错误** |
| Android Lint | **0 错误，13 条可选依赖版本提示** |
| Android 设备 UI 测试包编译 | 通过 |
| Android 设备 UI 测试执行 | 未执行 |
| 模拟器安装与运行 | 未执行 |
| 1.0.1 iQOO Neo11 真机验证 | 未执行 |
| APK ZIP CRC、Debug v2 签名 | 通过 |
| APK 16 KB ZIP 对齐 | 通过 |
| 内置 8 个 ELF 库的 LOAD 对齐 | 16 KB |
| 权限与 Manifest | 无 INTERNET、相机、麦克风、位置、悬浮窗、无障碍及直接写 Secure Settings 权限；声明 specialUse 真实用途 |

## 自动化测试证据

公开 JUnit 报告位于 [docs/verification/junit](docs/verification/junit)，仅把主机标识统一为 `build-environment`，测试结果未改变。

| 测试类 | 通过数量 | 范围 |
|---|---:|---|
| RuleEngineTest | 35 | 三值条件、优先级、连续横屏、竖屏退出、息屏、应用规则、保护与手动优先 |
| ControllerTest | 27 | 固定传输、退出码、双反馈、超时、恢复基线、部分失败及并发合并 |
| ForegroundEvidenceTest | 7 | 前台事件证据、多个 resumed、失效查询和未知状态 |
| AutomaticEnableRetryTest | 16 | 退避持续重试、成功结束、取消条件、回退恢复及手动冲突 |
| PowerConnectionGateTest | 9 | 供电前置、未接电不运行附加规则、单次清理与重新接电防抖 |

App 的正式执行链中没有演示电池状态或 Fake Shizuku。控制器测试中的 Fake 只替代系统接口。

## APK 身份

构建结构检查数据见 [apk-verification.json](docs/verification/apk-verification.json)。

- 文件：`QO-Direct-Charge-debug.apk`，30,503,946 字节。
- SHA-256：`bcdfde0190b952d22826140af3a3ec9b4d5fd7e0a3e326518897b55328083f2d`。
- Debug 证书 SHA-256：`92108a18f3b86ee7bffe7cd072bf14c68393d68c06642487a511e4bed50c6002`。
- minSdk 29；compileSdk / targetSdk 36。保留 AndroidX 自带的原生库，未用删除库的方式伪造页面大小兼容。

## 设备验证边界

用户反馈 1.0.0 开启智能管理后，横屏自动触发成功。此反馈不等于 1.0.1 新增逻辑已经完成真机验收。

仍需确认从已开启状态关闭后的双反馈、自然失败时的重试、OriginOS 后台调度、分屏与 AOD、实际功率、电池电流及电源芯片模式。Settings 与 Bypass 不能单独证明 PMIC 模式。

操作清单见 [DEVICE_CHECKLIST.md](DEVICE_CHECKLIST.md)，不得通过人为加热或修改原厂保护制造测试场景。README 中的界面图明确是布局示意，没有冒充运行截图。
