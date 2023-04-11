package com.github.ashutoshgngwr.noice.activity

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.annotation.VisibleForTesting
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.github.appintro.AppIntro
import com.github.appintro.AppIntroPageTransformerType
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.fragment.AppIntroFragment
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AppIntroActivity : AppIntro() {

  companion object {
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal const val PREF_HAS_USER_SEEN_APP_INTRO = "has_user_seen_app_intro"

    /**
     * [maybeStart] displays the [AppIntroActivity] if user hasn't seen it before.
     */
    fun maybeStart(context: Context) {
      PreferenceManager.getDefaultSharedPreferences(context).also {
        if (!it.getBoolean(PREF_HAS_USER_SEEN_APP_INTRO, false)) {
          context.startActivity(Intent(context, AppIntroActivity::class.java))
        }
      }
    }
  }

  @set:Inject
  internal lateinit var analyticsProvider: AnalyticsProvider

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    isColorTransitionsEnabled = true
    showStatusBar(true)

    setStatusBarColor(Color.TRANSPARENT)
    setTransformer(
      AppIntroPageTransformerType.Parallax(
        titleParallaxFactor = 1.0,
        imageParallaxFactor = -1.0,
        descriptionParallaxFactor = 2.0
      )
    )
    addSlide(
      AppIntroFragment.newInstance(
        title = getString(R.string.appintro__getting_started_title),
        description = "${getString(R.string.app_description)}\n\n${getString(R.string.appintro__getting_started_desc_0)}",
        imageDrawable = R.drawable.app_banner,
        titleColor = ActivityCompat.getColor(this, R.color.appintro__text_color),
        descriptionColor = ActivityCompat.getColor(this, R.color.appintro__text_color),
        backgroundColor = ActivityCompat.getColor(this, R.color.appintro_slide_0__background)
      )
    )
    addSlide(
      AppIntroFragment.newInstance(
        title = getString(R.string.appintro__library_title),
        description = getString(R.string.appintro__library_desc),
        imageDrawable = R.drawable.ecosystem_200,
        titleColor = ActivityCompat.getColor(this, R.color.appintro__text_color),
        descriptionColor = ActivityCompat.getColor(this, R.color.appintro__text_color),
        backgroundColor = ActivityCompat.getColor(this, R.color.appintro_slide_1__background)
      )
    )
    addSlide(
      AppIntroFragment.newInstance(
        title = getString(R.string.appintro__presets_title),
        description = getString(R.string.appintro__presets_desc),
        imageDrawable = R.drawable.equalizer_200,
        titleColor = ActivityCompat.getColor(this, R.color.appintro__text_color),
        descriptionColor = ActivityCompat.getColor(this, R.color.appintro__text_color),
        backgroundColor = ActivityCompat.getColor(this, R.color.appintro_slide_2__background)
      )
    )
    addSlide(
      AppIntroFragment.newInstance(
        title = getString(R.string.appintro__timer_title),
        description = getString(R.string.appintro__timer_desc),
        imageDrawable = R.drawable.sleeping_200,
        titleColor = ActivityCompat.getColor(this, R.color.appintro__text_color),
        descriptionColor = ActivityCompat.getColor(this, R.color.appintro__text_color),
        backgroundColor = ActivityCompat.getColor(this, R.color.appintro_slide_3__background)
      )
    )
    addSlide(
      AppIntroFragment.newInstance(
        title = getString(R.string.appintro__chromecast_title),
        description = getString(R.string.appintro__chromecast_desc),
        imageDrawable = R.drawable.round_cast_128,
        titleColor = ActivityCompat.getColor(this, R.color.appintro__text_color),
        descriptionColor = ActivityCompat.getColor(this, R.color.appintro__text_color),
        backgroundColor = ActivityCompat.getColor(this, R.color.appintro_slide_4__background)
      )
    )
    addSlide(
      AppIntroFragment.newInstance(
        title = getString(R.string.appintro__getting_started_title),
        description = getString(R.string.appintro__getting_started_desc_1),
        imageDrawable = R.drawable.app_banner,
        titleColor = ActivityCompat.getColor(this, R.color.appintro__text_color),
        descriptionColor = ActivityCompat.getColor(this, R.color.appintro__text_color),
        backgroundColor = ActivityCompat.getColor(this, R.color.appintro_slide_5__background)
      )
    )

    analyticsProvider.setCurrentScreen("app_intro", AppIntroActivity::class)
  }

  override fun onSkipPressed(currentFragment: Fragment?) {
    markSeenInPrefsAndFinish(true)
  }

  override fun onDonePressed(currentFragment: Fragment?) {
    markSeenInPrefsAndFinish(false)
  }

  private fun markSeenInPrefsAndFinish(isSkipped: Boolean) {
    PreferenceManager.getDefaultSharedPreferences(this).edit {
      putBoolean(PREF_HAS_USER_SEEN_APP_INTRO, true)
    }

    finish()
    analyticsProvider.logEvent("app_intro_complete", bundleOf("success" to !isSkipped))
  }
}
