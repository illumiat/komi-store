package zed.rainxch.core.data.local.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

// DEFAULT NULL is spelled out for the same reason as in MIGRATION_19_20: Room compares each
// column's default against the exported schema on open, so omitting it fails validation.
//
// Existing rows keep NULL, which is the correct reading of the old behaviour: before this
// migration every stored row was one that passed the installability probe, so none of them
// was excluded.
val MIGRATION_20_21 =
    object : Migration(20, 21) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "ALTER TABLE starred_repos ADD COLUMN exclusionReason TEXT DEFAULT NULL",
            )
        }
    }
