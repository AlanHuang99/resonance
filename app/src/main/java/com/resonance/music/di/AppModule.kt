package com.resonance.music.di

import android.content.pm.ApplicationInfo
import android.content.Context
import com.resonance.music.data.api.DynamicBaseUrlInterceptor
import com.resonance.music.data.api.SubsonicApi
import com.resonance.music.data.api.SubsonicApiHelper
import com.resonance.music.data.api.SubsonicAuthInterceptor
import com.resonance.music.data.repository.AuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
        // Read credentials from AuthRepository's synchronous cache, which is kept
        // fresh on login/logout — so changing server or re-logging-in takes effect
        // without an app restart.
        val credentialsProvider = authRepository::getCachedCredentials

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
        return SubsonicApiHelper(authRepository::getCachedCredentials)
    }
}
