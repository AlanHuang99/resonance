package com.resonance.music.di

import android.content.Context
import androidx.room.Room
import com.resonance.music.data.api.SubsonicApi
import com.resonance.music.data.api.SubsonicApiHelper
import com.resonance.music.data.api.SubsonicAuthInterceptor
import com.resonance.music.data.db.ResonanceDatabase
import com.resonance.music.data.db.dao.AlbumDao
import com.resonance.music.data.db.dao.ArtistDao
import com.resonance.music.data.db.dao.SongDao
import com.resonance.music.data.repository.AuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(authRepository: AuthRepository): OkHttpClient {
        val authInterceptor = SubsonicAuthInterceptor {
            runBlocking { authRepository.getCredentials() }
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideSubsonicApi(okHttpClient: OkHttpClient, authRepository: AuthRepository): SubsonicApi {
        // Use a placeholder base URL; the actual URL comes from credentials
        // We'll need a dynamic base URL interceptor
        val baseUrl = runBlocking {
            authRepository.getCredentials()?.serverUrl?.trimEnd('/') ?: "http://localhost"
        }

        return Retrofit.Builder()
            .baseUrl("$baseUrl/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SubsonicApi::class.java)
    }

    @Provides
    @Singleton
    fun provideSubsonicApiHelper(authRepository: AuthRepository): SubsonicApiHelper {
        return SubsonicApiHelper {
            runBlocking { authRepository.getCredentials() }
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ResonanceDatabase {
        return Room.databaseBuilder(
            context,
            ResonanceDatabase::class.java,
            "resonance_db"
        ).build()
    }

    @Provides
    fun provideSongDao(db: ResonanceDatabase): SongDao = db.songDao()

    @Provides
    fun provideAlbumDao(db: ResonanceDatabase): AlbumDao = db.albumDao()

    @Provides
    fun provideArtistDao(db: ResonanceDatabase): ArtistDao = db.artistDao()
}
