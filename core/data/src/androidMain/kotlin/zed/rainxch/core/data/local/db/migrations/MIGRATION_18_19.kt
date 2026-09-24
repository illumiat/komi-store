package zed.rainxch.core.data.local.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_18_19 =
    object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // The identity of the build a snapshot was taken from. `latestReleaseId` and
            // `latestAssetId` are the release and asset object ids, which is what tells one
            // build of a reused tag from the next; `latestAssetDigest` is the content
            // fallback for a host that supplies no ids. All three are null on existing rows,
            // which the check reads as "no identity recorded" and falls back to the publish
            // time it already stores.
            db.execSQL(
                """
                ALTER TABLE installed_apps
                ADD COLUMN latestReleaseId INTEGER DEFAULT NULL
                """.trimIndent(),
            )
            db.execSQL(
                """
                ALTER TABLE installed_apps
                ADD COLUMN latestAssetId INTEGER DEFAULT NULL
                """.trimIndent(),
            )
            db.execSQL(
                """
                ALTER TABLE installed_apps
                ADD COLUMN latestAssetDigest TEXT DEFAULT NULL
                """.trimIndent(),
            )
        }
    }
