package com.mochimate.maid

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ThinkingConfigDisabled
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * リリの「頭」。Claude(Haiku)に、リリの性格・ご主人様ノート・今日の様子・最近の会話を渡して返事を作ってもらう。
 * 会話の記録とノートはスマホの中に保存し、返事を作る時に必要な分だけ送る。
 */
object LiliBrain {

    const val MODEL = "claude-haiku-5-5"

    private val io = Executors.newSingleThreadExecutor()
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private var client: AnthropicClient? = null
    private var clientKey: String? = null

    private fun client(key: String): AnthropicClient {
        if (client == null || clientKey != key) {
            client = AnthropicOkHttpClient.builder()
                .apiKey(key)
                .timeout(Duration.ofSeconds(40))
                .maxRetries(2)
                .build()
            clientKey = key
        }
        return client!!
    }

    /** ご主人様のメッセージに返事をする。返事は1〜3個の短いメッセージ(行ごと)に分かれて返る */
    fun reply(context: Context, userText: String, callback: (List<String>) -> Unit) {
        val prefs = Prefs(context)
        val store = ChatStore(context)
        val key = prefs.anthropicKey
        store.add(ChatMessage(true, userText, System.currentTimeMillis()))
        if (key.isNullOrBlank()) {
            finish(store, listOf("あっ…わたし、まだお話しする準備ができてないみたいです。", "設定の「リリと話す」で API キーを入れてもらえますか？"), callback)
            return
        }
        if (store.todayCount() > ChatStore.DAILY_LIMIT) {
            finish(store, listOf("えへへ、今日はいっぱいお話ししてちょっと疲れちゃいました…", "また明日たくさんお話ししましょうね♪"), callback)
            return
        }
        io.execute {
            val lines = try {
                val params = MessageCreateParams.builder()
                    .model(MODEL)
                    .maxTokens(400L)
                    .thinking(ThinkingConfigDisabled.builder().build())
                    .systemOfTextBlockParams(
                        listOf(
                            // 毎回同じ部分(性格)は使い回してもらって安くする
                            TextBlockParam.builder().text(LiliPersona.SYSTEM)
                                .cacheControl(CacheControlEphemeral.builder().build()).build(),
                            TextBlockParam.builder().text(situation(context, store)).build(),
                        ),
                    )
                    .apply { history(store).forEach { (u, t) -> if (u) addUserMessage(t) else addAssistantMessage(t) } }
                    .build()
                val res = client(key).messages().create(params)
                if (res.stopReason().orElse(null) == StopReason.REFUSAL) {
                    listOf("うーん…その話は、わたしからはちょっとやめておきますね。", "ほかのお話ししましょ？")
                } else {
                    val text = res.content().mapNotNull { b -> b.text().orElse(null)?.text() }.joinToString("\n")
                    split(text)
                }
            } catch (e: UnauthorizedException) {
                listOf("あれ…API キーがちがうみたいです。", "設定でもう一度確かめてもらえますか？")
            } catch (e: PermissionDeniedException) {
                listOf("あれ…API キーがちがうみたいです。", "設定でもう一度確かめてもらえますか？")
            } catch (e: RateLimitException) {
                listOf("ちょっと待ってください、今いっぱいいっぱいで…", "少ししてからもう一回話しかけてくださいね。")
            } catch (e: AnthropicServiceException) {
                val msg = e.message.orEmpty()
                if (e.statusCode() == 400 && msg.contains("credit", ignoreCase = true)) {
                    listOf("あっ…お話しするためのクレジットがなくなっちゃったみたいです。", "Anthropic のサイトで補充してもらえますか？")
                } else {
                    listOf("ごめんなさい、うまくお返事できませんでした…", "もう一回言ってもらえますか？")
                }
            } catch (e: AnthropicIoException) {
                listOf("あれ、電波が届いてないみたいです…", "Wi-Fi のあるところでまたお話ししましょ！")
            } catch (e: Exception) {
                listOf("ごめんなさい、うまくお返事できませんでした…", "もう一回言ってもらえますか？")
            }
            main.post { finish(store, lines, callback) }
            maybeUpdateNotebook(context, key, store)
        }
    }

    private fun finish(store: ChatStore, lines: List<String>, callback: (List<String>) -> Unit) {
        val now = System.currentTimeMillis()
        lines.forEachIndexed { i, l -> store.add(ChatMessage(false, l, now + i)) }
        callback(lines)
    }

    /** 返事を、チャットの吹き出し1つずつに分ける */
    fun split(text: String): List<String> =
        text.lines().map { it.trim().removePrefix("リリ：").removePrefix("リリ:").trim() }
            .filter { it.isNotEmpty() }
            .take(4)
            .ifEmpty { listOf("…えへへ") }

    /** 最近の会話(最大24件)。ご主人様から始まり、交互になるようにまとめる */
    private fun history(store: ChatStore): List<Pair<Boolean, String>> {
        val out = ArrayList<Pair<Boolean, String>>()
        for (m in store.messages().takeLast(24)) {
            if (out.isNotEmpty() && out.last().first == m.fromUser) {
                out[out.size - 1] = m.fromUser to (out.last().second + "\n" + m.text)
            } else out += m.fromUser to m.text
        }
        while (out.isNotEmpty() && !out.first().first) out.removeAt(0)
        return out
    }

    /** 今日の様子(アプリのデータ)とノート。毎回変わる部分 */
    private fun situation(context: Context, store: ChatStore): String {
        val prefs = Prefs(context)
        val now = SimpleDateFormat("yyyy年M月d日(E) H時m分", Locale.JAPAN).format(Date())
        val b = StringBuilder()
        b.append("【いま】$now\n")
        b.append("【呼び方の初期値】${prefs.masterName}（ノートに呼び方があればそちらを優先）\n")
        b.append("【これまでに話した日数】${store.talkDays()}日\n")
        if (prefs.careCalorie) {
            val cal = CalorieStore(context)
            val t = cal.total()
            b.append("【今日のごはん】${t.kcal}kcal / 目標${cal.goal}kcal、たんぱく質${t.p.roundToInt()}g\n")
        }
        if (prefs.careWeight) WeightStore(context).latest()?.let { b.append("【体重】${it.second}kg（${it.first}）\n") }
        if (prefs.careCondition) {
            val c = ConditionStore(context).get()
            if (c.body != null || c.mood != null) b.append("【今日の調子】体調${c.body ?: "?"}/5・気分${c.mood ?: "?"}/5\n")
        }
        if (prefs.careMoney) {
            val m = MoneyStore(context)
            b.append("【今月使ったお金】${MoneyStore.yen(m.monthTotal())} / 予算${MoneyStore.yen(m.monthlyBudget)}\n")
        }
        WeatherRepository.cachedToday(prefs)?.let { b.append("【今日の天気】${it.label} ${it.temp.roundToInt()}℃\n") }
        if (prefs.homeEnabled) b.append("【いまおうちにいる？】${if (prefs.lastHomeEntered) "たぶんいる" else "たぶん外"}\n")
        val note = store.notebook
        b.append("\n【ご主人様ノート（あなたが覚えていること）】\n")
        b.append(if (note.isBlank()) "（まだ何も書いていない。会話の中で少しずつ聞いていく）" else note)
        return b.toString()
    }

    // ---------- ノートを書き直す(何回か話すごとに) ----------

    private fun maybeUpdateNotebook(context: Context, key: String, store: ChatStore) {
        if (store.sinceNote < 6) return
        store.sinceNote = 0
        runCatching {
            val convo = store.messages().takeLast(16).joinToString("\n") { (if (it.fromUser) "ご主人様: " else "リリ: ") + it.text }
            val params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(900L)
                .thinking(ThinkingConfigDisabled.builder().build())
                .system(LiliPersona.NOTE_SYSTEM)
                .addUserMessage("今日は${java.time.LocalDate.now()}です。\n\n【今のノート】\n${store.notebook.ifBlank { "（空）" }}\n\n【最近の会話】\n$convo\n\n新しいノートを書いてください。")
                .build()
            val res = client(key).messages().create(params)
            val text = res.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("\n").trim()
            if (text.isNotBlank() && res.stopReason().orElse(null) != StopReason.REFUSAL) store.notebook = text
        }
    }
}
