package ru.krestnikov.pushfilter

import android.app.Application

class PushFilterApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
