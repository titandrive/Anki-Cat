// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.appearance

import android.content.res.Configuration
import android.graphics.Typeface
import android.net.Uri
import android.widget.TextView
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.reviewer.ReviewerCustomFonts
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.nio.ByteBuffer
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AppFontsTest : RobolectricTest() {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `bundled font accepts valid TTF and rejects corrupt table bounds`() {
        val data = context.assets.open("fonts/JetBrainsMono-Regular.ttf").use { it.readBytes() }
        AppFonts.validateTrueType(data)
        ByteBuffer.wrap(data).putInt(20, Int.MAX_VALUE)
        assertFailsWith<IllegalArgumentException> { AppFonts.validateTrueType(data) }
        assertFailsWith<IllegalArgumentException> { AppFonts.validateTrueType(ByteArray(0)) }
    }

    @Test
    fun `returning to default restores original view font and style`() {
        val view = TextView(context)
        val original = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
        view.typeface = original
        context.sharedPrefs().edit { putString(AppFonts.KEY, "jetbrains") }
        AppFonts.applyTo(view)
        assertEquals(Typeface.BOLD_ITALIC, view.typeface.style)
        context.sharedPrefs().edit { putString(AppFonts.KEY, "default") }
        AppFonts.applyTo(view)
        assertEquals(original, view.typeface)
    }

    @Test
    fun `selected card font overrides template fonts and default selection removes custom CSS`() {
        context.sharedPrefs().edit { putString(AppFonts.KEY, "jetbrains") }
        val css = StringBuilder().also { ReviewerCustomFonts(context).updateCssStyle(it) }.toString()
        assertTrue(css.contains("body.card, body.card *"))
        assertTrue(css.contains(AppFonts.CARD_FONT_PATH))
        assertTrue(css.contains("font-family: AnkiCatCustom !important"))
        assertTrue(ReviewerCustomFonts(context).fontScript.contains("ankicat-font.js"))
        context.sharedPrefs().edit { putString(AppFonts.KEY, "default") }
        assertEquals("", AppFonts.cardCss(context))
        assertEquals("", ReviewerCustomFonts(context).fontScript)
    }

    @Test
    fun `preview renderer includes selected font override and removes it for default`() {
        context.sharedPrefs().edit { putString(AppFonts.KEY, "jetbrains") }
        val html =
            com.ichi2.anki.previewer
                .stdHtml(context)
        assertTrue(html.contains("font-family: AnkiCatCustom !important"))
        assertTrue(html.contains(AppFonts.CARD_FONT_PATH))
        assertTrue(html.contains("ankicat-font.js"))
        context.sharedPrefs().edit { putString(AppFonts.KEY, "default") }
        val defaultHtml =
            com.ichi2.anki.previewer
                .stdHtml(context)
        assertTrue(!defaultHtml.contains("AnkiCatCustom"))
        assertTrue(!defaultHtml.contains("ankicat-font.js"))
    }

    @Test
    fun `size multiplier preserves system text size and does not mutate base context`() {
        val base = context.createConfigurationContext(Configuration(context.resources.configuration).apply { fontScale = 1.25f })
        for (percent in listOf(70, 80, 90, 100, 110, 120, 130, 150)) {
            context.sharedPrefs().edit { putString(AppFonts.SIZE_KEY, percent.toString()) }
            val scaled = AppFonts.scaledContext(base)
            assertEquals(1.25f * percent / 100f, scaled.resources.configuration.fontScale, 0.001f)
            assertEquals(1.25f, base.resources.configuration.fontScale)
        }
        context.sharedPrefs().edit { putString(AppFonts.SIZE_KEY, "invalid") }
        assertEquals(1f, AppFonts.sizeScale(context))
    }

    @Test
    fun `activity receives the scaled text configuration`() {
        val originalScale = context.resources.configuration.fontScale
        context.sharedPrefs().edit { putString(AppFonts.SIZE_KEY, "80") }
        Robolectric.buildActivity(com.ichi2.anki.AnkiActivity::class.java).use { controller ->
            controller.setup()
            assertEquals(
                originalScale * 0.8f,
                controller
                    .get()
                    .resources.configuration.fontScale,
                0.001f,
            )
        }
    }

    @Test
    fun `card render scales text using the adjusted activity font scale`() {
        context.sharedPrefs().edit { putString(AppFonts.SIZE_KEY, "80") }
        val scaled = AppFonts.scaledContext(context)
        val appearance =
            com.ichi2.anki.cardviewer.CardAppearance.create(
                ReviewerCustomFonts(scaled),
                scaled.sharedPrefs(),
                scaled.resources.configuration.fontScale,
            )
        val css = appearance.style
        assertTrue(css.contains("html { zoom: ${scaled.resources.configuration.fontScale} }"))
    }

    @Test
    fun `font import survives source removal and invalid import preserves previous font`() {
        val source = File(context.cacheDir, "test.ttf")
        source.writeBytes(context.assets.open("fonts/JetBrainsMono-Regular.ttf").use { it.readBytes() })
        AppFonts.importFont(context, Uri.fromFile(source))
        source.delete()
        assertEquals("custom", AppFonts.selection(context))
        assertNotNull(AppFonts.openCardFont(context)?.use { it.readBytes() })
        val previous = AppFonts.openCardFont(context)!!.use { it.readBytes() }
        source.writeBytes(byteArrayOf(1, 2, 3))
        assertFailsWith<IllegalArgumentException> { AppFonts.importFont(context, Uri.fromFile(source)) }
        assertTrue(previous.contentEquals(AppFonts.openCardFont(context)!!.use { it.readBytes() }))
    }
}
