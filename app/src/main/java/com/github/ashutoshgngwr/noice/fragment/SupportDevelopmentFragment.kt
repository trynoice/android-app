package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.View
import androidx.core.app.ShareCompat
import androidx.fragment.app.Fragment
import com.github.ashutoshgngwr.noice.R
import kotlinx.android.synthetic.main.fragment_support_development.*

class SupportDevelopmentFragment : Fragment(R.layout.fragment_support_development) {

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    button_share.setOnClickListener {
      val text = getString(R.string.app_description)
      val targetURL = getString(R.string.support_development__share_url)
      ShareCompat.IntentBuilder.from(requireActivity())
        .setChooserTitle(R.string.support_development__share)
        .setType("text/plain")
        .setText("$text\n\n$targetURL")
        .startChooser()
    }
  }
}
