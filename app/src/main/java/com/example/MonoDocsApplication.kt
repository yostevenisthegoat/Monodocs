package com.example

import android.app.Application
import com.example.data.local.MonoDocsDatabase
import com.example.data.local.MonoDocsRepository
import com.example.data.local.PreferencesRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class AppContainer(application: Application) {
    private val database: MonoDocsDatabase = MonoDocsDatabase.getInstance(application)
    val repository: MonoDocsRepository = MonoDocsRepository(database.monoDocsDao())
    val preferencesRepository: PreferencesRepository = PreferencesRepository(application)
}

class MonoDocsApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        container = AppContainer(this)
    }
}
