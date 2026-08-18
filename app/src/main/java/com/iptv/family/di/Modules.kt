package com.iptv.family.di

import android.content.Context
import com.iptv.family.data.local.AppDatabase
import com.iptv.family.data.m3u.M3UParser
import com.iptv.family.data.repository.PlaylistRepository
import com.iptv.family.data.xtream.XtreamApiClient
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.Singleton
import dagger.Module
import dagger.Provides
import javax.inject.Inject

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ParserModule {
    @Provides
    @Singleton
    fun provideM3UParser(): M3UParser {
        return M3UParser()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideXtreamApiClientFactory(): XtreamApiClientFactory {
        return XtreamApiClientFactory()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun providePlaylistRepository(
        database: AppDatabase,
        m3uParser: M3UParser,
        xtreamApiFactory: XtreamApiClientFactory,
    ): PlaylistRepository {
        return PlaylistRepository(database, m3uParser, xtreamApiFactory)
    }
}