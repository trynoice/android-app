package com.github.ashutoshgngwr.noice.widget

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.View
import android.view.View.OnClickListener
import android.widget.FrameLayout
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import com.github.ashutoshgngwr.noice.DonateActivity
import com.github.ashutoshgngwr.noice.R
import kotlinx.android.synthetic.playstore.view_donate.view.*

class DonateView : FrameLayout {

  constructor(@NonNull context: Context) : super(context)
  constructor(@NonNull context: Context, @Nullable attrs: AttributeSet) : super(context, attrs)
  constructor(@NonNull context: Context, @Nullable attrs: AttributeSet, defStyleAttr: Int) : super(
    context, attrs, defStyleAttr
  )

  init {
    View.inflate(context, R.layout.view_donate, this)
    button_usd1.setOnClickListener(clickListenerFor(DonateActivity.DONATE_AMOUNT_1USD))
    button_usd2.setOnClickListener(clickListenerFor(DonateActivity.DONATE_AMOUNT_2USD))
    button_usd5.setOnClickListener(clickListenerFor(DonateActivity.DONATE_AMOUNT_5USD))
    button_usd10.setOnClickListener(clickListenerFor(DonateActivity.DONATE_AMOUNT_10USD))
    button_usd15.setOnClickListener(clickListenerFor(DonateActivity.DONATE_AMOUNT_15USD))
    button_usd25.setOnClickListener(clickListenerFor(DonateActivity.DONATE_AMOUNT_25USD))
  }

  private fun clickListenerFor(donateAmount: String): OnClickListener {
    return OnClickListener {
      Intent(context, DonateActivity::class.java).also {
        it.putExtra(DonateActivity.EXTRA_DONATE_AMOUNT, donateAmount)
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(it)
      }
    }
  }
}
