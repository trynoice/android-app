package com.github.ashutoshgngwr.noice.widget

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View.OnClickListener
import android.widget.FrameLayout
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import com.github.ashutoshgngwr.noice.DonateActivity
import com.github.ashutoshgngwr.noice.databinding.DonateViewBinding

class DonateView : FrameLayout {

  private val binding: DonateViewBinding

  constructor(@NonNull context: Context) : super(context)
  constructor(@NonNull context: Context, @Nullable attrs: AttributeSet) : super(context, attrs)
  constructor(@NonNull context: Context, @Nullable attrs: AttributeSet, defStyleAttr: Int) : super(
    context, attrs, defStyleAttr
  )

  init {
    LayoutInflater.from(context).also {
      binding = DonateViewBinding.inflate(it, this, true)
    }

    binding.oneUsdButton.setOnClickListener(clickListenerFor(DonateActivity.DONATE_AMOUNT_1USD))
    binding.twoUsdButton.setOnClickListener(clickListenerFor(DonateActivity.DONATE_AMOUNT_2USD))
    binding.fiveUsdButton.setOnClickListener(clickListenerFor(DonateActivity.DONATE_AMOUNT_5USD))
    binding.tenUsdButton.setOnClickListener(clickListenerFor(DonateActivity.DONATE_AMOUNT_10USD))
    binding.fifteenUsdButton.setOnClickListener(clickListenerFor(DonateActivity.DONATE_AMOUNT_15USD))
    binding.twentyfiveUsdButton.setOnClickListener(clickListenerFor(DonateActivity.DONATE_AMOUNT_25USD))
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
