package com.example.towerandroid

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.IOException

/** 订阅内容仅在当前界面内存中处理；导出由用户选择目的地。 */
class MainActivity : Activity() {
    private lateinit var inputView: EditText
    private lateinit var statusView: TextView
    private lateinit var outputView: TextView
    private lateinit var exportButton: Button
    private var generatedText: String? = null
    private var parsed = false
    private var pendingExport: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null) // 不恢复可能包含敏感信息的视图状态。
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            isSaveEnabled = false
        }
        val heading = TextView(this).apply { text = "订阅内容管理"; textSize = 22f }
        val notice = TextView(this).apply {
            text = "实验版：仅接受手动粘贴的少量受支持节点链接或 Base64 列表，不下载订阅。导出仅包含 mihomo proxies 节点片段，并非完整配置；文件包含凭据，请妥善保管。"
        }
        inputView = EditText(this).apply {
            hint = "在此粘贴订阅内容"
            minLines = 5
            gravity = android.view.Gravity.TOP
            isSaveEnabled = false
            if (android.os.Build.VERSION.SDK_INT >= 26) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        statusView = TextView(this).apply { text = "状态：等待分析"; textSize = 16f }
        outputView = TextView(this).apply {
            text = "尚未生成文本"
            isSaveEnabled = false
            setTextIsSelectable(false)
        }
        val analyzeButton = Button(this).apply { text = "分析内容" }
        val generateButton = Button(this).apply { text = "生成文本" }
        exportButton = Button(this).apply { text = "导出文本"; isEnabled = false }
        val clearButton = Button(this).apply { text = "清空" }

        listOf(heading, notice, inputView, analyzeButton, statusView, generateButton,
            outputView, exportButton, clearButton).forEach { column.addView(it) }
        setContentView(ScrollView(this).apply { addView(column) })

        inputView.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                parsed = false
                nodes = emptyList()
                clearOutput()
                statusView.text = "状态：内容已更改，请重新分析"
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        analyzeButton.setOnClickListener {
            parsed = false
            clearOutput()
            val input = inputView.text.toString()
            if (input.isBlank()) {
                statusView.text = "状态：请先粘贴订阅内容"
            } else {
                try {
                    val summary = parseContent(input)
                    parsed = true
                    statusView.text = "状态：$summary"
                } catch (_: Exception) {
                    statusView.text = "状态：分析失败或核心功能尚未接入"
                }
            }
        }
        generateButton.setOnClickListener {
            clearOutput()
            if (!parsed) {
                statusView.text = "状态：请先成功分析当前内容"
            } else {
                try {
                    val result = generateConfig()
                    if (result.isBlank()) throw IllegalStateException("生成结果为空")
                    generatedText = result
                    outputView.text = "节点片段已生成（含敏感凭据，不在此显示）"
                    exportButton.isEnabled = true
                    statusView.text = "状态：文本已生成，尚未导出"
                } catch (_: Exception) {
                    statusView.text = "状态：生成失败或核心功能尚未接入"
                }
            }
        }
        exportButton.setOnClickListener {
            val text = generatedText ?: return@setOnClickListener
            pendingExport = text
            try {
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TITLE, "mihomo-proxies.yaml")
                }, REQUEST_EXPORT)
            } catch (_: Exception) {
                pendingExport = null
                statusView.text = "状态：无法打开系统文件选择器"
            }
        }
        clearButton.setOnClickListener {
            inputView.text.clear()
            parsed = false
            pendingExport = null
            clearOutput()
            statusView.text = "状态：已清空"
        }
    }

    private fun clearOutput() {
        generatedText = null
        pendingExport = null
        outputView.text = "尚未生成文本"
        exportButton.isEnabled = false
    }

    @Deprecated("使用平台 Activity 结果回调，避免增加外部依赖")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_EXPORT) return
        val text = pendingExport
        pendingExport = null
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null || text == null) {
            statusView.text = "状态：导出已取消或内容已失效"
            return
        }
        try {
            val stream = contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("无法打开目标文件")
            stream.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
            statusView.text = "状态：已导出到所选位置"
        } catch (_: Exception) {
            statusView.text = "状态：导出失败；目标文件可能不完整，请检查或删除"
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // 不保存订阅内容、解析结果或生成文本。
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        pendingExport = null
        generatedText = null
        super.onDestroy()
    }

    private var nodes: List<Core.Node> = emptyList()

    private fun parseContent(input: String): String {
        nodes = emptyList()
        nodes = Core.parseSubscription(input)
        return "已解析 ${nodes.size} 个节点"
    }

    private fun generateConfig(): String = Core.exportYaml(nodes)

    private companion object { const val REQUEST_EXPORT = 1001 }
}
