package com.risenav.rokid.beegame

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 主活动类，负责游戏窗口的初始化与显示
 */
class MainActivity : AppCompatActivity() {

    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置全屏显示
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // 设置保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 初始化游戏视图
        gameView = GameView(this)
        setContentView(gameView)

        // 设置窗口引用
        gameView.setWindow(window)
    }

    override fun onPause() {
        super.onPause()
        gameView.pause() // 暂停游戏
    }

    override fun onResume() {
        super.onResume()
        gameView.resume() // 恢复游戏
    }

    override fun onDestroy() {
        super.onDestroy()
        gameView.release() // 释放资源
    }
}
