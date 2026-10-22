package com.kigtts.input.gestures

import com.kigtts.plugin.api.*

internal object GesturePages {
    fun settings(settings: GestureSettings, catalog: QuickTextSnapshot?) = PluginPage("gestures", "手势触发", buildList {
        add(PageBlock.Toggle("启用手势触发", settings.enabled, "master", "在大字幕区域一笔画出手势，发送绑定的快捷文本。", symbol = "gesture"))
        GestureTemplates.all.forEach { template ->
            val binding = settings.binding(template.id)
            val entry = catalog?.entries?.firstOrNull { it.id == binding.entryId }
            add(PageBlock.Group(template.name, listOf(
                PageBlock.Toggle("启用", binding.enabled, "toggle:${template.id}", template.instruction, symbol = "gesture"),
                PageBlock.Actions(listOf(PageAction("choose:${template.id}", entry?.let(::label) ?: "选择快捷文本", PluginIcon.TEXT),
                    PageAction("clear:${template.id}", "清空绑定", PluginIcon.CLOSE))),
            )))
        }
    }, onEnterAction = "load-library")
    fun chooseGroups(gesture: String, catalog: QuickTextSnapshot) = PluginOutcome.Menu("选择快捷文本分组", buildList {
        catalog.groups.filter { group -> catalog.entries.any { it.groupId == group.id } }.forEach {
            add(PageAction("group:$gesture:${it.id}:0", it.name, PluginIcon.TEXT))
        }
        if (isEmpty()) add(PageAction("clear:$gesture", "还没有快捷文本", PluginIcon.TEXT))
    })
    fun chooseEntries(gesture: String, group: String, offset: Int, catalog: QuickTextSnapshot): PluginOutcome.Menu {
        require(offset >= 0)
        val entries = catalog.entries.filter { it.groupId == group }
        return PluginOutcome.Menu("选择快捷文本", buildList {
            entries.drop(offset).take(20).forEach { add(PageAction("bind:$gesture:${it.id}", label(it), PluginIcon.TEXT)) }
            if (offset >= 20) add(PageAction("group:$gesture:$group:${offset - 20}", "上一页", PluginIcon.MENU))
            if (offset + 20 < entries.size) add(PageAction("group:$gesture:$group:${offset + 20}", "下一页", PluginIcon.MENU))
            add(PageAction("choose:$gesture", "选择其它分组", PluginIcon.MENU))
        })
    }
    private fun label(entry: QuickEntry) = entry.title.ifBlank { entry.text }.replace('\n', ' ').take(60).ifBlank {
        when (entry.kind) { QuickCardKind.AUDIO -> "音频卡片"; QuickCardKind.MEDIA -> "媒体卡片"; QuickCardKind.PAGE -> "网页卡片"; else -> "快捷文本" }
    }
}
