package dev.naved.j2kdesktop

import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser
import java.awt.Frame

/**
 * Real fullscreen on Windows, done the way browsers do it: take the title bar and borders out of
 * the window's style and make it cover its monitor exactly. Windows then hides the taskbar on its own.
 * Leaving fullscreen puts the old style, size and position (normal or maximized) back.
 */
internal object WindowsFullscreen {
    private const val GWL_STYLE = -16
    private const val WS_CAPTION = 0x00C00000
    private const val WS_THICKFRAME = 0x00040000
    private const val SWP_NOSIZE = 0x0001
    private const val SWP_NOMOVE = 0x0002
    private const val SWP_NOZORDER = 0x0004
    private const val SWP_FRAMECHANGED = 0x0020
    private const val SWP_NOOWNERZORDER = 0x0200
    private const val MONITOR_DEFAULTTONEAREST = 2

    private var savedStyle: Int? = null
    private var savedPlacement: WinUser.WINDOWPLACEMENT? = null

    fun enter(frame: Frame) {
        if (savedStyle != null) return
        val user32 = User32.INSTANCE
        val hwnd = hwndOf(frame)

        val placement = WinUser.WINDOWPLACEMENT()
        placement.length = placement.size()
        user32.GetWindowPlacement(hwnd, placement)
        val style = user32.GetWindowLong(hwnd, GWL_STYLE)

        val info = WinUser.MONITORINFO()
        info.cbSize = info.size()
        user32.GetMonitorInfo(user32.MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST), info)
        val screen = info.rcMonitor

        user32.SetWindowLong(hwnd, GWL_STYLE, style and (WS_CAPTION or WS_THICKFRAME).inv())
        // insertAfter = null is HWND_TOP
        user32.SetWindowPos(
            hwnd, null,
            screen.left, screen.top, screen.right - screen.left, screen.bottom - screen.top,
            SWP_NOOWNERZORDER or SWP_FRAMECHANGED,
        )
        savedStyle = style
        savedPlacement = placement
    }

    fun exit(frame: Frame) {
        val style = savedStyle ?: return
        val user32 = User32.INSTANCE
        val hwnd = hwndOf(frame)
        user32.SetWindowLong(hwnd, GWL_STYLE, style)
        savedPlacement?.let { user32.SetWindowPlacement(hwnd, it) }
        user32.SetWindowPos(
            hwnd, null, 0, 0, 0, 0,
            SWP_NOMOVE or SWP_NOSIZE or SWP_NOZORDER or SWP_NOOWNERZORDER or SWP_FRAMECHANGED,
        )
        savedStyle = null
        savedPlacement = null
    }

    private fun hwndOf(frame: Frame) = HWND(Native.getComponentPointer(frame))
}
