package com.andyer.n2lu

import android.app.Application
import com.andyer.n2lu.data.EventLog
import com.andyer.n2lu.service.Reposter

class N2LuApp : Application() {
    override fun onCreate() {
        super.onCreate()
        EventLog.load(this)
        Reposter.ensureChannel(this)
    }
}
