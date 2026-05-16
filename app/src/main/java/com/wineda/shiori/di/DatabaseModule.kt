package com.wineda.shiori.di

import android.content.Context
import androidx.room.Room
import com.wineda.shiori.data.local.ShioriDatabase
import com.wineda.shiori.data.local.dao.JournalDao
import com.wineda.shiori.data.local.dao.MemoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ShioriDatabase = Room.databaseBuilder(
        context,
        ShioriDatabase::class.java,
        "shiori.db",
    ).fallbackToDestructiveMigration(true).build()

    @Provides fun provideJournalDao(database: ShioriDatabase): JournalDao = database.journalDao()
    @Provides fun provideMemoDao(database: ShioriDatabase): MemoDao = database.memoDao()
}
