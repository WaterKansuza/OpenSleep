package org.opensleep.app.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.opensleep.app.data.local.db.AppDatabase
import org.opensleep.app.data.local.db.NoteImpactType
import org.opensleep.app.data.local.db.dao.AlarmDao
import org.opensleep.app.data.local.db.dao.SleepDebtDao
import org.opensleep.app.data.local.db.dao.SleepSessionDao
import org.opensleep.app.data.local.db.entity.SleepNoteEntity
import org.opensleep.app.data.local.db.entity.UserSleepProfileEntity
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        dbProvider: Provider<AppDatabase>
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "opensleep_local.db"
        )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    CoroutineScope(Dispatchers.IO).launch {
                        val database = dbProvider.get()
                        database.sleepDebtDao().upsertProfile(UserSleepProfileEntity())
                        database.sleepSessionDao().insertNotes(defaultSleepNotes())
                    }
                }
            })
            .build()
    }

    @Provides fun provideSleepSessionDao(db: AppDatabase): SleepSessionDao = db.sleepSessionDao()
    @Provides fun provideSleepDebtDao(db: AppDatabase): SleepDebtDao = db.sleepDebtDao()
    @Provides fun provideAlarmDao(db: AppDatabase): AlarmDao = db.alarmDao()

    private fun defaultSleepNotes(): List<SleepNoteEntity> = listOf(
        SleepNoteEntity(name = "Cà phê muộn", iconKey = "coffee", impactType = NoteImpactType.NEGATIVE, isDefault = true, displayOrder = 1),
        SleepNoteEntity(name = "Rượu bia", iconKey = "alcohol", impactType = NoteImpactType.NEGATIVE, isDefault = true, displayOrder = 2),
        SleepNoteEntity(name = "Căng thẳng", iconKey = "stress", impactType = NoteImpactType.NEGATIVE, isDefault = true, displayOrder = 3),
        SleepNoteEntity(name = "Tập thể dục", iconKey = "workout", impactType = NoteImpactType.POSITIVE, isDefault = true, displayOrder = 4),
        SleepNoteEntity(name = "Đọc sách", iconKey = "book", impactType = NoteImpactType.POSITIVE, isDefault = true, displayOrder = 5),
        SleepNoteEntity(name = "Ăn khuya", iconKey = "late_meal", impactType = NoteImpactType.NEGATIVE, isDefault = true, displayOrder = 6)
    )
}
