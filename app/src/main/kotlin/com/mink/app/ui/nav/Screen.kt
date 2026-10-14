package com.mink.app.ui.nav

import android.content.Context
import android.view.View

interface Screen {
    fun createView(ctx: Context): View
    fun onShow() {}
    fun onHide() {}
    fun onBack(): Boolean = false // return true if consumed
}
