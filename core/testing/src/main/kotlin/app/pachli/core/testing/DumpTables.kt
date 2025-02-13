/*
 * Copyright (c) 2025 Pachli Association
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

package app.pachli.core.testing

import androidx.core.database.getStringOrNull
import app.pachli.core.database.AppDatabase

val SCHEMA_QUERY = """
SELECT
    name
FROM
    sqlite_master
WHERE
    type ='table' AND
    name NOT LIKE 'sqlite_%';
""".trimIndent()

/**
 * Dumps all table contents to STDOUT.
 *
 * Because tests use an in-memory database it's not possible to use the database
 * inspector when debugging a test to verify the contents match expectations.
 *
 * This is especially problematic when debugging foreign key constraint failures,
 * as the SQLite error does not explain which FK constraint was violated.
 *
 * `dumpTables` prints the current content of all tables to STDOUT. This allows
 * you to run the problematic operation in a transaction and see the table contents
 * after the operation but before the transaction completes.
 *
 * ```kotlin
 * db.withTransaction {
 *     println("Before operation")
 *     dumpTables(db)
 *
 *     someOperationThatCausesConstraintViolation()
 *
 *     println("After operation")
 *     dumpTables(db)
 * }
 * ```
 *
 * As long as all FK constraints are marked as deferrable this will print the
 * table contents after the operation but before the transaction finishes and the
 * constraint exception is raised.
 */
fun dumpTables(db: AppDatabase) {
    db.query(SCHEMA_QUERY, null).use { sc ->
        while (sc.moveToNext()) {
            val tableName = sc.getStringOrNull(0)

            val tableCursor = db.query("SELECT * FROM $tableName", null)
            if (tableCursor.count < 1) continue

            println("Table: $tableName")

            tableCursor.use { tc ->
                println(tc.columnNames.joinToString(","))

                while (tc.moveToNext()) {
                    val s = buildList {
                        (0..tc.columnCount - 1).forEach { add(tc.getStringOrNull(it)) }
                    }
                    println(s.joinToString(","))
                }
            }
        }
    }
}
