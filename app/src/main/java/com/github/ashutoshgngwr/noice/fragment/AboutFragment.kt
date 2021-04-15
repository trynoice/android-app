package com.github.ashutoshgngwr.noice.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.github.ashutoshgngwr.noice.BuildConfig
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.sound.Sound
import mehdi.sakout.aboutpage.AboutPage
import mehdi.sakout.aboutpage.Element

class AboutFragment : Fragment() {

  companion object {
    private val OTHER_CREDITS = arrayOf(
      arrayOf(
        R.string.credits__app_icon,
        R.drawable.ic_launcher_24dp,
        R.string.credits__app_icon_url
      ),
      arrayOf(
        R.string.credits__support_development_icon,
        R.drawable.ic_nav_support_development,
        R.string.credits__support_development_icon_url
      ),
      arrayOf(
        R.string.credits__appintro_sound_library_icon,
        R.drawable.ic_appintro_sound_library,
        R.string.credits__appintro_sound_library_icon_url
      ),
      arrayOf(
        R.string.credits__appintro_preset_icon,
        R.drawable.ic_appintro_preset,
        R.string.credits__appintro_preset_icon_url
      ),
      arrayOf(
        R.string.credits__appintro_sleep_timer_icon,
        R.drawable.ic_appintro_sleep_timer,
        R.string.credits__appintro_sleep_timer_icon_url
      ),
      arrayOf(
        R.string.credits__appintro_chromecast_icon,
        R.drawable.ic_appintro_chromecast,
        R.string.credits__appintro_chromecast_icon_url
      )
    )
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
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
      addWebsite(getString(R.string.app_website))
      @Suppress("ConstantConditionIf")
      if (BuildConfig.IS_PLAY_STORE_BUILD) {
        addPlayStore(requireContext().packageName)
      }

      addGitHub(getString(R.string.app_github))
      addGroup(getString(R.string.connect_with_author))
      addEmail(getString(R.string.author_email), getString(R.string.about__email))
      addGitHub(getString(R.string.author_social_handle), getString(R.string.about__github))
      addTwitter(getString(R.string.author_social_handle), getString(R.string.about__twitter))
      addGroup(getString(R.string.credits))
      for (item in OTHER_CREDITS) {
        addItem(createElement(item[0], item[1], item[2]))
      }

      for (sound in Sound.LIBRARY.values) {
        for ((titleResID, urlResID) in sound.credits) {
          addItem(createElement(titleResID, R.drawable.ic_about_sound, urlResID))
        }
      }

      create()
    }
  }

  private fun getVersionElement(): Element {
    val version =
      requireContext().packageManager?.getPackageInfo(requireContext().packageName, 0)?.versionName
    return Element("v$version", R.drawable.ic_about_version)
  }

  private fun createElement(titleResId: Int, iconId: Int, urlResId: Int): Element {
    return Element(getString(titleResId), iconId)
      .setOnClickListener {
        requireContext().startActivity(
          Intent(
            Intent.ACTION_VIEW,
            Uri.parse(getString(urlResId))
          )
        )
      }
  }
}
