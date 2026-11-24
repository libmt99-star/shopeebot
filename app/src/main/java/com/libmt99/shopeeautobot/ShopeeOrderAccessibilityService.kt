package com.libmt99.shopeeautobot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ShopeeOrderAccessibilityService : AccessibilityService() {

    private var lastEventTimeMs: Long = 0L
    private val cooldownMs: Long = 100L
    private var stepCount: Int = 0
    private val maxStepsPerSession: Int = 10

    // true = dừng trước nút "Đặt hàng" để kiểm tra voucher & tổng tiền;
    // false = tự bấm "Đặt hàng" sau khi chọn voucher.
    private val pauseBeforePlaceOrder: Boolean = true

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: ""
        if (pkg.isNotEmpty() && pkg != "com.shopee.vn" && !pkg.contains("shopee")) {
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastEventTimeMs < cooldownMs) return

        val root = rootInActiveWindow ?: return

        if (hasSecurityPrompt(root)) {
            Log.w("ShopeeAutoBot", "Gặp xác minh/captcha -> Tạm dừng an toàn.")
            stepCount = maxStepsPerSession
            lastEventTimeMs = now
            return
        }

        if (stepCount >= maxStepsPerSession) return

        if (processVoucherAndOrder(root)) {
            stepCount++
            lastEventTimeMs = now
        }
    }

    private fun processVoucherAndOrder(root: AccessibilityNodeInfo): Boolean {
        val candidates = findVoucherCandidates(root)
        if (candidates.isNotEmpty()) {
            val best = candidates.maxByOrNull { it.score }
            if (best != null && triggerClick(best.targetNode)) {
                Log.i("ShopeeAutoBot", "Chọn voucher điểm ước tính: ${best.score}")
                return true
            }
        }

        if (isVoucherScreen(root)) {
            val confirmBtn = findClickableByExactOrPrefix(root, listOf("đồng ý", "áp dụng", "ok"))
            if (confirmBtn != null && triggerClick(confirmBtn)) {
                Log.i("ShopeeAutoBot", "Đã bấm xác nhận voucher.")
                return true
            }
        }

        val checkoutBtn = findClickableByExactOrPrefix(root, listOf("mua ngay", "thanh toán"))
        if (checkoutBtn != null && triggerClick(checkoutBtn)) {
            Log.i("ShopeeAutoBot", "Chuyển sang bước thanh toán.")
            return true
        }

        val orderBtn = findClickableByExactOrPrefix(root, listOf("đặt hàng"))
        if (orderBtn != null) {
            if (pauseBeforePlaceOrder) {
                Log.i("ShopeeAutoBot", "Đã tới nút Đặt hàng -> Dừng chờ bạn xác nhận.")
                stepCount = maxStepsPerSession
                return true
            } else if (triggerClick(orderBtn)) {
                Log.i("ShopeeAutoBot", "Đã bấm Đặt hàng.")
                stepCount = maxStepsPerSession
                return true
            }
        }

        return false
    }

    private data class VoucherCandidate(
        val targetNode: AccessibilityNodeInfo,
        val score: Long
    )

    private fun findVoucherCandidates(root: AccessibilityNodeInfo): List<VoucherCandidate> {
        val list = mutableListOf<VoucherCandidate>()

        fun scan(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.isEnabled) {
                val label = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
                    .joinToString(" ")
                    .trim()
                if (label.isNotEmpty()) {
                    val value = estimateVoucherValue(label)
                    if (value > 0L) {
                        val clickable = findClickableParent(node) ?: node
                        list.add(VoucherCandidate(clickable, value))
                    }
                }
            }
            for (i in 0 until node.childCount) {
                scan(node.getChild(i))
            }
        }

        scan(root)
        return list
    }

    private fun estimateVoucherValue(text: String): Long {
        val lower = text.lowercase()
        if (lower.contains("hết lượt") || lower.contains("không đủ điều kiện") || lower.contains("đã dùng")) {
            return 0L
        }

        val kRegex = Regex("""giảm\s*(\d+)\s*k\b""")
        kRegex.find(lower)?.let {
            return (it.groupValues[1].toLongOrNull() ?: 0L) * 1_000L
        }

        val vndRegex = Regex("""giảm\s*([\d.]+)\s*(đ|d|vnđ)?""")
        vndRegex.find(lower)?.let {
            val rawNum = it.groupValues[1].replace(".", "")
            return rawNum.toLongOrNull() ?: 0L
        }

        val pctRegex = Regex("""giảm\s*(\d+)\s*%""")
        pctRegex.find(lower)?.let {
            return (it.groupValues[1].toLongOrNull() ?: 0L) * 1_500L
        }

        if (lower.contains("miễn phí vận chuyển") || lower.contains("freeship")) {
            return 15_000L
        }

        return 0L
    }

    private fun isVoucherScreen(root: AccessibilityNodeInfo): Boolean {
        return containsAnyText(root, listOf("chọn shopee voucher", "chọn voucher", "mã giảm giá"))
    }

    private fun hasSecurityPrompt(root: AccessibilityNodeInfo): Boolean {
        return containsAnyText(root, listOf("xác minh", "captcha", "kiểm tra bảo mật"))
    }

    private fun containsAnyText(root: AccessibilityNodeInfo, keywords: List<String>): Boolean {
        fun walk(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            val txt = node.text?.toString()?.lowercase() ?: ""
            if (txt.isNotEmpty() && keywords.any { txt.contains(it) }) return true
            for (i in 0 until node.childCount) {
                if (walk(node.getChild(i))) return true
            }
            return false
        }
        return walk(root)
    }

    private fun findClickableByExactOrPrefix(root: AccessibilityNodeInfo, targets: List<String>): AccessibilityNodeInfo? {
        fun walk(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            val txt = node.text?.toString()?.trim()?.lowercase() ?: ""
            if (txt.isNotEmpty() && targets.any { txt == it || txt.startsWith(it) }) {
                return findClickableParent(node) ?: if (node.isClickable) node else null
            }
            for (i in 0 until node.childCount) {
                val res = walk(node.getChild(i))
                if (res != null) return res
            }
            return null
        }
        return walk(root)
    }

    private fun findClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var cur: AccessibilityNodeInfo? = node
        var hops = 0
        while (cur != null && hops < 5) {
            if (cur.isClickable && cur.isEnabled) return cur
            cur = cur.parent
            hops++
        }
        return null
    }

    private fun triggerClick(node: AccessibilityNodeInfo): Boolean {
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.width() > 0 && bounds.height() > 0) {
            val path = Path().apply {
                moveTo(bounds.exactCenterX(), bounds.exactCenterY())
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, 35L))
                .build()
            return dispatchGesture(gesture, null, null)
        }
        return false
    }

    override fun onInterrupt() {
        Log.w("ShopeeAutoBot", "Service bị ngắt.")
    }
}
