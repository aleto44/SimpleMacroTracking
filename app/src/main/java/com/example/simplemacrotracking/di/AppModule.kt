package com.example.simplemacrotracking.di

import android.content.Context
import androidx.room.Room
import com.example.simplemacrotracking.data.db.AppDatabase
import com.example.simplemacrotracking.data.network.GeminiApi
import com.example.simplemacrotracking.data.network.OpenFoodFactsApi
import com.example.simplemacrotracking.data.network.CopilotApi
import com.example.simplemacrotracking.data.network.GitHubApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ── Database ──────────────────────────────────────────────────
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "macro_db")
            .fallbackToDestructiveMigration(dropAllTables = true)   // safe during pre-release; replace with proper migrations before v2
            .build()

    @Provides
    fun provideFoodItemDao(db: AppDatabase) = db.foodItemDao()

    @Provides
    fun provideDiaryEntryDao(db: AppDatabase) = db.diaryEntryDao()

    @Provides
    fun provideWeightEntryDao(db: AppDatabase) = db.weightEntryDao()

    // ── Moshi ─────────────────────────────────────────────────────
    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // ── OkHttp Clients ────────────────────────────────────────────
    @Provides
    @Singleton
    @Named("default")
    fun provideDefaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    @Named("gemini")
    fun provideGeminiOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)  // AI models can take time to respond
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // ── Network (two separate base URLs) ──────────────────────────
    @Provides
    @Singleton
    @Named("openfoodfacts")
    fun provideOpenFoodFactsRetrofit(moshi: Moshi, @Named("default") client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://world.openfoodfacts.org/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    @Provides
    @Singleton
    @Named("gemini")
    fun provideGeminiRetrofit(moshi: Moshi, @Named("gemini") client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    @Provides
    @Singleton
    fun provideOpenFoodFactsApi(@Named("openfoodfacts") r: Retrofit): OpenFoodFactsApi =
        r.create(OpenFoodFactsApi::class.java)

    @Provides
    @Singleton
    fun provideGeminiApi(@Named("gemini") r: Retrofit): GeminiApi =
        r.create(GeminiApi::class.java)

    @Provides
    @Singleton
    @Named("copilot")
    fun provideCopilotOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    @Named("copilot")
    fun provideCopilotRetrofit(moshi: Moshi, @Named("copilot") client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.githubcopilot.com/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    @Provides
    @Singleton
    fun provideCopilotApi(@Named("copilot") r: Retrofit): CopilotApi =
        r.create(CopilotApi::class.java)

    @Provides
    @Singleton
    @Named("github")
    fun provideGitHubOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Single GitHubApi handles both github.com (device-flow) and api.github.com.
     *  We use the api.github.com base and rely on the full URL for github.com calls. */
    @Provides
    @Singleton
    fun provideGitHubApi(@Named("github") client: OkHttpClient, moshi: Moshi): GitHubApi {
        return Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GitHubApi::class.java)
    }
}

