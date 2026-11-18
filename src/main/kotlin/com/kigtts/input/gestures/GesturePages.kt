package com.kigtts.input.gestures

import com.kigtts.plugin.api.*

internal object GesturePages {
    fun settings(settings: GestureSettings, catalog: QuickTextSnapshot?, text: (String, String) -> String = { _, fallback -> fallback }) = PluginPage("gestures", text("title", "手势触发"), buildList {
        add(PageBlock.Toggle(text("enable", "启用手势触发"), settings.enabled, "master", text("gesture.summary", "在字幕区一笔画出手势，发送绑定的快捷文本。"), symbol = "gesture"))
        GestureTemplates.all.forEach { template ->
            val binding = settings.binding(template.id)
            val entry = catalog?.entries?.firstOrNull { it.id == binding.entryId }
            add(PageBlock.StrokeGuide(text("gesture.${template.id}.name", template.name), text("gesture.${template.id}", template.instruction),
                    template.points.map { StrokePoint(it.x, it.y) }, entry?.let { label(it, text) } ?: text("missing", "未选择快捷文本"),
                    ManagementToggle(binding.enabled, "toggle:${template.id}"),
                    listOf(PageAction("choose:${template.id}", text("choose", "选择快捷文本"), PluginIcon.TEXT),
                        PageAction("clear:${template.id}", text("clear", "清空绑定"), PluginIcon.CLOSE))))
        }
    }, onEnterAction = "load-library")
    fun chooseGroups(gesture: String, catalog: QuickTextSnapshot, text: (String, String) -> String = { _, fallback -> fallback }) = PluginOutcome.Menu(text("choose_group", "选择快捷文本分组"), buildList {
        catalog.groups.filter { group -> catalog.entries.any { it.groupId == group.id } }.forEach {
            add(PageAction("group:$gesture:${it.id}:0", it.name, PluginIcon.TEXT))
        }
        if (isEmpty()) add(PageAction("clear:$gesture", text("empty", "还没有快捷文本"), PluginIcon.TEXT))
    })
    fun chooseEntries(gesture: String, group: String, offset: Int, catalog: QuickTextSnapshot, text: (String, String) -> String = { _, fallback -> fallback }): PluginOutcome.Menu {
        require(offset >= 0)
        val entries = catalog.entries.filter { it.groupId == group }
        return PluginOutcome.Menu(text("choose", "选择快捷文本"), buildList {
            entries.drop(offset).take(20).forEach { add(PageAction("bind:$gesture:${it.id}", label(it, text), PluginIcon.TEXT)) }
            if (offset >= 20) add(PageAction("group:$gesture:$group:${offset - 20}", text("previous", "上一页"), PluginIcon.MENU))
            if (offset + 20 < entries.size) add(PageAction("group:$gesture:$group:${offset + 20}", text("next", "下一页"), PluginIcon.MENU))
            add(PageAction("choose:$gesture", text("other_group", "选择其它分组"), PluginIcon.MENU))
        })
    }
    private fun label(entry: QuickEntry, text: (String, String) -> String) = entry.title.ifBlank { entry.text }.replace('\n', ' ').take(60).ifBlank {
        when (entry.kind) { QuickCardKind.AUDIO -> text("audio", "音频卡片"); QuickCardKind.MEDIA -> text("media", "媒体卡片"); QuickCardKind.PAGE -> text("page", "网页卡片"); else -> text("text", "快捷文本") }
    }
}
