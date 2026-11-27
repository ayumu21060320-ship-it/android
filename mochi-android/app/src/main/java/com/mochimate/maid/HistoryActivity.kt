package com.mochimate.maid

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** メイドさんが知らせた通知の履歴 */
class HistoryActivity : Activity() {

    private val d by lazy { resources.displayMetrics.density }
    private fun px(v: Int) = (v * d).toInt()
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(20), px(20), px(20))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(getColor(R.color.bg))
            fitsSystemWindows = true
            addView(root)
        })
        root.addView(TextView(this).apply {
            text = "通知の履歴"
            textSize = 20f
            setTextColor(getColor(R.color.ink))
            paint.isFakeBoldText = true
        })
        root.addView(TextView(this).apply {
            text = "メイドさんが知らせた通知です（最新100件）。メッセージ系のアプリは、アプリ名と時間だけ残します。"
            textSize = 13f
            setTextColor(getColor(R.color.ink_sub))
            setPadding(0, px(4), 0, px(10))
        })
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.card_bg)
            setPadding(px(14), px(8), px(14), px(8))
        }
        root.addView(list)
        root.addView(Button(this).apply {
            text = "履歴を消す"
            setTextColor(getColor(R.color.ink_sub))
            setBackgroundResource(R.drawable.button_sub_bg)
            setOnClickListener { NotifyHistory(this@HistoryActivity).clear(); render() }
        }, LinearLayout.LayoutParams(-1, px(44)).apply { topMargin = px(14) })
        render()
    }

    private fun render() {
        list.removeAllViews()
        val items = NotifyHistory(this).all()
        if (items.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "まだ何もありません"
                setTextColor(getColor(R.color.ink_sub))
                setPadding(0, px(8), 0, px(8))
            })
            return
        }
        val fmt = SimpleDateFormat("M/d H:mm", Locale.JAPAN)
        for (it in items) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, px(6), 0, px(6))
            }
            val icon = try { packageManager.getApplicationIcon(it.pkg) } catch (e: PackageManager.NameNotFoundException) { getDrawable(R.mipmap.ic_launcher) }
            row.addView(ImageView(this).apply { setImageDrawable(icon) }, LinearLayout.LayoutParams(px(32), px(32)))
            val mark = when (it.kind) { "pay" -> "💰 "; "alarm" -> "⏰ "; else -> "" }
            row.addView(TextView(this).apply {
                text = mark + it.label + (it.title?.let { t -> "\n$t" } ?: "")
                textSize = 14f
                setTextColor(getColor(R.color.ink))
                setPadding(px(10), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(TextView(this).apply {
                text = fmt.format(Date(it.time))
                textSize = 12f
                setTextColor(getColor(R.color.ink_sub))
            })
            list.addView(row)
        }
    }
}
