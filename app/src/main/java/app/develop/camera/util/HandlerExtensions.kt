package app.develop.camera.util

import android.os.Handler
import java.util.concurrent.Executor

// 扩展函数：将 Handler 转换为 Executor
fun Handler.asExecutor(): Executor {
    return Executor { command -> this.post(command) }
}