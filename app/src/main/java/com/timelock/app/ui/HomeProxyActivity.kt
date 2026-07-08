package com.timelock.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * 透明代理 Activity — 从 Service 启动来绕过 MIUI 后台限制。
 * 仅做一件事：启动 Home 然后立即结束。
 */
class HomeProxyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(homeIntent)
        finish()
    }
}
