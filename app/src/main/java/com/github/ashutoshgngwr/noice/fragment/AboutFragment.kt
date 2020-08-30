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
import mehdi.sakout.aboutpage.AboutPage
import mehdi.sakout.aboutpage.Element

class AboutFragment : Fragment() {

  companion object {
    private val CREDITS = arrayOf(
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
        R.string.credits__sound_airplane_inflight,
        R.drawable.ic_about_sound,
        R.string.credits__sound_airplane_inflight_url
      ),
      arrayOf(
        R.string.credits__sound_airplane_seatbelt_beeps,
        R.drawable.ic_about_sound,
        R.string.credits__sound_airplane_seatbelt_beeps_url
      ),
      arrayOf(
        R.string.credits__sound_birds,
        R.drawable.ic_about_sound,
        R.string.credits__sound_birds_url
      ),
      arrayOf(
        R.string.credits__sound_bonfire,
        R.drawable.ic_about_sound,
        R.string.credits__sound_bonfire_url
      ),
      arrayOf(
        R.string.credits__sound_coffee_shop,
        R.drawable.ic_about_sound,
        R.string.credits__sound_coffee_shop_url
      ),
      arrayOf(
        R.string.credits__sound_crickets,
        R.drawable.ic_about_sound,
        R.string.credits__sound_crickets_url
      ),
      arrayOf(
        R.string.credits__sound_creaking_ship_0,
        R.drawable.ic_about_sound,
        R.string.credits__sound_creaking_ship_0_url
      ),
      arrayOf(
        R.string.credits__sound_creaking_ship_1,
        R.drawable.ic_about_sound,
        R.string.credits__sound_creaking_ship_1_url
      ),
      arrayOf(
        R.string.credits__sound_distant_thunder,
        R.drawable.ic_about_sound,
        R.string.credits__sound_distant_thunder_url
      ),
      arrayOf(
        R.string.credits__sound_electric_car,
        R.drawable.ic_about_sound,
        R.string.credits__sound_electric_car_url
      ),
      arrayOf(
        R.string.credits__sound_heavy_rain,
        R.drawable.ic_about_sound,
        R.string.credits__sound_heavy_rain_url
      ),
      arrayOf(
        R.string.credits__sound_light_rain,
        R.drawable.ic_about_sound,
        R.string.credits__sound_light_rain_url
      ),
      arrayOf(
        R.string.credits__sound_moderate_rain,
        R.drawable.ic_about_sound,
        R.string.credits__sound_moderate_rain_url
      ),
      arrayOf(
        R.string.credits__morning_in_a_village,
        R.drawable.ic_about_sound,
        R.string.credits__morning_in_a_village_url
      ),
      arrayOf(
        R.string.credits__sound_moving_train,
        R.drawable.ic_about_sound,
        R.string.credits__sound_moving_train_url
      ),
      arrayOf(
        R.string.credits__sound_night,
        R.drawable.ic_about_sound,
        R.string.credits__sound_night_url
      ),
      arrayOf(
        R.string.credits__sound_office,
        R.drawable.ic_about_sound,
        R.string.credits__sound_office_url
      ),
      arrayOf(
        R.string.credits__sound_rolling_thunder,
        R.drawable.ic_about_sound,
        R.string.credits__sound_rolling_thunder_url
      ),
      arrayOf(
        R.string.credits__sound_seaside,
        R.drawable.ic_about_sound,
        R.string.credits__sound_seaside_url
      ),
      arrayOf(
        R.string.credits__sound_soft_wind,
        R.drawable.ic_about_sound,
        R.string.credits__sound_soft_wind_url
      ),
      arrayOf(
        R.string.credits__sound_thunder_crack,
        R.drawable.ic_about_sound,
        R.string.credits__sound_thunder_crack_url
      ),
      arrayOf(
        R.string.credits__sound_train_horn,
        R.drawable.ic_about_sound,
        R.string.credits__sound_train_horn_url
      ),
      arrayOf(
        R.string.credits__sound_walking_through_the_snow,
        R.drawable.ic_about_sound,
        R.string.credits__sound_walking_through_the_snow_url
      ),
      arrayOf(
        R.string.credits__sound_water_hose,
        R.drawable.ic_about_sound,
        R.string.credits__sound_water_hose_url
      ),
      arrayOf(
        R.string.credits__sound_water_stream,
        R.drawable.ic_about_sound,
        R.string.credits__sound_water_stream_url
      ),
      arrayOf(
        R.string.credits__sound_wind_chimes_of_shells,
        R.drawable.ic_about_sound,
        R.string.credits__sound_wind_chimes_of_shells_url
      ),
      arrayOf(
        R.string.credits__sound_wind_in_palm_trees,
        R.drawable.ic_about_sound,
        R.string.credits__sound_wind_in_palm_trees_url
      )
    )

    val TRANSLATIONS = arrayOf(
      arrayOf(
        R.string.credits__translation_es_ar_0,
        R.string.credits__translation_es_ar_0__url
      ),
      arrayOf(
        R.string.credits__translation_ru_0,
        R.string.credits__translation_ru_0__url
      ),
      arrayOf(
        R.string.credits__translation_de_0,
        R.string.credits__translation_de_0__url
      ),
      arrayOf(
        R.string.credits__translation_it_it_0,
        R.string.credits__translation_it_it_0__url
      ),
      arrayOf(
        R.string.credits__translation_fr_fr_0,
        R.string.credits__translation_fr_fr_0__url
      ),
      arrayOf(
        R.string.credits__translation_pt_br_0,
        R.string.credits__translation_pt_br_0__url
      ),
      arrayOf(
        R.string.credits__translation_es_es_0,
        R.string.credits__translation_es_es_0__url
      ),
      arrayOf(
        R.string.credits__translation_sv_0,
        R.string.credits__translation_sv_0__url
      ),
      arrayOf(
        R.string.credits__translation_sq_al_0,
        R.string.credits__translation_sq_al_0__url
      ),
      arrayOf(
        R.string.credits__translation_nl_0,
        R.string.credits__translation_nl_0__url
      ),
      arrayOf(
        R.string.credits__translation_cs_0,
        R.string.credits__translation_cs_0__url
      ),
      arrayOf(
        R.string.credits__translation_pl_0,
        R.string.credits__translation_pl_0__url
      ),
      arrayOf(
        R.string.credits__translation_de_1,
        R.string.credits__translation_de_1__url
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
      addGroup(getString(R.string.about__translations))
      for (item in TRANSLATIONS) {
        addItem(createElement(item[0], R.drawable.ic_about_translation, item[1]))
      }

      addGroup(getString(R.string.credits))
      for (item in CREDITS) {
        addItem(createElement(item[0], item[1], item[2]))
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
