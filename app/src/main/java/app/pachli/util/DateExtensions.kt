package app.pachli.util

import java.util.Calendar
import java.util.Date

/**
 * Compares this [Date] with [other], and returns true if they are equal to within the same
 * minute.
 *
 * "Same minute" means "they are within the same clock minute", not "they are within 60 seconds
 * of each other".
 */
fun Date.equalByMinute(other: Date): Boolean {
    val c1 = Calendar.getInstance()
    c1.setTime(this)
    c1.set(Calendar.SECOND, 0)
    c1.set(Calendar.MILLISECOND, 0)

    val c2 = Calendar.getInstance()
    c2.setTime(other)
    c2.set(Calendar.SECOND, 0)
    c2.set(Calendar.MILLISECOND, 0)

    return c1.timeInMillis == c2.timeInMillis
}
