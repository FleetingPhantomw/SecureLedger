package com.secureledger.integration.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

/** Bounded traversal; input fields and password nodes are never copied into OCR rules. */
object AccessibilityTextExtractor {
    fun extract(root: AccessibilityNodeInfo): List<String> {
        val lines = ArrayList<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        var characters = 0
        while (queue.isNotEmpty() && visited++ < 120 && characters < 4_000) {
            val node = queue.removeFirst()
            if (node.isPassword || node.className?.contains("EditText") == true) continue
            listOfNotNull(node.text, node.contentDescription).forEach { raw ->
                val line = raw.toString().replace(Regex("\\s+"), " ").trim().take(160)
                if (line.isNotEmpty() && characters + line.length <= 4_000) {
                    lines.add(line)
                    characters += line.length
                }
            }
            for (index in 0 until node.childCount) node.getChild(index)?.let(queue::addLast)
        }
        return lines.distinct()
    }
}
