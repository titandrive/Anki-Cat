/*
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.reviewer

class ReviewerCustomFonts(
    private val context: android.content.Context? = null,
) {
    /**
     * The CSS used to set the theme font.
     */
    private val customStyle =
        "BODY {font-family: 'OpenSans';font-weight: normal;font-style: normal;font-stretch: normal;}"

    val fontScript: String
        get() =
            if (context != null && com.ichi2.anki.appearance.AppFonts
                    .typeface(context) != null
            ) {
                "<script src=\"file:///android_asset/scripts/ankicat-font.js\"></script>"
            } else {
                ""
            }

    fun updateCssStyle(cssStyle: StringBuilder) {
        val fontCss = context?.let(com.ichi2.anki.appearance.AppFonts::cardCss).orEmpty()
        cssStyle.append(fontCss.ifEmpty { customStyle })
    }
}
