package com.example.yingshizhuizong.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.yingshizhuizong.data.dao.NoteDao
import com.example.yingshizhuizong.data.dao.WatchItemDao
import com.example.yingshizhuizong.data.entity.Note
import com.example.yingshizhuizong.data.entity.WatchItem

@Database(
    entities = [WatchItem::class, Note::class],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun watchItemDao(): WatchItemDao
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v1 → v2：新增预告片相关字段（已有数据保持不变） */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watch_items ADD COLUMN trailerKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE watch_items ADD COLUMN trailerName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE watch_items ADD COLUMN trailerUrl TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v2 → v3：新增 TMDB id，用来拉取简介与演职员 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watch_items ADD COLUMN tmdbId INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v3 → v4：新增「分类」字段，并删除不再使用的标签表。
         * 老数据统一落到「其他」，启动时会自动从 TMDB 补全分类。
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watch_items ADD COLUMN category TEXT NOT NULL DEFAULT 'OTHER'")
                db.execSQL("DROP TABLE IF EXISTS watch_item_tag_cross_ref")
                db.execSQL("DROP TABLE IF EXISTS tags")
            }
        }

        /**
         * v4 → v5：新增「看完时间」字段，用于观影统计与年度报告。
         * 旧的已看数据没有这个时间，先用 updatedAt 兜底，避免统计里丢数据。
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watch_items ADD COLUMN watchedAt INTEGER")
                db.execSQL(
                    "UPDATE watch_items SET watchedAt = updatedAt WHERE status = 'WATCHED'"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "yingshi_tracker.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
