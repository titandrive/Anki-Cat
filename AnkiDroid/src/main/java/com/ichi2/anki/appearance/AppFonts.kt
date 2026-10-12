// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.appearance

import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.edit
import com.ichi2.anki.R
import com.ichi2.anki.common.preferences.sharedPrefs
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AppFonts {
    const val KEY = "ankicat_font"
    const val SIZE_KEY = "ankicat_font_size"
    const val CUSTOM_NAME = "ankicat_font_filename"
    const val CARD_FONT_PATH = "/_ankicat/font.ttf"
    private const val MAX_BYTES = 20 * 1024 * 1024
    private var cachedKey: String? = null
    private var cachedFont: Typeface? = null
    private val styledFonts = mutableMapOf<Int, Typeface>()

    fun selection(context: Context): String = context.sharedPrefs().getString(KEY, "default") ?: "default"

    private fun customFile(context: Context) = File(context.filesDir, "app-fonts/custom.ttf")

    fun sizeScale(context: Context): Float {
        val percent = context.sharedPrefs().getString(SIZE_KEY, "100")?.toIntOrNull() ?: 100
        return if (percent in listOf(70, 80, 90, 100, 110, 120, 130, 150)) percent / 100f else 1f
    }

    fun scaledContext(base: Context): Context {
        val scale = sizeScale(base)
        if (scale == 1f) return base
        val configuration = Configuration(base.resources.configuration)
        configuration.fontScale *= scale
        return base.createConfigurationContext(configuration)
    }

    fun signature(context: Context): String = "${selection(context)}:${customFile(context).lastModified()}:${sizeScale(context)}"

    @Synchronized
    private fun styledTypeface(
        font: Typeface,
        style: Int,
    ): Typeface = styledFonts.getOrPut(style) { Typeface.create(font, style) }

    @Synchronized
    fun typeface(context: Context): Typeface? {
        val selected = selection(context)
        val key = "$selected:${customFile(context).lastModified()}"
        if (key != cachedKey) {
            cachedFont =
                runCatching {
                    when (selected) {
                        "jetbrains" -> Typeface.createFromAsset(context.assets, "fonts/JetBrainsMono-Regular.ttf")
                        "custom" -> Typeface.createFromFile(customFile(context))
                        else -> null
                    }
                }.getOrNull()
            styledFonts.clear()
            cachedKey = key
        }
        return cachedFont
    }

    fun applyTo(view: View) {
        val font = typeface(view.context)

        fun visit(child: View) {
            if (child is TextView) {
                val original = child.getTag(R.id.ankicat_original_typeface) as? Typeface
                if (font != null) {
                    if (original == null) child.setTag(R.id.ankicat_original_typeface, child.typeface ?: Typeface.DEFAULT)
                    val style = child.typeface?.style ?: Typeface.NORMAL
                    val selected = styledTypeface(font, style)
                    if (child.typeface !== selected) child.typeface = selected
                } else if (original != null) {
                    child.typeface = original
                    child.setTag(R.id.ankicat_original_typeface, null)
                }
            }
            if (child is ViewGroup) for (index in 0 until child.childCount) visit(child.getChildAt(index))
        }
        visit(view)
    }

    fun importFont(
        context: Context,
        uri: Uri,
    ) {
        val name =
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: "custom.ttf"
        require(name.endsWith(".ttf", ignoreCase = true))
        val directory = File(context.filesDir, "app-fonts").apply { mkdirs() }
        val temporary = File.createTempFile("import-", ".ttf", directory)
        try {
            val bytes =
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= MAX_BYTES)
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } ?: error("Cannot read font")
            validateTrueType(bytes)
            temporary.writeBytes(bytes)
            Typeface.createFromFile(temporary)
            require(temporary.renameTo(customFile(context)))
            context.sharedPrefs().edit {
                putString(CUSTOM_NAME, name)
                putString(KEY, "custom")
            }
            cachedKey = null
        } finally {
            temporary.delete()
        }
    }

    internal fun validateTrueType(data: ByteArray) {
        require(data.size in 12..MAX_BYTES)
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        require(buffer.int == 0x00010000)
        val count = buffer.short.toInt() and 65535
        require(count in 1..256 && 12 + count * 16 <= data.size)
        val tables = mutableSetOf<String>()
        repeat(count) { index ->
            buffer.position(12 + index * 16)
            val tag = ByteArray(4).also { buffer.get(it) }.toString(Charsets.US_ASCII)
            buffer.int
            val offset = buffer.int.toLong() and 0xffffffffL
            val size = buffer.int.toLong() and 0xffffffffL
            require(offset >= 12 + count * 16 && offset + size <= data.size)
            tables += tag
        }
        require(tables.containsAll(listOf("cmap", "head", "hhea", "hmtx", "maxp", "name", "glyf", "loca")))
    }

    fun cardCss(context: Context): String =
        if (typeface(context) == null) {
            ""
        } else {
            "@font-face { font-family: AnkiCatCustom; src: url('$CARD_FONT_PATH?v=${signature(context)}'); }\n" +
                "body.card, body.card * { font-family: AnkiCatCustom !important; }\n"
        }

    fun openCardFont(context: Context): InputStream? =
        when (selection(context)) {
            "jetbrains" -> context.assets.open("fonts/JetBrainsMono-Regular.ttf")
            "custom" -> customFile(context).takeIf { it.isFile }?.inputStream()
            else -> null
        }
}
