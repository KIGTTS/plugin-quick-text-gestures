package com.kigtts.input.gestures

import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class GesturePluginTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun points(id: String, width: Float = 400f, height: Float = 600f): List<GesturePoint> {
        val template = GestureTemplates.all.single { it.id == id }.points
        return template.zipWithNext().flatMap { (a, b) -> (0..11).map { index ->
            val t = index / 12f; GesturePoint((a.x + (b.x - a.x) * t) * width, (a.y + (b.y - a.y) * t) * height)
        } } + template.last().let { GesturePoint(it.x * width, it.y * height) }
    }
    @Test fun allSixOldGesturesRecognizeAtPortraitAndLandscapeSizes() {
        val recognizer = GestureRecognizer(GestureTemplates.all.map { it.id }.toSet())
        listOf(400f to 600f, 800f to 340f).forEach { (width, height) ->
            GestureTemplates.all.forEach { template ->
                assertEquals("${template.id} ${width}x$height", template.id, recognizer.recognize(points(template.id, width, height), 800, width, height))
            }
        }
    }
    @Test fun ordinarySwipesTinyMarksInvalidCoordinatesAndCancelledDurationsNeverTrigger() {
        val recognizer = GestureRecognizer(GestureTemplates.all.map { it.id }.toSet())
        assertNull(recognizer.recognize((0..30).map { GesturePoint(10f + it * 10, 100f + it * 8) }, 500, 400f, 600f))
        assertNull(recognizer.recognize(points("m_shape").map { GesturePoint(it.x / 30, it.y / 30) }, 500, 400f, 600f))
        assertNull(recognizer.recognize(points("m_shape"), 30, 400f, 600f))
        assertNull(recognizer.recognize(points("m_shape"), 8000, 400f, 600f))
        assertNull(recognizer.recognize(points("m_shape") + GesturePoint(Float.NaN, 5f), 500, 400f, 600f))
        assertNull(recognizer.recognize(points("m_shape"), 500, -1f, 600f))
    }
    private class Library(var entries: List<QuickEntry>) : QuickTextBroker {
        val changes = MutableStateFlow(QuickTextChange(1L))
        var denied = false
        val offsets = mutableListOf<Int>()
        override suspend fun request(command: QuickTextCommand): QuickTextReply {
            val query = command as QuickTextCommand.Query
            offsets += query.offset
            if (denied) return QuickTextReply.Failure(QuickTextError.PERMISSION_DENIED, "读取权限未授予")
            val page = entries.drop(query.offset).take(50)
            return QuickTextReply.Snapshot(QuickTextSnapshot(changes.value.revision, listOf(QuickGroup("g", "常用")), page,
                offset = query.offset, total = entries.size, nextOffset = (query.offset + 50).takeIf { it < entries.size }))
        }
        override fun changes(afterRevision: Long): Flow<QuickTextChange> = changes
    }
    private suspend fun plugin(library: Library, root: File = temporary.newFolder()): Pair<QuickGesturePlugin, File> {
        val plugin = QuickGesturePlugin()
        plugin.attach(object : PluginEnvironment {
            override val storage = PluginStorage(root)
            override val assets = temporary.newFolder()
            override val quickTextBroker = library
            override suspend fun download(url: String, relativePath: String, sha256: String?): File = error("not required")
        })
        return plugin to root
    }
    private fun stroke(id: String = "m_shape") = PluginContext("current", "", inputs = mapOf(
        "gesture.points" to JSONArray().also { rows -> points(id).forEach { rows.put(JSONArray().put(it.x).put(it.y)) } }.toString(),
        "gesture.width" to "400", "gesture.height" to "600", "gesture.durationMs" to "800"))
    @Test fun bindingsSendExistingIdsAndDeletionClearsRatherThanRebindingOrSendingOldText() = runBlocking {
        val library = Library(listOf(QuickEntry("first", "g", "旧文字")))
        val (plugin, root) = plugin(library)
        try {
            plugin.execute("bind:m_shape:first", PluginContext("", ""))
            plugin.execute("master", PluginContext("", "", inputs = mapOf("value" to "true")))
            assertEquals(PluginOutcome.SendQuickText("first"), plugin.execute("subtitle-stroke", stroke()))
            library.entries = listOf(QuickEntry("replacement", "g", "旧文字")); library.changes.value = QuickTextChange(2L)
            withTimeout(4000) { while (GestureSettings(File(root, "config/gestures.properties")).binding("m_shape").entryId.isNotEmpty()) delay(20) }
            assertEquals(GestureBinding(), GestureSettings(File(root, "config/gestures.properties")).binding("m_shape"))
            assertEquals(PluginOutcome.NoAction, plugin.execute("subtitle-stroke", stroke()))
            assertTrue(plugin.contributions.gestures.isEmpty())
        } finally { plugin.close() }
    }
    @Test fun fullPaginationFindsLateEntriesAndPermissionDenialNeverErasesBindings() = runBlocking {
        val library = Library((0..120).map { QuickEntry("entry-$it", "g", "词条 $it") })
        val (plugin, root) = plugin(library)
        try {
            assertEquals(PluginOutcome.RefreshUi, plugin.execute("bind:m_shape:entry-120", PluginContext("", "")))
            assertTrue(library.offsets.containsAll(listOf(0, 50, 100)))
            plugin.execute("master", PluginContext("", "", inputs = mapOf("value" to "true")))
            val readsBeforePicker = library.offsets.toList()
            assertTrue(plugin.execute("choose:m_shape", PluginContext("", "")) is PluginOutcome.SelectQuickText)
            assertEquals(readsBeforePicker, library.offsets)
            library.denied = true
            assertTrue(plugin.execute("load-library", PluginContext("", "")) is PluginOutcome.Dialog)
            assertEquals("entry-120", GestureSettings(File(root, "config/gestures.properties")).binding("m_shape").entryId)
            library.denied = false
            assertEquals(PluginOutcome.SendQuickText("entry-120"), plugin.execute("subtitle-stroke", stroke()))
            assertEquals(PluginOutcome.NoAction, plugin.execute("subtitle-stroke", stroke().copy(contentKind = "IMAGE")))
        } finally { plugin.close() }
    }
    @Test fun persistedDisabledBindingsAreAlsoClearedWhenTheOriginalEntryIsRemoved() = runBlocking {
        val root = temporary.newFolder()
        GestureSettings(File(root, "config/gestures.properties")).set("m_shape", GestureBinding("gone", false))
        val (plugin, _) = plugin(Library(emptyList()), root)
        try {
            withTimeout(4000) { while (GestureSettings(File(root, "config/gestures.properties")).hasBindings()) delay(20) }
            assertTrue(plugin.contributions.gestures.isEmpty())
        } finally { plugin.close() }
    }
}
