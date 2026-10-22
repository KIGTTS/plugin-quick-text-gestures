package com.kigtts.input.gestures

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

internal data class GestureBinding(val entryId: String = "", val enabled: Boolean = false)
internal class GestureSettings(private val file: File) {
    @Volatile var enabled = false
    private val bindings = java.util.concurrent.ConcurrentHashMap<String, GestureBinding>()
    init {
        runCatching {
            require(file.length() <= 16384)
            val properties = Properties().apply { file.inputStream().use(::load) }
            enabled = properties.getProperty("enabled") == "true"
            GestureTemplates.all.forEach { template ->
                val id = properties.getProperty("${template.id}.entry", "").take(256)
                bindings[template.id] = GestureBinding(id, id.isNotBlank() && properties.getProperty("${template.id}.enabled") == "true")
            }
        }
    }
    fun binding(id: String) = bindings[id] ?: GestureBinding()
    fun set(id: String, binding: GestureBinding) {
        require(GestureTemplates.all.any { it.id == id }); bindings[id] = binding; save()
    }
    fun active() = GestureTemplates.all.map { it.id }.filter { binding(it).enabled && binding(it).entryId.isNotBlank() }.toSet()
    fun hasBindings() = bindings.values.any { it.entryId.isNotBlank() }
    fun removeMissing(existing: Set<String>): Boolean {
        var changed = false
        GestureTemplates.all.forEach { template ->
            if (binding(template.id).entryId.let { it.isNotBlank() && it !in existing }) {
                bindings[template.id] = GestureBinding(); changed = true
            }
        }
        if (changed) save()
        return changed
    }
    fun save() {
        val properties = Properties().apply {
            setProperty("enabled", enabled.toString())
            GestureTemplates.all.forEach { template -> binding(template.id).let {
                setProperty("${template.id}.entry", it.entryId); setProperty("${template.id}.enabled", it.enabled.toString())
            } }
        }
        file.parentFile!!.mkdirs()
        val temporary = File.createTempFile("gestures-", ".tmp", file.parentFile)
        try {
            temporary.outputStream().use { properties.store(it, "KIGTTS quick text gesture bindings") }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
    }
}
