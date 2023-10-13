package app.pachli.util

import java.util.Date

/**
 * Compares this [Date] with [other], and returns true if they are equal to within the same
 * minute.
 *
 * "Same minute" means "they are within the same clock minute", not "they are within 60 seconds
 * of each other".
 */
fun Date.equalByMinute(other: Date): Boolean {
    return this.minutes == other.minutes &&
        this.hours == other.hours &&
        this.date == other.date &&
        this.month == other.month &&
        this.year == other.year
}
