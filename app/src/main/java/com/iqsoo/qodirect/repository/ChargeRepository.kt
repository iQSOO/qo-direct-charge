package com.iqsoo.qodirect.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.iqsoo.qodirect.automation.*
import com.iqsoo.qodirect.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.storage by preferencesDataStore("qo_policy")

class ChargeRepository(private val context: Context, val dao: ChargeDao, scope: CoroutineScope) {
    val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
        prettyPrint = true
    }
    private val compactJson = Json(json) { prettyPrint = false }
    private val key = stringPreferencesKey("preferences_v1")
    private val editing = Mutex()
    private val mutablePreferences = MutableStateFlow(Preferences())
    val preferences = mutablePreferences.asStateFlow()
    private val loaded = MutableStateFlow(false)
    val ready = loaded.asStateFlow()

    init {
        scope.launch {
            context.storage.data.collect { values ->
                mutablePreferences.value =
                    values[key]?.let { json.decodeFromString<Preferences>(it) } ?: Preferences()
                loaded.value = true
            }
        }
    }

    val rules =
        dao.observeRules()
            .map { rows -> rows.map { json.decodeFromString<Rule>(it.json) } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())
    val logs = dao.observeLogs().stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun update(block: (Preferences) -> Preferences) =
        editing.withLock {
            val stored =
                context.storage.edit { values ->
                    val current =
                        values[key]?.let { json.decodeFromString<Preferences>(it) } ?: Preferences()
                    val next = block(current)
                    Validation.policy(next.policy)
                    values[key] = json.encodeToString(next)
                }
            mutablePreferences.value = json.decodeFromString<Preferences>(stored[key]!!)
            loaded.value = true
        }

    suspend fun saveRule(r: Rule) {
        Validation.rule(r)
        dao.putRule(RuleEntity(r.id, json.encodeToString(r)))
    }

    suspend fun deleteRule(id: String) = dao.deleteRule(id)

    suspend fun putApp(a: AppRule) = update {
        it.copy(apps = it.apps.filterNot { r -> r.packageName == a.packageName } + a)
    }

    suspend fun exportRules(): String {
        val p = preferences.first()
        return json.encodeToString(
            RuleExport(
                policy = p.policy,
                apps = p.apps,
                rules = dao.rules().map { json.decodeFromString<Rule>(it.json) },
            )
        )
    }

    suspend fun importRules(s: String) {
        require(s.length <= 512_000) { "导入文件超过 500 KB" }
        val v = json.decodeFromString<RuleExport>(s)
        Validation.export(v)
        require(!preferences.value.automatic) { "请先停止自动管理，再导入配置" }
        dao.replaceRules(v.rules.map { RuleEntity(it.id, json.encodeToString(it)) })
        update { it.copy(policy = v.policy, apps = v.apps, paused = false) }
    }

    suspend fun log(
        trigger: String,
        action: String,
        result: String,
        code: String = "",
        exit: Int? = null,
        settings: Boolean? = null,
        bypass: Boolean? = null,
    ) {
        dao.log(
            LogEntry(
                trigger = trigger,
                action = action,
                result = result,
                code = code,
                exitCode = exit,
                settings = settings,
                bypass = bypass,
            )
        )
        dao.prune()
    }

    suspend fun exportLogs(): String =
        dao.logs().joinToString("\n") {
            Json { encodeDefaults = true }
                .encodeToString(
                    LogExport(
                        it.at,
                        it.trigger,
                        it.action,
                        it.result,
                        it.code,
                        it.exitCode,
                        it.settings,
                        it.bypass,
                    )
                )
        }
}

@kotlinx.serialization.Serializable
private data class LogExport(
    val at: Long,
    val trigger: String,
    val action: String,
    val result: String,
    val code: String,
    val exitCode: Int?,
    val settings: Boolean?,
    val bypass: Boolean?,
)
