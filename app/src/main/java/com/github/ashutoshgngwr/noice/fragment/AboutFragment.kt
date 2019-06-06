package com.github.ashutoshgngwr.noice.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.view.ContextThemeWrapper
import androidx.fragment.app.Fragment
import com.github.ashutoshgngwr.noice.R
import mehdi.sakout.aboutpage.AboutPage
import mehdi.sakout.aboutpage.Element

class AboutFragment : Fragment() {

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    // force night mode and use custom theme with correct color values
    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    val context = ContextThemeWrapper(this.context, R.style.AboutTheme)

    return AboutPage(context).run {
      setImage(R.drawable.app_banner)
      setDescription(getString(R.string.app_description))
      addItem(getVersionElement())
      addItem(createElement(R.string.app_copyright, R.drawable.ic_stat_copyright, R.string.app_license_url))
      addWebsite(getString(R.string.app_website))
      addPlayStore(getString(R.string.app_playstore))
      addGitHub(getString(R.string.app_github))
      addGroup(getString(R.string.connect_with_author))
      addEmail(getString(R.string.author_email), getString(R.string.about__email))
      addGitHub(getString(R.string.author_social_handle), getString(R.string.about__github))
      addTwitter(getString(R.string.author_social_handle), getString(R.string.about__twitter))
      addInstagram(getString(R.string.author_social_handle), getString(R.string.about__instagram))
      addGroup(getString(R.string.credits))
      addItem(
        createElement(
          R.string.credits__app_icon,
          R.drawable.ic_stat_media_player,
          R.string.credits__app_icon_url
        )
      )

      addItem(
        createElement(
          R.string.credits__sound_birds,
          R.drawable.ic_stat_sound,
          R.string.credits__sound_birds_url
        )
      )

      addItem(
        createElement(
          R.string.credits__sound_bonfire,
          R.drawable.ic_stat_sound,
          R.string.credits__sound_bonfire_url
        )
      )

      addItem(
        createElement(
          R.string.credits__sound_coffee_shop,
          R.drawable.ic_stat_sound,
          R.string.credits__sound_coffee_shop_url
        )
      )

      addItem(
        createElement(
          R.string.credits__sound_distant_thunder,
          R.drawable.ic_stat_sound,
          R.string.credits__sound_distant_thunder_url
        )
      )

      addItem(
        createElement(
          R.string.credits__sound_heavy_rain,
          R.drawable.ic_stat_sound,
          R.string.credits__sound_heavy_rain_url
        )
      )

      addItem(
        createElement(
          R.string.credits__sound_light_rain,
          R.drawable.ic_stat_sound,
          R.string.credits__sound_light_rain_url
        )
      )

      addItem(
        createElement(
          R.string.credits__sound_moderate_rain,
          R.drawable.ic_stat_sound,
          R.string.credits__sound_moderate_rain_url
        )
      )

      addItem(
        createElement(
          R.string.credits__sound_moving_train,
          R.drawable.ic_stat_sound,
          R.string.credits__sound_moving_train_url
        )
      )

      addItem(
        createElement(
          R.string.credits__sound_night,
          R.drawable.ic_stat_sound,
          R.string.credits__sound_night_url
        )
      )

      addItem(
        createElement(
          R.string.credits__sound_rolling_thunder,
          R.drawable.ic_stat_sound,
          R.string.credits__sound_rolling_thunder_url
        )
      )

      addItem(
        createElement(
          R.string.credits__sound_seaside,
          R.drawable.ic_stat_sound,
          R.string.credits__sound_seaside_url
        )
      )

      addItem(
        createElement(
          R.string.credits__sound_soft_wind,
          R.drawable.ic_stat_sound,
          R.string.credits__sound_soft_wind_url
        )
      )

      addItem(
        createElement(
          R.string.credits__sound_thunder_crack,
          R.drawable.ic_stat_sound,
          R.string.credits__sound_thunder_crack_url
        )
      )

      addItem(
        createElement(
          R.string.credits__sound_train_horn,
          R.drawable.ic_stat_sound,
          R.string.credits__sound_train_horn_url
        )
      )

      addItem(
        createElement(
          R.string.credits__sound_water_stream,
          R.drawable.ic_stat_sound,
          R.string.credits__sound_water_stream_url
        )
      )

      addItem(
        createElement(
          R.string.credits__sound_wind_chimes_of_shells,
          R.drawable.ic_stat_sound,
          R.string.credits__sound_wind_chimes_of_shells_url
        )
      )

      addItem(
        createElement(
          R.string.credits__sound_wind_in_palm_trees,
          R.drawable.ic_stat_sound,
          R.string.credits__sound_wind_in_palm_trees_url
        )
      )

      create()
    }
  }

  private fun getVersionElement(): Element {
    val version = context?.packageManager?.getPackageInfo(context?.packageName, 0)?.versionName
    return Element("v$version", R.drawable.ic_stat_version)
  }

  private fun createElement(titleResId: Int, iconId: Int, urlResId: Int): Element {
    return Element(getString(titleResId), iconId)
      .setOnClickListener {
        context?.startActivity(
          Intent(
            Intent.ACTION_VIEW,
            Uri.parse(getString(urlResId))
          )
        )
      }
  }
}
