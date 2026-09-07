package com.wegood.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wegood.app.data.Prefs
import com.wegood.app.data.Repo

/** 兜底轮询：守护被关/App 被杀时，每 15 分钟补拉漏掉的互动并通知 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (Prefs.deviceId.isBlank()) return Result.success()
        return runCatching { Repo.refresh() }
            .fold({ Result.success() }, { Result.retry() })
    }
}
