<p align="center">
  <a href="https://www.iqsoo.com"><img src="docs/assets/hero.png" width="100%" alt="QO 智能直驱品牌封面，手机区域为首页布局示意" /></a>
</p>

<h1 align="center">QO 智能直驱</h1>
<p align="center"><b>由新好友开发，<a href="https://www.iqsoo.com">iQSOO（iqsoo.com）</a> 发起并开源发布。</b></p>
<p align="center">1.0.1 · iQOO Neo11 · Android 16 / OriginOS · 无需 Root</p>
<p align="center"><a href="https://www.iqsoo.com">访问官网</a> · <a href="https://b23.tv/TRkUNyx">观看演示</a> · <a href="https://www.iqsoo.com/7656.html">使用教程</a> · <a href="#下载">下载 APK</a></p>

如果你用 iQOO Neo11 玩横屏游戏，又不想每次自己去点直驱开关，可以试试我做的这个工具。

接上有线充电器，打开智能管理，之后就按你设好的条件处理。你可以让横屏触发，也可以只给常玩的几个游戏用；退出游戏、转回竖屏、息屏以后，再按设置的延迟结束接管。

我把源码和 APK 都放在这个仓库，后续更新也在这里维护。使用教程和其他手机工具，继续放在 **[www.iqsoo.com](https://www.iqsoo.com)**。

## 先看演示

**[iQOO 手机自动开启与关闭直驱供电｜哔哩哔哩](https://b23.tv/TRkUNyx)**

想先看操作过程，可以直接打开视频。下面把安装和几个容易卡住的地方说清楚。

## 软件里能看到什么

<img src="docs/assets/ui-overview.png" width="100%" alt="QO 智能直驱首页、规则和诊断页面布局示意" />

首页看有没有接上充电器、当前横竖屏和直驱反馈。需要限制游戏或修改延迟，就去规则和应用页面。遇到没有触发、开启失败的情况，再到诊断和日志里看原因。界面支持深色模式，也能跟随系统配色。

## 装之前，先确认手机和 Shizuku

当前针对 **iQOO Neo11（V2520A），Android 16 / OriginOS** 开发。其他型号和系统版本还需要分别验证。

工具通过 Shizuku 调用原厂设置入口，不需要 Root。你要先把 Shizuku 启动起来，再给本应用授权。不会配对的，先看站内这篇 **[Shizuku 无线调试启动教程](https://www.iqsoo.com/91.html)**。

配对完成后，记得回 Shizuku 点启动，看到服务正在运行，再打开 QO 智能直驱。手机重启以后，也先检查一下 Shizuku 的运行状态。

目前自动触发使用 USB / AC 有线供电，无线和底座供电暂不开放。应用本身没有联网权限，规则和日志留在手机里，官网链接交给浏览器打开。

## 第一次怎么开起来

1. 下载本页底部的 APK 并安装。升级旧版时，先暂停智能管理，再覆盖安装。
2. 启动 Shizuku，打开 QO 智能直驱，点击“申请 / 检查 Shizuku 授权”，允许访问。
3. 到工具里的诊断页检查连接。无 Root 的控制服务应显示 shell UID **2000**。
4. 接上有线充电器，回首页打开 **“智能管理”**，允许通知权限。
5. 先启用横屏触发，关闭“横屏仅限所选应用”，保存设置。找一个能真正全屏横向显示的应用，保持几秒，看首页反馈。
6. 如果之前点过手动开启或关闭测试，先点“结束手动优先，恢复自动管理”。

这里最容易漏的就是首页总开关。**Shizuku 已授权以后，还要打开智能管理，自动规则才会工作。**

默认横屏持续 2 秒请求开启，恢复竖屏持续 10 秒退出。手机拿横了，但应用还在竖屏显示，就先检查系统旋转锁定和应用方向设置。

## 1.0.1 这次改了什么

先把充电器判断放到前面。没有接上已验证的有线电源时，自动规则、开启重试、直驱状态轮询和前台应用查询都停下来。重新插上以后，再看当前条件。

另一个改动是横屏开启失败后的重试。一次请求结束后还没确认成功，就先等 5 秒，再等 10 秒、20 秒，之后每隔 30 秒重新检查。每次都会先读系统状态，再看供电、横屏、权限和保护条件。确认成功，或者原条件已经不成立，就结束这次重试。

如果 Settings 已经开启，只是在等 Bypass 标志同步，也会先确认反馈，避免重复写设置。首页能看到等待原因和下一次评估，具体执行结果在日志里。

## 只给常玩的游戏用

先让普通横屏触发跑通，再到应用页搜索你要用的游戏。可以给不同应用设置进入、退出延迟，也可以加上“必须横屏”的条件。需要排除的应用放进黑名单。

比如你只想让两个游戏使用这套规则，就选中这两个，其他应用不参与。两个都符合条件的游戏之间切换时，可以保持接管；退出以后再按延迟处理。使用应用识别功能时，需要打开使用情况访问权限。

想把电量、温度和时间段一起加进去，可以在规则页组合 AND、OR、NOT，设置优先级和生效时段。规则支持复制、编辑和导入导出。

## 息屏和手动开关怎么处理

默认息屏 60 秒后退出，也可以改成其他延迟、自定义时间或保持原状态。提前亮屏会取消尚未到期的退出任务，再看当前条件。Doze 和厂商后台管理可能延后执行，具体时间以系统调度为准。

你随时可以暂停自动管理，或者改用手动控制。检测到后续原生开关变化时，软件会保留手动优先，避免两个开关反复争抢。

结束自动管理时，会尽可能恢复接管前状态。如果原厂直驱本来就由你手动开启，恢复以后也可能继续开启。要明确关闭，可以去诊断页做关闭测试，再刷新确认。

## 没触发时，先查这几处

- **授权正常，却没有动作：** 看智能管理是否打开，服务是否运行，有没有暂停或进入手动优先。
- **一直等充电器：** 看 USB / AC 外部供电信息。旁路供电时，电池状态可能和普通充电不同。
- **指定游戏没命中：** 看使用情况访问、所选应用和前台识别结果。分屏或前台状态不明确时，先回到单个应用全屏测试。
- **Settings 开了，Bypass 没同步：** 到诊断和日志里看确认结果，符合条件时会按间隔继续检查。
- **重启后不能控制：** 先重新启动 Shizuku，再回来检查授权和管理状态。

Settings 表示原生配置，Bypass 是厂商反馈标志，两者可能延迟同步。电源芯片的实际工作模式，还需要结合设备验证。

原厂温控和电池保护保留。软件额外设置的默认触发电量为 20%～100%，温度达到 42°C 暂停，降到 39°C 后再评估；这些条件可以在保护页面调整。

## 想看源码和测试记录

项目使用 Kotlin、Jetpack Compose、Material 3 和官方 Shizuku API。控制请求由统一规则引擎处理，核心控制与界面分开。

1.0.1 已完成 API 36 编译、94 项 JVM 单元测试、Lint、APK 签名与 16 KB 对齐检查。设备 UI 测试完成编译，新版重试、原生关闭反馈和 OriginOS 后台表现仍需真机验证。

- [测试报告](TEST_REPORT.md) · [设备验收清单](DEVICE_CHECKLIST.md) · [更新记录](CHANGELOG.md)
- [构建说明](docs/BUILDING.md) · [项目架构](docs/ARCHITECTURE.md)
- [参与贡献](CONTRIBUTING.md) · [安全反馈](SECURITY.md) · [第三方许可](THIRD_PARTY_NOTICES.md)

项目自有代码采用 [MIT License](LICENSE)，第三方组件保留各自许可。

## 下载

**当前版本 1.0.1，versionCode 2。** APK 使用 Debug 签名，沿用此前交付版本的证书。

| 文件 | 下载地址 |
|---|---|
| **Android APK · 约 29.09 MiB** | **[下载 QO-Direct-Charge-debug.apk](https://github.com/iQSOO/qo-direct-charge/releases/download/v1.0.1/QO-Direct-Charge-debug.apk)** |
| 中国移动云盘 · 1.0.1 | [备用下载](https://yun.139.com/shareweb/#/w/i/2yA8vdzxXuha4) |
| 完整公开源码 | [QO-Direct-Charge-source.zip](https://github.com/iQSOO/qo-direct-charge/releases/download/v1.0.1/QO-Direct-Charge-source.zip) |
| 文件校验 | [SHA256SUMS.txt](https://github.com/iQSOO/qo-direct-charge/releases/download/v1.0.1/SHA256SUMS.txt) |
| 版本记录 | [GitHub Releases](https://github.com/iQSOO/qo-direct-charge/releases) |

APK SHA-256：

```text
bcdfde0190b952d22826140af3a3ec9b4d5fd7e0a3e326518897b55328083f2d
```

<p align="center"><b>新好友 · iQSOO</b><br /><a href="https://www.iqsoo.com">www.iqsoo.com</a></p>
