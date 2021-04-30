package com.github.ashutoshgngwr.noice.fragment

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.FontRes
import com.github.appintro.AppIntroBaseFragment
import com.github.appintro.model.SliderPage
import com.github.ashutoshgngwr.noice.R

class AppIntroFragment(override val layoutId: Int) : AppIntroBaseFragment() {

  companion object {
    /**
     * Generates a new instance for [AppIntroFragment]
     *
     * @param title CharSequence which will be the slide title
     * @param description CharSequence which will be the slide description
     * @param imageDrawable @DrawableRes (Integer) the image that will be
     *                             displayed, obtained from Resources
     * @param backgroundColor @ColorInt (Integer) custom background color
     * @param titleColor @ColorInt (Integer) custom title color
     * @param descriptionColor @ColorInt (Integer) custom description color
     * @param titleTypefaceFontRes @FontRes (Integer) custom title typeface obtained
     *                             from Resources
     * @param descriptionTypefaceFontRes @FontRes (Integer) custom description typeface obtained
     *                             from Resources
     * @param backgroundDrawable @DrawableRes (Integer) custom background drawable
     *
     * @return An [AppIntroFragment] created instance
     */
    @JvmOverloads
    @JvmStatic
    fun newInstance(
      layoutId: Int = R.layout.app_intro_fragment,
      title: CharSequence? = null,
      description: CharSequence? = null,
      @DrawableRes imageDrawable: Int = 0,
      @ColorInt backgroundColor: Int = 0,
      @ColorInt titleColor: Int = 0,
      @ColorInt descriptionColor: Int = 0,
      @FontRes titleTypefaceFontRes: Int = 0,
      @FontRes descriptionTypefaceFontRes: Int = 0,
      @DrawableRes backgroundDrawable: Int = 0
    ): AppIntroFragment {
      return newInstance(
        layoutId,
        SliderPage(
          title = title,
          description = description,
          imageDrawable = imageDrawable,
          backgroundColor = backgroundColor,
          titleColor = titleColor,
          descriptionColor = descriptionColor,
          titleTypefaceFontRes = titleTypefaceFontRes,
          descriptionTypefaceFontRes = descriptionTypefaceFontRes,
          backgroundDrawable = backgroundDrawable
        )
      )
    }

    /**
     * Generates an [AppIntroFragment] from a given [SliderPage]
     *
     * @param sliderPage the [SliderPage] object which contains all attributes for
     * the current slide
     *
     * @return An [AppIntroFragment] created instance
     */
    private fun newInstance(layoutId: Int, sliderPage: SliderPage): AppIntroFragment {
      val slide = AppIntroFragment(layoutId)
      slide.arguments = sliderPage.toBundle()
      return slide
    }
  }
}
