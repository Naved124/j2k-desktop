package android.content.res

import android.util.DisplayMetrics

class Resources private constructor() {
    private val metrics by lazy { DisplayMetrics.fromScreen() }

    fun getDisplayMetrics(): DisplayMetrics = metrics

    companion object {
        private val systemInstance by lazy { Resources() }

        @JvmStatic
        fun getSystem(): Resources = systemInstance
    }
}
