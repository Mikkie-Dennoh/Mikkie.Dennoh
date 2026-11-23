package co.tawipay.topupdesk

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class UssdAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastPromptSignature: String? = null
    private var processing = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !AppPreferences(this).isRunActive() || !isTelephonyPackage(event.packageName?.toString().orEmpty())) return
        val root = rootInActiveWindow ?: return
        val signature = collectText(root).joinToString("|").take(700)
        if (signature.isBlank() || signature == lastPromptSignature || processing) return

        lastPromptSignature = signature
        processing = true
        handler.postDelayed({
            val currentRoot = rootInActiveWindow
            if (currentRoot == null) {
                processing = false
                return@postDelayed
            }
            submitNextReply(currentRoot)
            processing = false
        }, 350)
    }

    private fun submitNextReply(root: AccessibilityNodeInfo) {
        val preferences = AppPreferences(this)
        val input = findInput(root)
        val sendButton = findSendButton(root)
        if (input == null || sendButton == null) return

        val nextReply = preferences.takeNextReply() ?: return
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, nextReply)
        }
        val updated = input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        if (updated) {
            handler.postDelayed({
                if (!sendButton.isClickable || !sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    preferences.clearPendingRun()
                    Toast.makeText(this, "USSD reply could not be submitted. Run stopped.", Toast.LENGTH_LONG).show()
                }
            }, 250)
        } else {
            preferences.clearPendingRun()
            Toast.makeText(this, "USSD field is not supported on this phone. Run stopped.", Toast.LENGTH_LONG).show()
        }
    }

    private fun findInput(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.className?.toString()?.contains("EditText", ignoreCase = true) == true && node.isEnabled) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val found = findInput(child)
            if (found != null) return found
        }
        return null
    }

    private fun findSendButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val label = listOfNotNull(node.text?.toString(), node.contentDescription?.toString()).joinToString(" ")
        if (node.isClickable && label.matches(Regex("(?i).*(send|reply|ok|continue).*"))) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val found = findSendButton(child)
            if (found != null) return found
        }
        return null
    }

    private fun collectText(node: AccessibilityNodeInfo): List<String> = buildList {
        node.text?.toString()?.takeIf(String::isNotBlank)?.let(::add)
        node.contentDescription?.toString()?.takeIf(String::isNotBlank)?.let(::add)
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            addAll(collectText(child))
        }
    }

    private fun isTelephonyPackage(packageName: String): Boolean =
        packageName.contains("phone", ignoreCase = true) ||
            packageName.contains("dialer", ignoreCase = true) ||
            packageName.contains("telephony", ignoreCase = true)

    override fun onInterrupt() {
        AppPreferences(this).clearPendingRun()
        processing = false
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
