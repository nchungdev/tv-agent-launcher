package com.gathin.tvhome.core.util

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.os.Bundle
import android.view.View
import com.gathin.tvhome.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive

/**
 * Extension property gắn CoroutineScope vào vòng đời của Dialog (In-Window Sheet).
 * Tự động hủy toàn bộ coroutine khi Dialog dismiss và dọn tag để tránh scope chết.
 */
val Dialog.dialogScope: CoroutineScope
    get() {
        val decor = window?.decorView
        val existing = decor?.getTag(R.id.coroutine_scope_tag) as? CoroutineScope
        if (existing != null && existing.isActive) return existing

        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Main.immediate)
        decor?.setTag(R.id.coroutine_scope_tag, scope)
        decor?.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {}
            override fun onViewDetachedFromWindow(view: View) {
                scope.cancel()
                view.setTag(R.id.coroutine_scope_tag, null)
                view.removeOnAttachStateChangeListener(this)
            }
        })
        return scope
    }

/**
 * Extension property gắn CoroutineScope vào vòng đời của View.
 * Xử lý an toàn khi View bị attach/detach nhiều lần (ví dụ trong RecyclerView/ViewGroup):
 * - Hủy scope và xóa tag khi detach.
 * - Tự động remove listener để tránh rò rỉ listener.
 * - Khởi tạo scope mới khi re-attach và được gọi lại.
 */
val View.viewScope: CoroutineScope
    get() {
        val existing = getTag(R.id.coroutine_scope_tag) as? CoroutineScope
        if (existing != null && existing.isActive) return existing

        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Main.immediate)
        setTag(R.id.coroutine_scope_tag, scope)

        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                scope.cancel()
                v.setTag(R.id.coroutine_scope_tag, null)
                v.removeOnAttachStateChangeListener(this)
            }
        }
        addOnAttachStateChangeListener(listener)
        return scope
    }

/**
 * Extension property gắn CoroutineScope vào vòng đời của Activity.
 * Tự động hủy coroutine khi Activity onDestroy và dọn tag.
 */
val Activity.activityScope: CoroutineScope
    get() {
        val decor = window.decorView
        val existing = decor.getTag(R.id.coroutine_scope_tag) as? CoroutineScope
        if (existing != null && existing.isActive) return existing

        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Main.immediate)
        decor.setTag(R.id.coroutine_scope_tag, scope)

        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityDestroyed(target: Activity) {
                    if (target === this@activityScope) {
                        scope.cancel()
                        decor.setTag(R.id.coroutine_scope_tag, null)
                        application.unregisterActivityLifecycleCallbacks(this)
                    }
                }
                override fun onActivityCreated(target: Activity, state: Bundle?) {}
                override fun onActivityStarted(target: Activity) {}
                override fun onActivityResumed(target: Activity) {}
                override fun onActivityPaused(target: Activity) {}
                override fun onActivityStopped(target: Activity) {}
                override fun onActivitySaveInstanceState(
                    target: Activity,
                    state: Bundle
                ) {}
            }
        )
        return scope
    }
