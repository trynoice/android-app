package com.github.ashutoshgngwr.noice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.VisibleForTesting
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.github.appintro.AppIntro
import com.github.appintro.AppIntroFragment
import com.github.appintro.AppIntroPageTransformerType

class AppIntroActivity : AppIntro() {

  companion object {
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val PREF_HAS_USER_SEEN_APP_INTRO = "has_user_seen_app_intro"

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

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    isColorTransitionsEnabled = true
    showStatusBar(true)
    setStatusBarColor(ActivityCompat.getColor(this, R.color.colorStatusBar))
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
        title = getString(R.string.appintro__sound_library_title),
        description = getString(R.string.appintro__sound_library_desc),
        imageDrawable = R.drawable.ic_appintro_sound_library,
        titleColor = ActivityCompat.getColor(this, R.color.appintro__text_color),
        descriptionColor = ActivityCompat.getColor(this, R.color.appintro__text_color),
        backgroundColor = ActivityCompat.getColor(this, R.color.appintro_slide_1__background)
      )
    )
    addSlide(
      AppIntroFragment.newInstance(
        title = getString(R.string.appintro__preset_title),
        description = getString(R.string.appintro__preset_desc),
        imageDrawable = R.drawable.ic_appintro_preset,
        titleColor = ActivityCompat.getColor(this, R.color.appintro__text_color),
        descriptionColor = ActivityCompat.getColor(this, R.color.appintro__text_color),
        backgroundColor = ActivityCompat.getColor(this, R.color.appintro_slide_2__background)
      )
    )
    addSlide(
      AppIntroFragment.newInstance(
        title = getString(R.string.appintro__timer_title),
        description = getString(R.string.appintro__timer_desc),
        imageDrawable = R.drawable.ic_appintro_sleep_timer,
        titleColor = ActivityCompat.getColor(this, R.color.appintro__text_color),
        descriptionColor = ActivityCompat.getColor(this, R.color.appintro__text_color),
        backgroundColor = ActivityCompat.getColor(this, R.color.appintro_slide_3__background)
      )
    )
    addSlide(
      AppIntroFragment.newInstance(
        title = getString(R.string.appintro__chromecast_title),
        description = getString(R.string.appintro__chromecast_desc),
        imageDrawable = R.drawable.ic_appintro_chromecast,
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
  }

  // make onSkipPressed and onDonePressed public so that these can be called directly from the
  // unit tests. Without it, the tests need to rely on (not so) complex ViewActions. This move is
  // an attempt to fix tests breaking in the CI environment but running smooth on my local setup.
  // https://github.com/ashutoshgngwr/noice/issues/320

  public override fun onSkipPressed(currentFragment: Fragment?) {
    super.onSkipPressed(currentFragment)
    markSeenInPrefsAndFinish()
  }

  public override fun onDonePressed(currentFragment: Fragment?) {
    super.onDonePressed(currentFragment)
    markSeenInPrefsAndFinish()
  }

  private fun markSeenInPrefsAndFinish() {
    PreferenceManager.getDefaultSharedPreferences(this).edit {
      putBoolean(PREF_HAS_USER_SEEN_APP_INTRO, true)
    }

    finish()
  }
}
