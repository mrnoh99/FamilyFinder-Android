package com.familyfinder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FamilyMember::class], version = 1, exportSchema = false)
abstract class FamilyDatabase : RoomDatabase() {
    abstract fun familyDao(): FamilyDao

    companion object {
        @Volatile
        private var INSTANCE: FamilyDatabase? = null

        fun getDatabase(context: Context): FamilyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FamilyDatabase::class.java,
                    "family_database"
                )
                    // 스키마(version) 변경 시 마이그레이션이 없으면 앱이 크래시하므로,
                    // 별도 Migration을 추가하기 전까지는 기존 DB를 재생성한다.
                    // 가족 데이터는 등록 화면에서 다시 만들 수 있어 파괴적 마이그레이션이 무난하다.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
