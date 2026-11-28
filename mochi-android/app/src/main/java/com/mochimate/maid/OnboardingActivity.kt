package com.mochimate.maid

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * はじめての会話。リリが生活のことを聞いて、お世話することを提案し、必要な許可を1つずつお願いする。
 * 「お世話を見直す」からも開ける(その時は名前などは聞かない)。AI は使わない(決まった会話)。
 */
class OnboardingActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var list: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var answers: FlowLayout
    private lateinit var inputRow: LinearLayout
    private lateinit var input: EditText
    private lateinit var maid: MochiView
    private val d by lazy { resources.displayMetrics.density }
    private fun px(v: Int) = (v * d).toInt()

    private var review = false
    private val steps = ArrayDeque<() -> Unit>()
    /** 設定画面から戻ってきた時に続ける処理 */
    private var onReturn: (() -> Unit)? = null

    // 聞いたこと
    private var name = ""
    private var tone = ""
    private var life = ""
    private val wants = linkedSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        review = intent.getBooleanExtra(EXTRA_REVIEW, false)
        buildUi()
        if (review) {
            steps += { say("お世話の見直しですね！生活のこと、もう一回聞かせてください。") { next() } }
        } else {
            steps += ::greet
            steps += ::askName
            steps += ::askTone
        }
        steps += ::askLife
        steps += { askYesNo("care_diet", "ごはんとか体重のこと、気になってたりします？", listOf("ダイエットしたい", "健康的に食べたい", "とくにない")) { a -> if (a != "とくにない") wants += listOf("calorie", "weight", "condition") } }
        steps += { askYesNo("care_money", "お金のこと…ついつい使いすぎちゃうこと、あります？", listOf("ある…", "たまに", "ないかな")) { a -> if (a != "ないかな") wants += "money" } }
        steps += { askYesNo("care_plan", "予定とかやること、うっかり忘れちゃうことあります？", listOf("よくある", "たまに", "ないかな")) { a -> if (a != "ないかな") wants += listOf("schedule", "todo") } }
        steps += { askYesNo("care_garbage", "ゴミの日、うっかり忘れちゃうことは？", listOf("ある", "ない", "ひとり暮らしじゃない")) { a -> if (a == "ある") wants += "garbage" } }
        steps += { askYesNo("care_screen", "スマホ、つい見すぎちゃいます？", listOf("見すぎちゃう", "ちょっとだけ", "大丈夫")) { a -> if (a == "見すぎちゃう") wants += "screen" } }
        steps += { askYesNo("care_focus", "勉強とか作業とか、集中したい時間ってあります？", listOf("ある", "ないかな")) { a -> if (a == "ある") wants += "focus" } }
        steps += { askYesNo("care_weather", "天気って、毎朝チェックしたりします？", listOf("知りたい", "いらない")) { a -> if (a == "知りたい") wants += "weather" } }
        steps += { askYesNo("care_home", "おうちに帰ってきたら、『おかえり』って言ってほしいですか？", listOf("言ってほしい！", "いらないかな")) { a -> if (a != "いらないかな") wants += "home" } }
        steps += ::propose
        next()
    }

    override fun onResume() {
        super.onResume()
        onReturn?.let { onReturn = null; list.postDelayed({ it() }, 400) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onReturn?.let { onReturn = null; list.postDelayed({ it() }, 300) }
    }

    private fun next() {
        val step = steps.removeFirstOrNull() ?: return
        step()
    }

    // ---------- 会話の中身 ----------

    private fun greet() {
        say("はじめまして！今日からお世話係をさせてもらう、メイドのリリです。") {
            say("ご主人様の毎日を、いろいろお手伝いしますね。まずは少しだけ、お話を聞かせてください！") { next() }
        }
    }

    private fun askName() {
        say("なんて呼んだらいいですか？") {
            textAnswer("呼んでほしい名前", listOf("ご主人様")) { a ->
                name = a
                prefs.masterName = a
                say("${a}ですね！よろしくお願いします、${a}♪") { next() }
            }
        }
    }

    private fun askTone() {
        say("わたしの話し方、敬語とタメ口、どっちがいいですか？") {
            choose(listOf("敬語がいい", "タメ口がいい", "おまかせ")) { a ->
                tone = a
                say(if (a == "タメ口がいい") "わかった！じゃあ、だんだんくだけた感じにしていくね♪" else "わかりました！") { next() }
            }
        }
    }

    private fun askLife() {
        say("ふだんは、何してる人なんですか？") {
            choose(listOf("学生", "社会人", "就活中", "おうちにいることが多い", "そのほか")) { a ->
                life = a
                val reply = when (a) {
                    "学生" -> "学生さんなんですね！勉強もがんばりましょ。"
                    "社会人" -> "毎日おつかれさまです…！"
                    "就活中" -> "就活中なんですね。いっしょにがんばりましょう！"
                    "おうちにいることが多い" -> "おうち時間が長いんですね。生活のリズム、いっしょに整えましょ！"
                    else -> "そうなんですね！"
                }
                say(reply) { next() }
            }
        }
    }

    private fun askYesNo(key: String, q: String, options: List<String>, onAnswer: (String) -> Unit) {
        say(q) {
            choose(options) { a ->
                getSharedPreferences("mochi_onboarding", MODE_PRIVATE).edit().putString(key, a).apply()
                onAnswer(a)
                next()
            }
        }
    }

    // ---------- 提案 ----------

    private data class Care(val key: String, val label: String)

    private val allCares = listOf(
        Care("weather", "☀️ 天気"), Care("calorie", "🍙 カロリー"), Care("weight", "⚖️ 体重"), Care("condition", "😊 調子"),
        Care("money", "💰 お金"), Care("schedule", "📅 予定"), Care("todo", "✅ やること"), Care("garbage", "🗑️ ゴミの日"),
        Care("focus", "⏱️ 集中"), Care("screen", "📱 スマホ時間"), Care("home", "🏠 おかえり"),
    )

    private fun propose() {
        if (wants.isEmpty()) wants += listOf("weather", "todo")
        val picked = linkedSetOf<String>().apply { addAll(wants) }
        say("わかりました！じゃあ、わたしはこれをお世話しますね。") {
            say("ほかにもお願いしたいことや、いらないものがあったら、タップして変えてください。") {
                clearAnswers()
                fun draw() {
                    answers.removeAllViews()
                    allCares.forEach { c ->
                        val on = c.key in picked
                        answers.addView(chip(c.label, on) { if (!picked.remove(c.key)) picked.add(c.key); draw() })
                    }
                    answers.addView(chip("これでお願い！", false, primary = true) {
                        clearAnswers()
                        userBubble(allCares.filter { it.key in picked }.joinToString("・") { it.label })
                        apply(picked)
                    })
                }
                draw()
            }
        }
    }

    private fun apply(picked: Set<String>) {
        prefs.careWeather = "weather" in picked
        prefs.careCalorie = "calorie" in picked
        prefs.careWeight = "weight" in picked
        prefs.careCondition = "condition" in picked
        prefs.careMoney = "money" in picked
        prefs.careSchedule = "schedule" in picked
        prefs.careTodo = "todo" in picked
        prefs.careGarbage = "garbage" in picked
        prefs.careFocus = "focus" in picked
        prefs.careScreenTime = "screen" in picked
        if ("home" !in picked) prefs.homeEnabled = false
        writeNotebook(picked)

        say("まかせてください！") {
            // 必要な許可を順番に
            steps.clear()
            steps += ::permOverlay
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) steps += ::permNotifications
            if ("weather" in picked && prefs.cityName == null) steps += ::askCity
            if ("home" in picked) steps += ::permHome
            if ("money" in picked) steps += ::permPayments
            if ("screen" in picked) steps += ::permUsage
            if ("garbage" in picked) steps += { say("ゴミの日は、ホームの「🗑️ ゴミの日」から曜日を教えてくださいね。") { next() } }
            steps += ::wrapUp
            next()
        }
    }

    // ---------- 許可のお願い ----------

    private fun permOverlay() {
        if (Settings.canDrawOverlays(this)) { next(); return }
        say("ひとつお願いがあって…わたし、ホーム画面にいるために「ほかのアプリの上に重ねて表示」の許可が必要なんです。") {
            say("設定が開くので、「もちもちメイド」をオンにして戻ってきてくださいね。") {
                choose(listOf("許可しにいく", "あとで")) { a ->
                    if (a == "あとで") { say("わかりました。設定タブからいつでもできますよ！") { next() }; return@choose }
                    onReturn = { say(if (Settings.canDrawOverlays(this)) "ありがとうございます！これでホーム画面に行けます♪" else "あれ、まだみたいです…あとで設定タブからでも大丈夫ですよ。") { next() } }
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
            }
        }
    }

    private fun permNotifications() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) { next(); return }
        say("それと、わたしがホーム画面にいない時は通知でお知らせしたいので、通知も許可してもらえますか？") {
            choose(listOf("いいよ", "あとで")) { a ->
                if (a == "あとで") { next(); return@choose }
                onReturn = { next() }
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
    }

    private fun askCity() {
        say("天気を調べたいので、住んでいる街を教えてください！（例：横浜、仙台）") {
            textAnswer("街の名前", emptyList()) { q ->
                say("さがしてきますね…") {
                    WeatherRepository.searchCity(q) { cities ->
                        if (cities.isEmpty()) {
                            say("ごめんなさい、見つかりませんでした…あとで設定タブから選んでくださいね。") { next() }
                        } else {
                            say("どれですか？") {
                                choose(cities.take(5).map { "${it.name}（${it.detail}）" } + "ちがう") { a ->
                                    val city = cities.firstOrNull { "${it.name}（${it.detail}）" == a }
                                    if (city != null) { prefs.setCity(city); say("${city.name}ですね！毎日見ておきます♪") { next() } }
                                    else say("あとで設定タブから選んでくださいね。") { next() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun permHome() {
        say("「おかえり」を言うために、家のWi-Fiと位置情報を使います。場所はこのスマホの中だけで使って、どこにも送りませんよ。") {
            choose(listOf("いいよ", "あとで")) { a ->
                if (a == "あとで") { prefs.homeEnabled = false; say("わかりました。設定の「おうち」からいつでもできます！") { next() }; return@choose }
                prefs.homeEnabled = true
                onReturn = ::homeWifi
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 20)
            }
        }
    }

    private fun homeWifi() {
        say("今、おうちのWi-Fiにつながってますか？") {
            choose(listOf("つながってる", "いまは外")) { a ->
                if (a != "つながってる") { say("じゃあ、おうちにいる時に設定の「おうち」から登録してくださいね。") { homeBackground() }; return@choose }
                HomeWifiWatcher.currentSsid(this) { ssid ->
                    if (ssid == null) say("うーん、Wi-Fiの名前がわかりませんでした…設定の「おうち」からもう一回ためしてください。") { homeBackground() }
                    else {
                        prefs.homeWifi = ssid
                        prefs.lastHomeEntered = true
                        prefs.lastHomeEventAt = System.currentTimeMillis()
                        prefs.lastHomeSource = "wifi"
                        OverlayService.restartHomeWifi()
                        say("「$ssid」をおうちのWi-Fiとして覚えました！") { homeBackground() }
                    }
                }
            }
        }
    }

    private fun homeBackground() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) { next(); return }
        say("わたしが裏にいる時も気づけるように、位置情報を「常に許可」にしてもらえると助かります。") {
            choose(listOf("設定する", "あとで")) { a ->
                if (a == "あとで") { next(); return@choose }
                onReturn = { HomeGeofence.register(this); next() }
                requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 21)
            }
        }
    }

    private fun permPayments() {
        say("お金のこと、PayPay とかの支払い通知から、家計簿に自動でつけることもできますよ。") {
            choose(listOf("やってほしい", "手で入れる")) { a ->
                if (a != "やってほしい") { next(); return@choose }
                say("「連携の設定」で通知へのアクセスを許可して、支払いアプリに 💰 を付けてくださいね。いま開きます！") {
                    onReturn = { next() }
                    startActivity(Intent(this, IntegrationActivity::class.java))
                }
            }
        }
    }

    private fun permUsage() {
        if (ScreenTime.granted(this)) { next(); return }
        say("スマホを使った時間を見るために、「使用状況へのアクセス」の許可がいります。") {
            choose(listOf("許可しにいく", "あとで")) { a ->
                if (a == "あとで") { next(); return@choose }
                onReturn = { next() }
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }
    }

    private fun wrapUp() {
        prefs.onboarded = true
        GarbageAlarm.schedule(this)
        ScheduleAlarm.schedule(this)
        val n = prefs.masterName
        say(if (review) "見直し、おわりました！これからもよろしくお願いしますね、$n♪" else "準備できました！これからよろしくお願いしますね、$n♪") {
            say("お世話してほしいことが変わったら、いつでも「お世話を見直す」から教えてください。") {
                clearAnswers()
                val canOverlay = Settings.canDrawOverlays(this)
                choose(if (canOverlay && !OverlayService.isRunning) listOf("ホーム画面に来て！", "アプリのホームを見る") else listOf("アプリのホームを見る")) { a ->
                    if (a == "ホーム画面に来て！") {
                        OverlayService.start(this)
                        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } else {
                        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                    }
                    finish()
                }
            }
        }
    }

    /** 聞いたことを、リリのノートにも書いておく(あとで AI の会話を使う時に、もう知っている状態にする) */
    private fun writeNotebook(picked: Set<String>) {
        val store = ChatStore(this)
        val cares = allCares.filter { it.key in picked }.joinToString("・") { it.label.substringAfter(" ") }
        val lines = buildList {
            if (name.isNotBlank()) add("呼び方：$name")
            if (life.isNotBlank()) add("どんな人：$life")
            if (tone.isNotBlank()) add("話し方の好み：$tone")
            add("お世話していること：$cares")
        }.joinToString("\n")
        store.notebook = if (store.notebook.isBlank()) lines else store.notebook + "\n（はじめの会話より）\n" + lines
    }

    // ---------- 画面 ----------

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.bg))
            fitsSystemWindows = true
        }
        setContentView(root)
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(px(12), px(6), px(12), px(4)) }
        maid = MochiView(this)
        top.addView(maid, LinearLayout.LayoutParams(px(60), px(76)))
        top.addView(TextView(this).apply { text = "リリ"; textSize = 18f; setTextColor(getColor(R.color.ink)); paint.isFakeBoldText = true; setPadding(px(6), 0, 0, 0) })
        root.addView(top)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(12), px(4), px(12), px(12)) }
        scroll = ScrollView(this).apply { addView(list) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.card))
            setPadding(px(12), px(10), px(12), px(12))
        }
        answers = FlowLayout(this)
        bottom.addView(answers)
        inputRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; visibility = View.GONE }
        input = EditText(this).apply { textSize = 16f; setSingleLine(); imeOptions = EditorInfo.IME_ACTION_DONE }
        inputRow.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        bottom.addView(inputRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(6) })
        root.addView(bottom)
    }

    /** リリがしゃべる(人がチャットするみたいに少し間をあけて) */
    private fun say(text: String, then: () -> Unit) {
        clearAnswers()
        list.postDelayed({
            if (isFinishing) return@postDelayed
            list.addView(bubble(text, user = false))
            maid.react()
            scrollDown()
            list.postDelayed(then, 250L + text.length * 18L)
        }, 350)
    }

    private fun userBubble(text: String) {
        list.addView(bubble(text, user = true))
        scrollDown()
    }

    private fun bubble(text: String, user: Boolean): View {
        val tv = TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(getColor(R.color.ink))
            setLineSpacing(0f, 1.15f)
            setPadding(px(12), px(8), px(12), px(8))
            setBackgroundResource(if (user) R.drawable.chip_custom_bg else R.drawable.bubble_card_bg)
            maxWidth = px(290)
        }
        return LinearLayout(this).apply {
            gravity = if (user) Gravity.END else Gravity.START
            setPadding(0, px(4), 0, px(4))
            addView(tv)
        }
    }

    private fun choose(options: List<String>, onPick: (String) -> Unit) {
        clearAnswers()
        options.forEach { o -> answers.addView(chip(o, false) { clearAnswers(); userBubble(o); onPick(o) }) }
    }

    private fun textAnswer(hint: String, quick: List<String>, onAnswer: (String) -> Unit) {
        clearAnswers()
        inputRow.visibility = View.VISIBLE
        input.hint = hint
        input.setText("")
        fun submit(v: String) {
            val t = v.trim()
            if (t.isEmpty()) return
            clearAnswers()
            userBubble(t)
            onAnswer(t)
        }
        quick.forEach { q -> answers.addView(chip(q, false) { submit(q) }) }
        answers.addView(chip("決定", false, primary = true) { submit(input.text.toString()) })
        input.setOnEditorActionListener { _, _, _ -> submit(input.text.toString()); true }
        input.requestFocus()
    }

    private fun clearAnswers() {
        answers.removeAllViews()
        inputRow.visibility = View.GONE
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
    }

    private fun chip(t: String, on: Boolean, primary: Boolean = false, onClick: () -> Unit) = TextView(this).apply {
        text = t
        textSize = 15f
        setTextColor(if (on || primary) 0xFFFFFFFF.toInt() else getColor(R.color.ink))
        paint.isFakeBoldText = on || primary
        setBackgroundResource(when { primary -> R.drawable.button_bg; on -> R.drawable.chip_on_bg; else -> R.drawable.chip_bg })
        setPadding(px(14), px(9), px(14), px(9))
        setOnClickListener { onClick() }
    }

    private fun scrollDown() = scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }

    companion object {
        const val EXTRA_REVIEW = "review"
    }
}
