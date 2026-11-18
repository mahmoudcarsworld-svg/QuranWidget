package com.example.quranwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private lateinit var occTv: TextView
    private lateinit var verseTv: TextView
    private lateinit var refTv: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dn = resources.displayMetrics.density
        fun px(v: Int) = (v * dn).toInt()
        val tf = resources.getFont(R.font.amiri_quran)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(px(20), px(48), px(20), px(24))
        }
        setContentView(ScrollView(this).apply { addView(col) })

        fun lp(top: Int = 12) = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = px(top) }

        col.addView(TextView(this).apply {
            text = "آيات"; textSize = 26f; setTextColor(Color.parseColor("#0B3D2E")); gravity = Gravity.CENTER
        }, lp(0))

        // بطاقة المعاينة
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.widget_bg)
            setPadding(px(18), px(18), px(18), px(18))
        }
        occTv = TextView(this).apply { setTextColor(Color.parseColor("#FFD98E")); textSize = 13f; gravity = Gravity.CENTER }
        verseTv = TextView(this).apply { setTextColor(Color.WHITE); textSize = 24f; typeface = tf; gravity = Gravity.CENTER }
        refTv = TextView(this).apply { setTextColor(Color.parseColor("#CFE8D5")); textSize = 13f; gravity = Gravity.CENTER }
        card.addView(occTv); card.addView(verseTv, lp(8)); card.addView(refTv, lp(8))
        col.addView(card, lp(16))

        fun btn(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label; setOnClickListener { onClick() }
        }

        col.addView(btn("آية أخرى") {
            Quran.current(this, true); showVerse(); VerseWidget.refreshAll(this, false)
        }, lp())

        // نوع الآيات
        col.addView(TextView(this).apply { text = "نوع الآيات"; textSize = 16f }, lp(20))
        val rg = RadioGroup(this)
        val r0 = RadioButton(this).apply { text = "مناسبة لليوم (الجمعة، رمضان، الأعياد…) وإلا متنوعة"; id = View.generateViewId() }
        val r1 = RadioButton(this).apply { text = "متنوعة من كامل المصحف"; id = View.generateViewId() }
        rg.addView(r0); rg.addView(r1)
        rg.check(if (Quran.getMode(this) == 0) r0.id else r1.id)
        rg.setOnCheckedChangeListener { _, id ->
            Quran.setMode(this, if (id == r0.id) 0 else 1)
            showVerse(); VerseWidget.refreshAll(this, false)
        }
        col.addView(rg, lp(4))

        // الودجت
        col.addView(TextView(this).apply { text = "الشاشة الرئيسية"; textSize = 16f }, lp(20))
        col.addView(btn("إضافة الودجت للشاشة الرئيسية") {
            val mgr = AppWidgetManager.getInstance(this)
            if (mgr.isRequestPinAppWidgetSupported) {
                mgr.requestPinAppWidget(ComponentName(this, VerseWidget::class.java), null, null)
            } else {
                toast("اضغط مطولاً على الشاشة الرئيسية ← الودجات ← آيات")
            }
        }, lp(4))

        // شاشة القفل
        col.addView(TextView(this).apply { text = "شاشة القفل"; textSize = 16f }, lp(20))
        col.addView(TextView(this).apply {
            text = "أندرويد لا يسمح غالباً بودجت على شاشة القفل، لذلك يمكن وضع الآية كخلفية لشاشة القفل (سيستبدل ذلك خلفية القفل الحالية)."
            textSize = 13f
        }, lp(4))
        col.addView(btn("تعيين الآية الآن كخلفية لشاشة القفل") {
            Thread {
                try { LockScreen.apply(this, false); runOnUiThread { toast("تم تعيين خلفية القفل") } }
                catch (e: Exception) { runOnUiThread { toast("تعذّر تعيين الخلفية") } }
            }.start()
        }, lp(8))

        val sp = getSharedPreferences("quran", MODE_PRIVATE)
        col.addView(Switch(this).apply {
            text = "تغيير خلفية القفل تلقائياً كل 6 ساعات"
            isChecked = sp.getBoolean("lock", false)
            setOnCheckedChangeListener { _, on ->
                sp.edit().putBoolean("lock", on).apply()
                val wm = WorkManager.getInstance(this@MainActivity)
                if (on) {
                    wm.enqueueUniquePeriodicWork(
                        "lock", ExistingPeriodicWorkPolicy.UPDATE,
                        PeriodicWorkRequestBuilder<LockWorker>(6, TimeUnit.HOURS).build()
                    )
                    toast("تم التفعيل")
                } else wm.cancelUniqueWork("lock")
            }
        }, lp(12))

        showVerse()
    }

    private fun showVerse() {
        val v = Quran.current(this)
        verseTv.text = v.display
        refTv.text = v.ref
        occTv.text = v.occasion ?: ""
        occTv.visibility = if (v.occasion != null) View.VISIBLE else View.GONE
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
