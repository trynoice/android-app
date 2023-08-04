package com.github.ashutoshgngwr.noice.billing

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit

class OpenCollectiveDonationFlowProvider : DonationFlowProvider {

  override fun addButtons(fragmentManager: FragmentManager, containerId: Int) {
    fragmentManager.commit { replace(containerId, OpenCollectiveDonateButtonFragment()) }
  }

  override fun setCallbackFragmentHost(hostActivity: AppCompatActivity) {
    // no-op
  }
}
