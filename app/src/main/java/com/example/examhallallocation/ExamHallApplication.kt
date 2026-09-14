package com.example.examhallallocation

import android.app.Application
import com.example.examhallallocation.data.seed.DatabaseSeeder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ExamHallApplication : Application() {

    @Inject lateinit var databaseSeeder: DatabaseSeeder

    override fun onCreate() {
        super.onCreate()
        databaseSeeder.seedIfEmpty()
    }
}
