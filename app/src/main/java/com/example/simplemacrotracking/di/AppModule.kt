package com.example.simplemacrotracking.di

import android.content.Context
import androidx.room.Room
import com.example.simplemacrotracking.data.db.AppDatabase
import com.example.simplemacrotracking.data.network.GeminiApi
import com.example.simplemacrotracking.data.network.OpenFoodFactsApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ── Database ──────────────────────────────────────────────────
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "macro_db").build()

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

    // ── Network (two separate base URLs) ──────────────────────────
    @Provides
    @Singleton
    @Named("openfoodfacts")
    fun provideOpenFoodFactsRetrofit(moshi: Moshi): Retrofit = Retrofit.Builder()
        .baseUrl("https://world.openfoodfacts.org/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    @Provides
    @Singleton
    @Named("gemini")
    fun provideGeminiRetrofit(moshi: Moshi): Retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
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
}

