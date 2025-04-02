/* Copyright 2019 Joel Pyska
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

package app.pachli.components.report

/**
 * The "Report account" screen to display.
 */
enum class Screen {
    /** Shows [ReportStatusesFragment][app.pachli.components.report.fragments.ReportStatusesFragment]. */
    Statuses,

    /** Shows [ReportNoteFragment][app.pachli.components.report.fragments.ReportNoteFragment]. */
    Note,

    /** Shows [ReportDoneFragment][app.pachli.components.report.fragments.ReportDoneFragment]. */
    Done,

    /** Signal to finish [ReportActivity][app.pachli.components.report.ReportActivity]. */
    Finish,
}
