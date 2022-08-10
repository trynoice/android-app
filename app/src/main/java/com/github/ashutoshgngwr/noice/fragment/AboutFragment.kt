package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.github.ashutoshgngwr.noice.BuildConfig
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.ext.startCustomTab
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.mikepenz.aboutlibraries.LibsBuilder
import com.mikepenz.aboutlibraries.LibsConfiguration
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.util.SpecialButton
import dagger.hilt.android.AndroidEntryPoint
import mehdi.sakout.aboutpage.AboutPage
import mehdi.sakout.aboutpage.Element
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class AboutFragment : Fragment(), LibsConfiguration.LibsListener {

  @set:Inject
  internal lateinit var analyticsProvider: AnalyticsProvider

  private val mainNavController by lazy {
    Navigation.findNavController(requireActivity(), R.id.main_nav_host_fragment)
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    return AboutPage(context).run {
      setImage(R.drawable.app_banner)
      setDescription(getString(R.string.app_description))
      addItem(
        buildElement(
          R.drawable.ic_about_version,
          "v${BuildConfig.VERSION_NAME}",
          getString(R.string.app_changelog_url),
        )
      )

      addItem(
        buildElement(
          R.drawable.ic_about_copyright,
          getString(R.string.app_copyright, Calendar.getInstance().get(Calendar.YEAR)),
          getString(R.string.app_authors_url),
        )
      )

      addItem(
        buildElement(
          R.drawable.ic_baseline_shield_24,
          R.string.app_license,
          R.string.app_license_url,
        )
      )

      addItem(
        buildElement(
          R.drawable.ic_baseline_privacy_tip_24,
          R.string.privacy_policy,
          R.string.app_privacy_policy_url,
        )
      )

      addItem(
        buildElement(
          R.drawable.ic_baseline_policy_24,
          R.string.terms_of_service,
          R.string.app_tos_url,
        )
      )

      addGroup(getString(R.string.reach_us))

      if (!BuildConfig.IS_FREE_BUILD) {
        addItem(
          buildElement(
            R.drawable.about_icon_google_play,
            R.string.write_review_play_store,
            R.string.play_store_url,
          )
        )
      }

      addItem(
        buildElement(
          R.drawable.about_icon_email,
          getString(R.string.connect_through_email),
          "mailto:trynoiceapp@gmail.com"
        )
      )

      addItem(
        buildElement(
          R.drawable.about_icon_link,
          R.string.about_website,
          R.string.app_website_url
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
          R.drawable.about_icon_instagram,
          R.string.about_instagram,
          R.string.app_instagram_url
        )
      )

      addItem(
        buildElement(
          R.drawable.about_icon_github,
          R.string.about_github,
          R.string.app_github_url
        )
      )

      addGroup(getString(R.string.created_by))
      addTwitter("ashutoshgngwr", "Ashutosh Gangwar")

      addGroup(getString(R.string.third_party_attributions))
      addItem(
        buildElement(R.drawable.ic_baseline_shield_24, getString(R.string.oss_licenses)) {
          val data = LibsBuilder().withListener(this@AboutFragment)
          mainNavController.navigate(R.id.oss_licenses, bundleOf("data" to data))
        }
      )

      addItem(
        buildElement(
          R.drawable.ic_launcher_24dp,
          "white noise icon by Juraj Sedlák",
          "https://thenounproject.com/term/white-noise/1287855/"
        )
      )

      addItem(
        buildElement(
          R.drawable.ic_appintro_chromecast,
          "Chromecast icon by Oliver Silvérus, SE",
          "https://thenounproject.com/term/chromecast/2135765"
        )
      )

      addItem(
        buildElement(
          R.drawable.ic_appintro_library,
          "Ecosystem icon by Made x Made",
          "https://thenounproject.com/term/ecosystem/2318259"
        )
      )

      addItem(
        buildElement(
          R.drawable.ic_appintro_presets,
          "Equalizer icon by Souvik Bhattacharjee",
          "https://thenounproject.com/term/equalizer/1596234"
        )
      )

      addItem(
        buildElement(
          R.drawable.ic_settings_audio_focus,
          "Listen icon by snide",
          "https://thenounproject.com/term/listen/317779/"
        )
      )

      addItem(
        buildElement(
          R.drawable.ic_appintro_sleep_timer,
          "Sleeping icon by Koson Rattanaphan, TH",
          "https://thenounproject.com/term/sleeping/3434765"
        )
      )

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
    return buildElement(iconId, title) { it.context.startCustomTab(url) }
  }

  private fun buildElement(
    @DrawableRes iconId: Int,
    title: String,
    clickListener: View.OnClickListener,
  ): Element {
    return Element(title, iconId)
      .setAutoApplyIconTint(true)
      .setOnClickListener(clickListener)
  }

  override fun onExtraClicked(v: View, specialButton: SpecialButton): Boolean {
    return false
  }

  override fun onIconClicked(v: View) {
  }

  override fun onIconLongClicked(v: View): Boolean {
    return false
  }

  override fun onLibraryAuthorClicked(v: View, library: Library): Boolean {
    return onLibraryContentClicked(v, library)
  }

  override fun onLibraryAuthorLongClicked(v: View, library: Library): Boolean {
    return onLibraryAuthorClicked(v, library)
  }

  override fun onLibraryBottomClicked(v: View, library: Library): Boolean {
    if (library.licenses.size == 1) {
      val url = library.licenses.first().url ?: return false
      context?.startCustomTab(url)
      return true
    }

    return false
  }

  override fun onLibraryBottomLongClicked(v: View, library: Library): Boolean {
    return onLibraryBottomClicked(v, library)
  }

  override fun onLibraryContentClicked(v: View, library: Library): Boolean {
    val website = library.website ?: return false
    context?.startCustomTab(website)
    return true
  }

  override fun onLibraryContentLongClicked(v: View, library: Library): Boolean {
    return onLibraryContentClicked(v, library)
  }
}
