package com.v2ray.ang.receiver

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.shortcut.ScSwitchActivity

/** The launcher owns permission UI; the daemon supplies state through the switch ViewModel. */
class WidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_switch)
        val click = PendingIntent.getActivity(context, R.id.layout_switch,
            Intent(context, ScSwitchActivity::class.java).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        views.setOnClickPendingIntent(R.id.layout_switch, click)
        views.setImageViewResource(R.id.image_switch, R.drawable.ic_widget_transparent)
        views.setInt(R.id.layout_background, "setBackgroundResource", android.R.color.transparent)
        views.setContentDescription(R.id.layout_switch, context.getString(R.string.acc_toggle_vpn))
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppConfig.BROADCAST_ACTION_WIDGET_CLICK) {
            context.startActivity(Intent(context, ScSwitchActivity::class.java)
                .setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else if (intent.action == AppConfig.BROADCAST_ACTION_ACTIVITY) {
            val manager = AppWidgetManager.getInstance(context)
            onUpdate(context, manager, manager.getAppWidgetIds(ComponentName(context, WidgetProvider::class.java)))
        }
    }
}
