# 架构与控制边界

界面采用 Compose + Material 3，ViewModel 观察 StateFlow；DataStore 保存软件策略，Room 保存规则及日志。长期任务由可取消协程管理，前台服务使用真实用途的 `specialUse` 类型。

## 请求如何到达系统

`AutomationRuntime` 先通过 `PowerConnectionGate` 检查实际外部供电，再让 `RuleEngine` 根据最新环境计算唯一决策。控制命令经串行队列与 `VivoDirectChargeController` 的 Mutex 执行，通过官方 Shizuku UserService 在 shell UID 2000 下运行固定参数。

允许写入的原厂入口仅为：

```sh
settings put secure settings_battery_charge_director 1
settings put secure settings_battery_charge_director 0
```

Settings、Bypass 和两个支持属性只读回查；厂商 persist 属性不写入。普通 App 进程不执行特权命令，不使用私有 `Shizuku.newProcess` 或反射绕过。

每次请求检查退出码，等待 Settings 与 Bypass 在确认期限内一致；失败、超时或仅一项成功均不会被报告为硬件已生效。

## 自动重试与取消

`AutomaticEnableRetry` 只处理当前自动开启失败，且当前生效规则、亮屏解锁、实际横屏、有线供电、电量温度和用户管理状态必须持续符合条件。重试间隔在失败结束后按 5、10、20 秒增长，之后保持 30 秒，无总次数上限。

每次到期先强制读取原生状态；已经双反馈确认的目标不再写入。旧规则失效、手动优先、断电、保护、暂停和服务断线会取消旧重试。关闭、恢复与手动测试失败不会被转换成无限自动操作。

未接已验证的有线电源时，自动流程停在门禁前；不计算其他规则、不保持控制心跳、不轮询原生状态。退到后台后停止显示方向订阅，前台应用查询也停止。存在本进程接管记录时仅尝试一次供电退出，失败保留提示；手动诊断可以主动读取。

## 状态证据与恢复

- `BatteryManager.EXTRA_PLUGGED` 判断外部电源，独立于电池是否显示正在充电。
- DisplayManager 读取实际显示方向，不使用手机手持姿态猜测。
- 可选 UsageEvents 建立明确的前台证据，多窗口、缺少权限或旧查询结果不会强行推断前台。
- 息屏、AOD 与亮屏锁定分别判断；Doze 和厂商策略可能延后非精确调度。
- 控制器只保留本进程的接管基线；重启和重新连接读取现实，不重放磁盘中的旧充电状态。
- 请求失败确认窗口内的回退与后续已知状态变化分开处理；同一确认窗口内，单靠设置值不能完全区分厂商回退和原生手动操作。

相关测试结果见 [TEST_REPORT.md](../TEST_REPORT.md)。官网使用说明与更新：[www.iqsoo.com](https://www.iqsoo.com)。
