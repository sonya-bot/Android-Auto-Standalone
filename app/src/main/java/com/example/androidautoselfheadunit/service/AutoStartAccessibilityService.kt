package com.example.androidautoselfheadunit.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.androidautoselfheadunit.ui.MainActivity
import java.util.concurrent.atomic.AtomicBoolean

class AutoStartAccessibilityService : AccessibilityService() {

    enum class Mode {
        IDLE,
        START_SERVER,
        STOP_SERVER,
    }

    companion object {
        private const val TAG = "AutoStartA11yService"
        private const val GEARHEAD_PACKAGE = "com.google.android.projection.gearhead"
        private val currentMode = java.util.concurrent.atomic.AtomicReference(Mode.IDLE)
        private var onStopCallback: (() -> Unit)? = null
        private var instance: AutoStartAccessibilityService? = null

        fun armAutoStart() {
            Log.i(TAG, "armAutoStart requested")
            currentMode.set(Mode.START_SERVER)
        }

        fun armAutoStop(onComplete: (() -> Unit)? = null) {
            Log.i(TAG, "armAutoStop requested")
            onStopCallback = onComplete
            currentMode.set(Mode.STOP_SERVER)
        }

        fun hasInstance(): Boolean = instance != null

        fun stopServerAutomatically(onComplete: (() -> Unit)? = null) {
            val service = instance
            if (service != null) {
                Log.i(TAG, "stopServerAutomatically initiated from service instance")
                armAutoStop(onComplete)
                service.scheduleStopTimeout(4000L)
                try {
                    val intent = Intent("com.google.android.projection.gearhead.SETTINGS").apply {
                        setPackage("com.google.android.projection.gearhead")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    service.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to launch gearhead settings from accessibility service", e)
                    service.cancelStopTimeout()
                    disarmAutoStart()
                    onComplete?.invoke()
                }
            } else {
                Log.w(TAG, "Accessibility service instance not available to stop server automatically")
                onComplete?.invoke()
            }
        }

        fun disarmAutoStart() {
            instance?.cancelStopTimeout()
            currentMode.set(Mode.IDLE)
            onStopCallback = null
        }

        fun isServiceEnabled(context: Context): Boolean {
            if (instance != null) return true

            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
            val enabledServices = am?.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            if (enabledServices != null) {
                for (service in enabledServices) {
                    val serviceInfo = service.resolveInfo.serviceInfo
                    if (serviceInfo.packageName == context.packageName &&
                        serviceInfo.name == AutoStartAccessibilityService::class.java.name
                    ) {
                        return true
                    }
                }
            }

            val expectedServiceName = "${context.packageName}/${AutoStartAccessibilityService::class.java.name}"
            val enabledServicesSetting = try {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                )
            } catch (_: Exception) {
                null
            } ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServicesSetting)
            while (colonSplitter.hasNext()) {
                val componentNameString = colonSplitter.next().trim()
                if (componentNameString.equals(expectedServiceName, ignoreCase = true) ||
                    componentNameString.equals("${context.packageName}/.service.AutoStartAccessibilityService", ignoreCase = true)
                ) {
                    return true
                }
            }
            return false
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var menuOpened = false
    private var stopTimeoutRunnable: Runnable? = null

    private fun scheduleStopTimeout(delayMs: Long) {
        cancelStopTimeout()
        val r = Runnable {
            Log.w(TAG, "Stop server operation timed out after ${delayMs}ms. Invoking callback.")
            val cb = onStopCallback
            disarmAutoStart()
            cb?.invoke()
        }
        stopTimeoutRunnable = r
        mainHandler.postDelayed(r, delayMs)
    }

    private fun cancelStopTimeout() {
        stopTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        stopTimeoutRunnable = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "AutoStartAccessibilityService connected")
    }

    override fun onDestroy() {
        cancelStopTimeout()
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val mode = currentMode.get()
        if (mode == Mode.IDLE || event == null) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName != GEARHEAD_PACKAGE) return

        val rootNode = rootInActiveWindow ?: return
        try {
            when (mode) {
                Mode.START_SERVER -> processGearheadStartWindow(rootNode)
                Mode.STOP_SERVER -> processGearheadStopWindow(rootNode)
                Mode.IDLE -> Unit
            }
        } finally {
            rootNode.recycle()
        }
    }

    private fun processGearheadStartWindow(root: AccessibilityNodeInfo) {
        // Step 1: Check if the server is already running
        val stopItem = findStopServerItem(root)
        if (stopItem != null) {
            Log.i(TAG, "Head Unit Server is already running, returning to MainActivity")
            stopItem.recycle()
            onServerStartedSuccess()
            return
        }

        // Step 2: Look for the menu item to start head unit server
        val startItem = findStartServerItem(root)
        if (startItem != null) {
            Log.i(TAG, "Found start server item, clicking it!")
            val clicked = performClick(startItem)
            startItem.recycle()
            if (clicked) {
                onServerStartedSuccess()
            }
            return
        }

        // Step 3: If menu item is not visible yet, look for the overflow menu button (More options / その他のオプション)
        val overflowButton = findOverflowButton(root)
        if (overflowButton != null) {
            Log.i(TAG, "Found overflow button, clicking to open menu")
            menuOpened = true
            performClick(overflowButton)
            overflowButton.recycle()
        }
    }

    private fun processGearheadStopWindow(root: AccessibilityNodeInfo) {
        // Step 1: Look for the menu item to stop head unit server
        val stopItem = findStopServerItem(root)
        if (stopItem != null) {
            Log.i(TAG, "Found stop server item, clicking it!")
            val clicked = performClick(stopItem)
            stopItem.recycle()
            if (clicked) {
                onServerStoppedSuccess()
            }
            return
        }

        // Step 2: Check if server is already stopped
        val startItem = findStartServerItem(root)
        if (startItem != null) {
            Log.i(TAG, "Head Unit Server is already stopped, returning to Home")
            startItem.recycle()
            onServerStoppedSuccess()
            return
        }

        // Step 3: Look for the overflow menu button
        val overflowButton = findOverflowButton(root)
        if (overflowButton != null) {
            Log.i(TAG, "Found overflow button, clicking to open menu")
            menuOpened = true
            performClick(overflowButton)
            overflowButton.recycle()
        }
    }

    private fun findStopServerItem(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val targets = listOf(
            "ヘッドユニットサーバーを停止",
            "ヘッドユニット サーバーを停止",
            "ヘッドユニットサーバーを終了",
            "ヘッドユニット サーバーを終了",
            "Stop head unit server"
        )
        for (target in targets) {
            val list = root.findAccessibilityNodeInfosByText(target)
            if (!list.isNullOrEmpty()) {
                return list[0]
            }
        }
        return null
    }

    private fun findStartServerItem(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val targets = listOf(
            "ヘッドユニットサーバーを起動",
            "ヘッドユニット サーバーを起動",
            "ヘッドユニットサーバーを開始",
            "ヘッドユニット サーバーを開始",
            "Start head unit server"
        )
        for (target in targets) {
            val list = root.findAccessibilityNodeInfosByText(target)
            if (!list.isNullOrEmpty()) {
                return list[0]
            }
        }
        return null
    }

    private fun findOverflowButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val descriptions = listOf(
            "その他のオプション",
            "その他のオプションを表示",
            "More options"
        )
        for (desc in descriptions) {
            val list = root.findAccessibilityNodeInfosByText(desc)
            if (!list.isNullOrEmpty()) {
                return list[0]
            }
        }

        // Search recursively for node with contentDescription
        return searchNodeByDescription(root, descriptions)
    }

    private fun searchNodeByDescription(node: AccessibilityNodeInfo, descs: List<String>): AccessibilityNodeInfo? {
        val nodeDesc = node.contentDescription?.toString()
        if (nodeDesc != null && descs.any { it.equals(nodeDesc, ignoreCase = true) }) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = searchNodeByDescription(child, descs)
            if (found != null) {
                return found
            }
            child.recycle()
        }
        return null
    }

    private fun performClick(target: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = target
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            val parent = current.parent
            if (current != target) {
                current.recycle()
            }
            current = parent
        }
        return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun onServerStartedSuccess() {
        Log.i(TAG, "Head Unit Server started successfully, returning to MainActivity")
        currentMode.set(Mode.IDLE)
        menuOpened = false

        mainHandler.postDelayed({
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
        }, 1000)
    }

    private fun onServerStoppedSuccess() {
        Log.i(TAG, "Head Unit Server stopped successfully, returning to Home")
        cancelStopTimeout()
        currentMode.set(Mode.IDLE)
        menuOpened = false

        mainHandler.postDelayed({
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(homeIntent)

            val cb = onStopCallback
            onStopCallback = null
            cb?.invoke()
        }, 500)
    }

    override fun onInterrupt() {
        Log.w(TAG, "AutoStartAccessibilityService interrupted")
    }
}
