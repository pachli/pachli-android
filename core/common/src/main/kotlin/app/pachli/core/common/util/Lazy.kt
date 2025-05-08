package app.pachli.core.common.util

/**
 * Version of [lazy] using [LazyThreadSafetyMode.NONE]. So this is only safe
 * to use if the initialization can never happen from multiple threads.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun <T : Any> unsafeLazy(noinline initializer: () -> T): Lazy<T> = lazy(LazyThreadSafetyMode.NONE, initializer)
