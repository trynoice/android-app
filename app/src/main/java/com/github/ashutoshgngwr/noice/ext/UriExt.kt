package com.github.ashutoshgngwr.noice.ext

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.res.ResourcesCompat
import com.github.ashutoshgngwr.noice.R


/**
 * Launches the receiver [Uri] using an app themed [CustomTabsIntent].
 */
fun Uri.launchInCustomTab(context: Context) {
  val tbColor = ResourcesCompat.getColor(context.resources, R.color.action_bar, context.theme)
  val colorParams = CustomTabColorSchemeParams.Builder()
    .setToolbarColor(tbColor)
    .setNavigationBarColor(tbColor)
    .build()

  val customTabsIntent = CustomTabsIntent.Builder()
    .setDefaultColorSchemeParams(colorParams)
    .build()

  customTabsIntent.launchUrl(context, this)
}
