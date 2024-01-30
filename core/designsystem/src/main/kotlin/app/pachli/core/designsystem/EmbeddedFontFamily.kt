/*
 * Copyright 2023 Pachli Association
 *
 * This file is a part of Pachli.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Pachli is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Pachli; if not,
 * see <http://www.gnu.org/licenses>.
 */

package app.pachli.core.designsystem

import androidx.annotation.FontRes
import androidx.annotation.StyleRes

enum class EmbeddedFontFamily(@FontRes val font: Int, @StyleRes val style: Int) {
    DEFAULT(-1, -1),
    ATKINSON_HYPERLEGIBLE(R.font.atkinson_hyperlegible, R.style.FontAtkinsonHyperlegible),
    COMICNEUE(R.font.comicneue, R.style.FontComicNeue),
    ESTEDAD(R.font.estedad, R.style.FontEstedad),
    LEXEND(R.font.lexend, R.style.FontLexend),
    LUCIOLE(R.font.luciole, R.style.FontLuciole),
    OPENDYSLEXIC(R.font.opendyslexic, R.style.FontOpenDyslexic),
    ;

    companion object {
        fun from(s: String?): EmbeddedFontFamily {
            s ?: return DEFAULT

            return try {
                valueOf(s.uppercase())
            } catch (_: Throwable) {
                DEFAULT
            }
        }
    }
}
