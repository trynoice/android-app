package com.github.ashutoshgngwr.noice.provider

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.ext.launchInCustomTab

/**
 * [DonateViewProvider] provides an interface to add different variants of donate buttons in support
 * development fragment based on the implementation used by the application.
 */
interface DonateViewProvider {

  /**
   * Create a new donate view and attach it to the given [parent].
   */
  fun addViewToParent(parent: ViewGroup)
}

object OpenCollectiveDonateViewProvider : DonateViewProvider {

  override fun addViewToParent(parent: ViewGroup) {
    LayoutInflater.from(parent.context)
      .inflate(R.layout.open_collective_donate_button, parent, false)
      .apply {
        parent.addView(this)
        setOnClickListener {
          val url = it.context.getString(R.string.open_collective_url)
          Uri.parse(url).launchInCustomTab(it.context)
        }
      }
  }
}
