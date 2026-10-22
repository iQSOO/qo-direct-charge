# 从源码构建

官网与使用教程：[www.iqsoo.com](https://www.iqsoo.com)。本页面向需要自行构建的开发者；普通用户直接下载 README 底部的 APK 即可。

## 已验证的依赖组合

| 项目 | 固定版本 |
|---|---|
| JDK | 17 |
| compileSdk / targetSdk | 36 / 36 |
| minSdk / Build Tools | 29 / 36.0.0 |
| Android Gradle Plugin / Gradle | 8.13.2 / 8.13 |
| Kotlin / Compose Compiler | 2.2.21 / 2.2.21 |
| Compose BOM | 2025.10.01 |
| Shizuku API / Provider | 13.1.5 |
| Room / KSP | 2.7.2 / 2.2.21-2.0.4 |
| DataStore / WorkManager | 1.1.7 / 2.10.5 |
| Coroutines / Serialization | 1.10.2 / 1.9.0 |

以上是已完成本版本构建和检查的组合，不代表宣称每个组件都是最新版本。Gradle Wrapper 配置保留官方发行包校验值。

## 构建

安装 JDK 17、Android API 36 SDK 与 Build Tools 36.0.0，配置 `JAVA_HOME`、`ANDROID_HOME`；也可仅在本机构建目录设置 `local.properties` 的 `sdk.dir`，不要提交该文件。

```sh
chmod +x gradlew
./gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleDebug assembleDebugAndroidTest
./gradlew --no-daemon --max-workers=1 lintDebug
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`，设备 UI 测试包位于 `app/build/outputs/apk/androidTest/debug/`。Windows 可使用 `gradlew.bat`，并按本机方式配置环境变量。

Gradle 配置默认单工作线程和受限 JVM 内存，可根据开发机资源合理调整。项目没有自有 NDK 编译步骤；AndroidX 的传递原生库仍需随 APK 保留。

## 检查范围

JVM 测试验证真实规则、控制器、供电门禁、重试策略与前台事件聚合逻辑。Fake 仅替代系统传输，不用于正式应用界面或假装设备已成功。

连接专用设备后可运行：

```sh
./gradlew connectedDebugAndroidTest
```

设备 UI 测试不点击充电开启/关闭，不代替真机原厂反馈与后台运行验收。完整清单见 [DEVICE_CHECKLIST.md](../DEVICE_CHECKLIST.md)。

## 签名与升级

个人构建使用本机 Debug 密钥，仓库不包含发布者的任何私钥。签名不同的 APK 通常不能覆盖安装，先导出规则再处理个人测试安装，避免丢失本地配置。

GitHub 发布的 1.0.1 APK 与先前交付的安装包保持相同证书和字节内容；它是 Debug 签名，不冒充正式发布签名。未来若迁移正式签名，需要另行明确升级路径。
