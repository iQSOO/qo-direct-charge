package com.iqsoo.qodirect.automation

object Validation {
    fun condition(c: Condition, depth: Int = 0) {
        require(depth <= 8) { "条件嵌套不能超过 8 层" }
        require(c.children.size <= 24) { "子条件过多" }
        c.children.forEach { condition(it, depth + 1) }
        when (c.type) {
            ConditionType.ALL,
            ConditionType.ANY -> require(c.children.isNotEmpty()) { "逻辑组合至少需要一个条件" }
            ConditionType.NOT -> require(c.children.size == 1) { "NOT 必须包含一个条件" }
            ConditionType.APP_FOREGROUND,
            ConditionType.APP_EXITED ->
                require(c.value.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))) { "包名无效" }
            ConditionType.BATTERY_RANGE,
            ConditionType.TEMPERATURE_RANGE -> {
                val p =
                    c.value.split(',').mapNotNull { it.toFloatOrNull()?.takeIf(Float::isFinite) }
                require(p.size == 2 && p[0] <= p[1]) { "范围应为下限,上限" }
            }
            ConditionType.TIME_RANGE -> require(validTime(c.value)) { "时间格式应为 HH:mm-HH:mm" }
            else -> Unit
        }
    }

    fun validTime(s: String): Boolean =
        Regex("([01]\\d|2[0-3]):[0-5]\\d-([01]\\d|2[0-3]):[0-5]\\d").matches(s)

    fun rule(r: Rule) {
        require(r.id.length in 1..100 && r.name.isNotBlank() && r.name.length <= 80) {
            "规则名称不能为空，且不能超过 80 字"
        }
        require(r.priority in -1000..1000) { "优先级应在 -1000 至 1000 之间" }
        require(r.delaySeconds in 0..86400 && r.exitDelaySeconds in 0..86400) {
            "规则延迟应在 0 至 86400 秒之间"
        }
        require(validTime(r.window)) { "生效时段格式应为 HH:mm-HH:mm" }
        condition(r.condition)
    }

    fun policy(p: Policy) {
        require(
            p.enterSeconds in 0..3600 && p.exitSeconds in 0..3600 && p.screenOffSeconds in -1..86400
        ) {
            "进入/退出延迟应在 0–3600 秒，息屏延迟应为 -1 或 0–86400 秒"
        }
        require(p.minBattery in 0..100 && p.maxBattery in p.minBattery..100) {
            "电量范围应在 0–100%，上限不能低于下限"
        }
        require(
            p.maxTemperature.isFinite() &&
                p.resumeTemperature.isFinite() &&
                p.maxTemperature in 30f..50f &&
                p.resumeTemperature in 20f..p.maxTemperature
        ) {
            "暂停温度应为 30–50 °C，恢复温度应为 20 °C 至暂停温度"
        }
        require(p.confirmSeconds in 2..15 && p.cooldownSeconds in 0..120) {
            "确认超时应为 2–15 秒，冷却应为 0–120 秒"
        }
        require(p.darkMode in 0..2) { "外观选项无效" }
    }

    fun export(v: RuleExport) {
        require(v.version == 1) { "不支持此配置文件版本" }
        require(v.rules.size <= 100 && v.apps.size <= 300) { "配置最多包含 100 条规则和 300 个应用" }
        policy(v.policy)
        v.rules.forEach(::rule)
        require(v.rules.map { it.id }.distinct().size == v.rules.size) { "规则 ID 不能重复" }
        v.apps.forEach {
            require(it.packageName.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))) { "应用包名无效" }
            require(
                it.name.length <= 200 && it.enterSeconds in 0..3600 && it.exitSeconds in 0..3600
            ) {
                "应用名称过长，或应用延迟不在 0–3600 秒范围内"
            }
        }
    }
}
