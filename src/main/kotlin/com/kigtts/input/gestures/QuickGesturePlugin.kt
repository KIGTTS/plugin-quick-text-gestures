package com.kigtts.input.gestures

import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray

class QuickGesturePlugin : KigPlugin {
    override val descriptor = PluginDescriptor("input.quick-gestures", "手势触发", "一笔手势，快速发送绑定的快捷文本", "0.1.0", license = "GPL-3.0-only")
    private lateinit var environment: PluginEnvironment
    private lateinit var settings: GestureSettings
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val revision = MutableStateFlow(0L)
    override val uiChanges = revision
    @Volatile private var catalog: QuickTextSnapshot? = null
    private var watcher: Job? = null
    private val broker get() = requireNotNull(environment.quickTextBroker) { "当前应用未开放快捷文本读取" }
    override val contributions get() = PluginContributions(
        settingsEntries = listOf(SettingsEntry("gesture-settings", "手势触发", "gestures", PluginIcon.TEXT,
            cardId = "content", cardTitle = "内容", symbol = "gesture")),
        gestures = if (::settings.isInitialized && settings.enabled && settings.active().any { settings.binding(it).entryId in catalog?.entries.orEmpty().map { entry -> entry.id } })
            listOf(GestureAction("subtitle-stroke", "画手势发送快捷文本", PluginGesture.SUBTITLE_STROKE)) else emptyList())
    override suspend fun attach(environment: PluginEnvironment) {
        this.environment = environment
        settings = GestureSettings(environment.storage.file("config/gestures.properties"))
        if (settings.enabled || settings.hasBindings()) startWatcher()
    }
    override fun page(pageId: String, context: PluginContext): PluginPage? = if (pageId == "gestures") GesturePages.settings(settings, catalog) else null
    override suspend fun execute(actionId: String, context: PluginContext): PluginOutcome = mutex.withLock {
        try {
            when {
                actionId == "load-library" -> { refresh(); if (settings.enabled) startWatcher(); PluginOutcome.RefreshUi }
                actionId == "master" -> {
                    val next = context.inputs["value"]?.toBooleanStrictOrNull() ?: !settings.enabled
                    if (next) refresh()
                    settings.enabled = next; settings.save()
                    if (next || settings.hasBindings()) startWatcher()
                    revision.value++; PluginOutcome.RefreshUi
                }
                actionId == "subtitle-stroke" -> recognize(context)
                actionId.startsWith("toggle:") -> {
                    val id = actionId.removePrefix("toggle:"); known(id); refresh()
                    val current = settings.binding(id)
                    val next = context.inputs["value"]?.toBooleanStrictOrNull() ?: !current.enabled
                    if (next && current.entryId.isBlank()) GesturePages.chooseGroups(id, requireNotNull(catalog))
                    else { settings.set(id, current.copy(enabled = next)); revision.value++; PluginOutcome.RefreshUi }
                }
                actionId.startsWith("choose:") -> {
                    val id = actionId.removePrefix("choose:"); known(id); refresh()
                    GesturePages.chooseGroups(id, requireNotNull(catalog))
                }
                actionId.startsWith("group:") -> {
                    val parts = actionId.split(':', limit = 4); require(parts.size == 4); known(parts[1]); refresh()
                    GesturePages.chooseEntries(parts[1], parts[2], parts[3].toInt(), requireNotNull(catalog))
                }
                actionId.startsWith("bind:") -> {
                    val parts = actionId.split(':', limit = 3); require(parts.size == 3); known(parts[1]); refresh()
                    require(catalog!!.entries.any { it.id == parts[2] }) { "快捷文本已被删除，请重新选择" }
                    settings.set(parts[1], GestureBinding(parts[2], true)); startWatcher(); revision.value++; PluginOutcome.RefreshUi
                }
                actionId.startsWith("clear:") -> {
                    val id = actionId.removePrefix("clear:"); known(id); settings.set(id, GestureBinding()); revision.value++; PluginOutcome.RefreshUi
                }
                else -> PluginOutcome.NoAction
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) {
            catalog = null; revision.value++
            if (actionId == "subtitle-stroke") PluginOutcome.NoAction
            else PluginOutcome.Dialog("手势设置未完成", error.message?.take(240) ?: "请重新选择快捷文本")
        }
    }
    private suspend fun recognize(context: PluginContext): PluginOutcome {
        if (!settings.enabled || context.contentKind != "TEXT") return PluginOutcome.NoAction
        val input = context.inputs
        val raw = input["gesture.points"] ?: return PluginOutcome.NoAction
        if (raw.length > 32768) return PluginOutcome.NoAction
        val points = runCatching {
            val rows = JSONArray(raw); require(rows.length() in 8..512)
            List(rows.length()) { index -> rows.getJSONArray(index).let { GesturePoint(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
        }.getOrNull() ?: return PluginOutcome.NoAction
        val duration = input["gesture.durationMs"]?.toLongOrNull() ?: return PluginOutcome.NoAction
        val width = input["gesture.width"]?.toFloatOrNull() ?: return PluginOutcome.NoAction
        val height = input["gesture.height"]?.toFloatOrNull() ?: return PluginOutcome.NoAction
        val id = GestureRecognizer(settings.active()).recognize(points, duration, width, height) ?: return PluginOutcome.NoAction
        // Re-resolve at trigger time, even if an invalidation is still in flight.
        refresh()
        val binding = settings.binding(id)
        return if (binding.enabled && binding.entryId.isNotBlank()) PluginOutcome.SendQuickText(binding.entryId) else PluginOutcome.NoAction
    }
    private suspend fun refresh() {
        var first: QuickTextSnapshot? = null
        val entries = mutableListOf<QuickEntry>()
        var offset = 0
        do {
            val result = broker.request(QuickTextCommand.Query(offset = offset, revision = first?.revision))
            if (result is QuickTextReply.Failure) error(result.message)
            val page = (result as? QuickTextReply.Snapshot)?.value ?: error("无法读取快捷文本")
            if (first == null) first = page
            require(page.revision == first.revision) { "快捷文本已变化，请重新选择" }
            entries += page.entries
            require(entries.size <= 10000) { "快捷文本过多，请先整理分组" }
            val next = page.nextOffset ?: break
            require(next > offset) { "快捷文本分页异常" }; offset = next
        } while (true)
        val snapshot = requireNotNull(first).copy(entries = entries, total = entries.size, nextOffset = null, offset = 0)
        // Failure/denial never clears a binding. Only a complete successful snapshot can do so.
        settings.removeMissing(snapshot.entries.map { it.id }.toSet())
        catalog = snapshot; revision.value++
    }
    @OptIn(FlowPreview::class)
    private fun startWatcher() {
        if (watcher?.isActive == true) return
        watcher = scope.launch {
            try { broker.changes().debounce(100).collect { mutex.withLock { refresh() } } }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { catalog = null; revision.value++ }
        }
    }
    private fun known(id: String) { require(GestureTemplates.all.any { it.id == id }) { "没有这个手势" } }
    override suspend fun close() { scope.cancel(); catalog = null }
}
