package com.risenav.rokid.beegame

/** 子弹对象池（简单线程安全实现） */
class BulletPool(private val maxSize: Int) {
    private val pool = ArrayDeque<Bullet>()

    fun obtain(): Bullet? = synchronized(pool) {
        if (pool.isEmpty()) null else pool.removeFirst()
    }

    fun recycle(b: Bullet) = synchronized(pool) {
        // 重置状态并放回池（上层调用应先调用 deactivate()）
        if (pool.size < maxSize) pool.addLast(b)
    }
}