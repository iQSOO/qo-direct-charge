# 参与贡献

欢迎提交可复现的问题、设备兼容反馈和有针对性的修复。请先阅读 [官网](https://www.iqsoo.com)、[README](README.md) 与 [构建说明](docs/BUILDING.md)。

问题反馈请写清版本、设备型号、Android / OriginOS 版本、Shizuku 服务及授权状态、供电类型、当前生效规则，以及 Settings / Bypass 的实际回读。日志按需节选，不附个人令牌或完整应用使用历史。

修改规则或控制逻辑时，补充能重现问题的纯逻辑测试，验证取消、失败结果和恢复路径。界面修改可附截图，说明系统版本和主题。提交前运行 `./gradlew --no-daemon --max-workers=1 testDebugUnitTest lintDebug assembleDebug`。

控制必须继续通过授权后的官方 Shizuku UserService、固定命令和串行队列；未知状态不能猜测为成功。未经设备验证，不新增充电写入接口、不修改厂商 persist 属性和原厂保护，不扩展应用权限。

个人构建默认使用本机 Debug 密钥，通常不能覆盖安装本项目已经发布的 APK。不要把个人签名私钥提交到仓库。
