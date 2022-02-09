package com.github.ashutoshgngwr.noice.fragment

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.github.ashutoshgngwr.noice.BuildConfig
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.ext.launchInCustomTab
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import dagger.hilt.android.AndroidEntryPoint
import mehdi.sakout.aboutpage.AboutPage
import mehdi.sakout.aboutpage.Element
import javax.inject.Inject

@AndroidEntryPoint
class AboutFragment : Fragment() {

  @set:Inject
  internal lateinit var analyticsProvider: AnalyticsProvider

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    return AboutPage(context).run {
      setImage(R.drawable.app_banner)
      setDescription(getString(R.string.app_description))
      addItem(
        buildElement(
          R.drawable.ic_about_version,
          "v${BuildConfig.VERSION_NAME}",
          getString(R.string.app_changelog_url)
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

      if (!BuildConfig.IS_FREE_BUILD) {
        addItem(
          buildElement(
            R.drawable.about_icon_google_play,
            R.string.about_play_store,
            R.string.play_store_url,
          )
        )
      }

      addItem(
        buildElement(
          R.drawable.about_icon_instagram,
          R.string.about_instagram,
          R.string.app_instagram_url
        )
      )

      addItem(
        buildElement(
          R.drawable.about_icon_twitter,
          R.string.about_twitter,
          R.string.app_twitter_url
        )
      )

      addItem(
        buildElement(
          R.drawable.about_icon_github,
          R.string.about_github,
          R.string.app_github_url
        )
      )

      addItem(
        buildElement(
          R.drawable.about_icon_link,
          R.string.about_website,
          R.string.app_website_url
        )
      )

      addGroup(getString(R.string.created_by))
      addTwitter(creatorTwitter, creatorName)
      create()
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    analyticsProvider.setCurrentScreen("about", AboutFragment::class)
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
      .setOnClickListener { Uri.parse(url).launchInCustomTab(requireContext()) }
  }

  companion object {
    private const val creatorTwitter = "ashutoshgngwr"
    private const val creatorName = "Ashutosh Gangwar"
  }
}
