package dev.vescmonitor.core.ride

/** Hard guard for ride logging: a full disk breaks database transactions. */
object StorageGuard {
    const val MIN_FREE_BYTES = 200L * 1024 * 1024

    /** True when the free space is below max(200 MB, twice the database). */
    fun low(
        freeBytes: Long,
        dbBytes: Long,
    ): Boolean = freeBytes < maxOf(MIN_FREE_BYTES, 2 * dbBytes)
}
