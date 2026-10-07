package dev.vescmonitor.vesc.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Ride storage, owned by the native side only. WAL journal. Schema changes need a
 * migration and a test; destructive migration is never allowed (it would lose rides).
 */
@Database(entities = [CaptureEntity::class, ChunkEntity::class, RideEntity::class, RunEntity::class], version = 2, exportSchema = true)
abstract class VescDatabase : RoomDatabase() {
    abstract fun rides(): RideDao

    companion object {
        const val NAME = "vesc-monitor.db"

        fun open(context: Context): VescDatabase =
            Room
                .databaseBuilder(context, VescDatabase::class.java, NAME)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_1_2)
                .build()

        /** Adds speed-test runs. The SQL matches what Room generates for [RunEntity]. */
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `run` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`start_wall_ms` INTEGER NOT NULL, `json` TEXT NOT NULL)",
                    )
                }
            }
    }
}
