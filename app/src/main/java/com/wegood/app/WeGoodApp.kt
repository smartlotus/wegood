package com.wegood.app

import android.app.Application
import com.wegood.app.data.Prefs
import com.wegood.app.data.Repo
import com.wegood.app.notify.Notifications

class WeGoodApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Notifications.createChannels(this)
        Repo.init(this)
    }
}
