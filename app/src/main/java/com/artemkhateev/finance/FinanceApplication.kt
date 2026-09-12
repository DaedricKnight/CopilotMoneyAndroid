package com.artemkhateev.finance

import android.app.Application
import com.artemkhateev.finance.data.AppGraph

class FinanceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}
