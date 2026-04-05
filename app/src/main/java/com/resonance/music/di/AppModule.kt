package com.resonance.music.di

import android.content.pm.ApplicationInfo
import android.content.Context
import androidx.room.Room
import com.resonance.music.data.api.DynamicBaseUrlInterceptor
import com.resonance.music.data.api.SubsonicApi
import com.resonance.music.data.api.SubsonicApiHelper
import com.resonance.music.data.api.ServerCredentials
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
import com.google.gson.GsonBuilder
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        authRepository: AuthRepository
    ): OkHttpClient {
        // Cache credentials to avoid repeated blocking calls. The interceptors run on
        // OkHttp's I/O threads where a brief runBlocking is acceptable and unavoidable
        // (interceptors are synchronous). We cache after the first fetch so subsequent
        // requests never block.
        var cached: ServerCredentials? = null
        val credentialsProvider = {
            cached ?: runBlocking { authRepository.getCredentials() }.also { cached = it }
        }

        val authInterceptor = SubsonicAuthInterceptor(credentialsProvider)
        val dynamicBaseUrlInterceptor = DynamicBaseUrlInterceptor(credentialsProvider)

        val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (isDebuggable) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(dynamicBaseUrlInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideSubsonicApi(okHttpClient: OkHttpClient): SubsonicApi {
        val gson = GsonBuilder()
            .setLenient()
            .create()

        return Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(SubsonicApi::class.java)
    }

    @Provides
    @Singleton
    fun provideSubsonicApiHelper(authRepository: AuthRepository): SubsonicApiHelper {
        var cached: ServerCredentials? = null
        return SubsonicApiHelper {
            cached ?: runBlocking { authRepository.getCredentials() }.also { cached = it }
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
