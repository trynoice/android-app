package com.github.ashutoshgngwr.noice.fragment

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import com.github.ashutoshgngwr.noice.BuildConfig
import com.github.ashutoshgngwr.noice.R
import mehdi.sakout.aboutpage.AboutPage
import mehdi.sakout.aboutpage.Element

class AboutFragment : Fragment() {

  private lateinit var customTabsIntent: CustomTabsIntent

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    customTabsIntent = CustomTabsIntent.Builder()
      .setDefaultColorSchemeParams(
        CustomTabColorSchemeParams.Builder()
          .setToolbarColor(
            ResourcesCompat.getColor(
              resources,
              R.color.action_bar,
              requireContext().theme
            )
          )
          .build()
      )
      .build()

    return AboutPage(context).run {
      setImage(R.drawable.app_banner)
      setDescription(getString(R.string.app_description))
      addItem(getVersionElement())
      addItem(
        createElement(
          R.string.app_copyright,
          R.drawable.ic_about_copyright,
          R.string.app_license_url
        )
      )
      addItem(
        createElement(
          R.string.app_authors,
          R.drawable.ic_about_group,
          R.string.app_authors_url
        )
      )

      addItem(
        createElement(
          R.string.about_website,
          R.drawable.about_icon_link,
          R.string.app_website
        )
      )

      @Suppress("ConstantConditionIf")
      if (BuildConfig.IS_PLAY_STORE_BUILD) {
        addPlayStore(requireContext().packageName)
      }

      addItem(
        createElement(
          R.string.about_github,
          R.drawable.about_icon_github,
          R.string.app_github_url
        )
      )

      create()
    }
  }

  private fun getVersionElement(): Element {
    val version =
      requireContext().packageManager?.getPackageInfo(requireContext().packageName, 0)?.versionName
    return Element("v$version", R.drawable.ic_about_version)
      .setOnClickListener {
        customTabsIntent.launchUrl(requireContext(), Uri.parse(getString(R.string.app_changelog)))
      }
  }

  private fun createElement(titleResId: Int, iconId: Int, urlResId: Int): Element {
    return Element(getString(titleResId), iconId)
      .setOnClickListener {
        customTabsIntent.launchUrl(requireContext(), Uri.parse(getString(urlResId)))
      }
  }
}
