package com.personalkanban.app.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.personalkanban.app.data.local.CommentDao
import com.personalkanban.app.data.local.CustomerDao
import com.personalkanban.app.data.local.PersonalKanbanDatabase
import com.personalkanban.app.data.local.SyncConflictDao
import com.personalkanban.app.data.local.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(40, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PersonalKanbanDatabase =
        Room.databaseBuilder(
            context,
            PersonalKanbanDatabase::class.java,
            "personal_kanban.db"
        ).build()

    @Provides
    fun provideTaskDao(db: PersonalKanbanDatabase): TaskDao = db.taskDao()

    @Provides
    fun provideCommentDao(db: PersonalKanbanDatabase): CommentDao = db.commentDao()

    @Provides
    fun provideCustomerDao(db: PersonalKanbanDatabase): CustomerDao = db.customerDao()

    @Provides
    fun provideSyncConflictDao(db: PersonalKanbanDatabase): SyncConflictDao = db.syncConflictDao()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
