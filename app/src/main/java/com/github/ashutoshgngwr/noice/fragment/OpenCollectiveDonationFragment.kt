package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.ext.startCustomTab

class OpenCollectiveDonationFragment : Fragment(R.layout.open_collective_donation_fragment) {

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    view.setOnClickListener { requireContext().startCustomTab(R.string.open_collective_url) }
  }
}
