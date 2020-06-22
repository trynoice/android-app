package com.github.ashutoshgngwr.noice.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.github.ashutoshgngwr.noice.R
import kotlinx.android.synthetic.main.fragment_support_development.*

class SupportDevelopmentFragment : Fragment(R.layout.fragment_support_development) {

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    button_share.setOnClickListener {
      val targetURL = getString(R.string.support_development__share_url)
      Intent(Intent.ACTION_VIEW, Uri.parse(targetURL)).also {
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(it)
      }
    }
  }
}
