package app.pachli.components.timeline.util

import java.io.IOException
import retrofit2.HttpException

fun Throwable.isExpected() = this is IOException || this is HttpException

inline fun <T> ifExpected(
    t: Throwable,
    cb: () -> T,
): T {
    if (t.isExpected()) {
        return cb()
    } else {
        throw t
    }
}
