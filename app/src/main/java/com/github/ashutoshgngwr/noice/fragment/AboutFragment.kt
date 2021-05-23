package com.github.ashutoshgngwr.noice.fragment

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
      addItem(
        buildElement(
          R.drawable.ic_about_version,
          "v${BuildConfig.VERSION_NAME}",
          getString(R.string.app_changelog)
        )
      )

      addItem(
        buildElement(
          R.drawable.ic_about_copyright,
          R.string.app_copyright,
          R.string.app_license_url
        )
      )
      addItem(
        buildElement(
          R.drawable.ic_about_group,
          R.string.app_authors,
          R.string.app_authors_url
        )
      )

      addItem(
        buildElement(
          R.drawable.about_icon_link,
          R.string.about_website,
          R.string.app_website
        )
      )

      @Suppress("ConstantConditionIf")
      if (BuildConfig.IS_PLAY_STORE_BUILD) {
        addItem(
          buildElement(
            R.drawable.about_icon_google_play,
            R.string.about_play_store,
            R.string.support_development__share_url,
          )
        )
      }

      addItem(
        buildElement(
          R.drawable.about_icon_github,
          R.string.about_github,
          R.string.app_github_url
        )
      )

      create()
    }
  }

  private fun buildElement(
    @DrawableRes iconId: Int,
    @StringRes titleResId: Int,
    @StringRes urlResId: Int
  ): Element {
    return buildElement(iconId, getString(titleResId), getString(urlResId))
  }

  private fun buildElement(@DrawableRes iconId: Int, title: String, url: String): Element {
    return Element(title, iconId)
      .setAutoApplyIconTint(true)
      .setOnClickListener {
        customTabsIntent.launchUrl(requireContext(), Uri.parse(url))
      }
  }
}
