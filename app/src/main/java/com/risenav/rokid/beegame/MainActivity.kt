package com.risenav.rokid.beegame

import android.app.Activity
import android.os.Bundle
import android.view.Window
import android.view.WindowManager

/**
 * 主活动类，负责游戏窗口的初始化与显示
 */
class MainActivity : Activity() {

    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 取消标题栏
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        // 设置全屏显示
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

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