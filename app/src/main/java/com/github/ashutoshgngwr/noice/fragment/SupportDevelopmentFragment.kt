package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ShareCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.SupportDevelopmentFragmentBinding
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.DonationFragmentProvider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SupportDevelopmentFragment : Fragment() {

  private lateinit var binding: SupportDevelopmentFragmentBinding

  @set:Inject
  internal lateinit var donationFragmentProvider: DonationFragmentProvider

  @set:Inject
  internal lateinit var analyticsProvider: AnalyticsProvider

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    binding = SupportDevelopmentFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    childFragmentManager.beginTransaction()
      .add(R.id.donate_view_container, donationFragmentProvider.get())
      .commit()

    binding.shareButton.setOnClickListener {
      val text = getString(R.string.app_description)
      val playStoreURL = getString(R.string.play_store_url)
      val fdroidURL = getString(R.string.fdroid_url)
      ShareCompat.IntentBuilder(requireActivity())
        .setChooserTitle(R.string.support_development__share)
        .setType("text/plain")
        .setText("$text\n\n$playStoreURL\n$fdroidURL")
        .startChooser()

      analyticsProvider.logEvent("share_app_with_friends", bundleOf())
    }

    analyticsProvider.setCurrentScreen("support_development", SupportDevelopmentFragment::class)
  }
}
