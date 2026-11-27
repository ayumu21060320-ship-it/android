package com.mochimate.maid

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** ロック画面のメイドさんが出るまでに何が起きたかの記録(うまく出ない時の原因さがし用) */
object LockDiag {
    private const val KEY = "lock_diag"

    fun log(context: Context, msg: String) {
        val sp = context.getSharedPreferences("mochi_diag", Context.MODE_PRIVATE)
        val time = SimpleDateFormat("H:mm:ss", Locale.JAPAN).format(Date())
        val lines = (sp.getString(KEY, "")!!.lines().filter { it.isNotBlank() } + "$time $msg").takeLast(20)
        sp.edit().putString(KEY, lines.joinToString("\n")).apply()
    }

    fun read(context: Context): String =
        context.getSharedPreferences("mochi_diag", Context.MODE_PRIVATE).getString(KEY, "")!!.ifBlank { "まだ記録がありません" }
}
