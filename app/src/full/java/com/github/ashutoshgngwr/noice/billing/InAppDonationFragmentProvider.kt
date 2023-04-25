package com.github.ashutoshgngwr.noice.billing

import androidx.fragment.app.Fragment
import com.github.ashutoshgngwr.noice.fragment.InAppDonationFragment

object InAppDonationFragmentProvider : DonationFragmentProvider {

  override fun get(): Fragment {
    return InAppDonationFragment()
  }
}
