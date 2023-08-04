package com.github.ashutoshgngwr.noice.billing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.github.ashutoshgngwr.noice.databinding.GooglePlayDonationPurchasePendingFragmentBinding
import com.github.ashutoshgngwr.noice.metrics.AnalyticsProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class GooglePlayDonationPurchasePendingFragment : BottomSheetDialogFragment() {

  @set:Inject
  internal var analyticsProvider: AnalyticsProvider? = null

  private lateinit var binding: GooglePlayDonationPurchasePendingFragmentBinding

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = GooglePlayDonationPurchasePendingFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.okay.setOnClickListener { dismiss() }
    analyticsProvider?.setCurrentScreen(this::class)
  }
}
