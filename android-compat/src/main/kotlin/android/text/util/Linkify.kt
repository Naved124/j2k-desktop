package android.text.util

import android.text.Spannable

object Linkify {
    const val WEB_URLS = 1
    const val EMAIL_ADDRESSES = 2
    const val PHONE_NUMBERS = 4
    const val ALL = 15

    @JvmStatic
    fun addLinks(text: Spannable, mask: Int): Boolean = false
}
