package com.github.ashutoshgngwr.noice.ext

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.res.ResourcesCompat
import com.github.ashutoshgngwr.noice.R

/**
 * Launches a [CustomTabsIntent] with default application theme ([R.style.Theme_App]) for the given
 * [uri].
 */
fun Context.startCustomTab(uri: String) {
  val tbColor = ResourcesCompat.getColor(resources, R.color.action_bar, theme)
  val colorParams = CustomTabColorSchemeParams.Builder()
    .setToolbarColor(tbColor)
    .setNavigationBarColor(tbColor)
    .build()

  val customTabsIntent = CustomTabsIntent.Builder()
    .setDefaultColorSchemeParams(colorParams)
    .build()

  customTabsIntent.launchUrl(this, Uri.parse(uri))
}

/**
 * Launches a [CustomTabsIntent] with default application theme ([R.style.Theme_App]) for the given
 * [uri string resource][uriStringRes].
 */
fun Context.startCustomTab(@StringRes uriStringRes: Int) {
  startCustomTab(getString(uriStringRes))
}
