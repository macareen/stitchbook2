package com.macareen.stitchbook2

import android.app.Application

class StitchbookApplication : Application() {
    val container: AppContainer by lazy {
        DefaultAppContainer(this)
    }
}
