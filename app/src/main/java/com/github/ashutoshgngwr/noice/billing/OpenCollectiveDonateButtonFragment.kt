package com.github.ashutoshgngwr.noice.billing

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.ext.startCustomTab

class OpenCollectiveDonateButtonFragment :
  Fragment(R.layout.open_collective_donate_button_fragment) {

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    view.setOnClickListener { requireContext().startCustomTab(R.string.open_collective_url) }
  }
}
