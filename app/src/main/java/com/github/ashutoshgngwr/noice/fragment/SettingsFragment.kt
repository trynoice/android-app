package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.repository.SettingsRepository

class SettingsFragment : PreferenceFragmentCompat() {

  private lateinit var settingsRepository: SettingsRepository

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.settings, rootKey)
    settingsRepository = SettingsRepository.newInstance(requireContext())

    with(findPreference<Preference>(R.string.app_theme_key)) {
      summary = getAppThemeString()
      setOnPreferenceClickListener {
        DialogFragment.show(childFragmentManager) {
          title(R.string.app_theme)
          singleChoiceItems(
            items = resources.getStringArray(R.array.app_themes),
            currentChoice = settingsRepository.getAppTheme(),
            onItemSelected = { theme ->
              settingsRepository.setAppTheme(theme)
              summary = getAppThemeString()
              requireActivity().recreate()
            }
          )
        }

        false
      }
    }
  }

  private fun <T : Preference> findPreference(@StringRes keyResID: Int): T {
    return findPreference(getString(keyResID))
      ?: throw IllegalArgumentException("preference key not found")
  }

  private fun getAppThemeString(): String {
    return getString(
      when (settingsRepository.getAppTheme()) {
        SettingsRepository.APP_THEME_DARK -> R.string.app_theme_dark
        SettingsRepository.APP_THEME_LIGHT -> R.string.app_theme_light
        else -> R.string.app_theme_system_default
      }
    )
  }
}
