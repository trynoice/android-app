package com.github.ashutoshgngwr.noice.fragment

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.os.bundleOf
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.github.ashutoshgngwr.noice.BuildConfig
import com.github.ashutoshgngwr.noice.NoiceApplication
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.CrashlyticsProvider
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import com.google.android.material.snackbar.Snackbar
import com.google.gson.JsonIOException
import com.google.gson.JsonSyntaxException
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

class SettingsFragment : PreferenceFragmentCompat() {

  companion object {
    private val TAG = SettingsFragment::class.simpleName
  }

  private lateinit var settingsRepository: SettingsRepository
  private lateinit var presetRepository: PresetRepository
  private lateinit var crashlyticsProvider: CrashlyticsProvider
  private lateinit var analyticsProvider: AnalyticsProvider

  private val createDocumentActivityLauncher = registerForActivityResult(
    ActivityResultContracts.CreateDocument(),
    this::onCreateDocumentResult
  )

  private val openDocumentActivityLauncher = registerForActivityResult(
    ActivityResultContracts.OpenDocument(),
    this::onOpenDocumentResult
  )

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.settings, rootKey)
    settingsRepository = SettingsRepository.newInstance(requireContext())
    presetRepository = PresetRepository.newInstance(requireContext())
    val app = NoiceApplication.of(requireContext())
    crashlyticsProvider = app.getCrashlyticsProvider()
    analyticsProvider = app.getAnalyticsProvider()

    findPreference<Preference>(R.string.export_presets_key).setOnPreferenceClickListener {
      createDocumentActivityLauncher.launch("noice-saved-presets.json")
      analyticsProvider.logEvent("presets_export_begin", bundleOf())
      true
    }

    findPreference<Preference>(R.string.import_presets_key).setOnPreferenceClickListener {
      openDocumentActivityLauncher.launch(arrayOf("application/json"))
      analyticsProvider.logEvent("presets_import_begin", bundleOf())
      true
    }

    findPreference<Preference>(R.string.remove_all_app_shortcuts_key).setOnPreferenceClickListener {
      DialogFragment.show(childFragmentManager) {
        title(R.string.remove_all_app_shortcuts)
        message(R.string.remove_all_app_shortcuts_confirmation)
        negativeButton(R.string.cancel)
        positiveButton(R.string.okay) {
          ShortcutManagerCompat.removeAllDynamicShortcuts(requireContext())
          showSnackBar(R.string.all_app_shortcuts_removed)
          analyticsProvider.logEvent("preset_shortcut_remove_all", bundleOf())
        }
      }

      true
    }

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
              analyticsProvider.logEvent("theme_set", bundleOf("theme" to theme))
            }
          )
          negativeButton(R.string.cancel)
        }

        true
      }
    }

    findPreference<PreferenceCategory>(R.string.others_key)
      .isVisible = BuildConfig.IS_PLAY_STORE_BUILD

    findPreference<SwitchPreferenceCompat>(R.string.should_share_usage_data_key)
      .setOnPreferenceChangeListener { _, checked ->
        if (checked is Boolean) {
          crashlyticsProvider.setCollectionEnabled(checked)
          analyticsProvider.setCollectionEnabled(checked)
        }

        true
      }

    analyticsProvider.setCurrentScreen("settings", SettingsFragment::class)
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  internal fun onCreateDocumentResult(result: Uri?) {
    var success = false
    try {
      if (result == null) { // inside try to force run finally block.
        return
      }

      requireContext().contentResolver.openFileDescriptor(result, "w")?.use {
        presetRepository.writeTo(FileOutputStream(it.fileDescriptor))
      }

      success = true
    } catch (e: Throwable) {
      Log.w(TAG, "failed to export saved presets", e)
      when (e) {
        is FileNotFoundException,
        is IOException,
        is JsonIOException -> {
          showSnackBar(R.string.failed_to_write_file)
          crashlyticsProvider.apply {
            log("failed to export saved presets")
            recordException(e)
          }
        }
        else -> throw e
      }
    } finally {
      analyticsProvider.logEvent("presets_export_complete", bundleOf("success" to success))
    }
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  internal fun onOpenDocumentResult(result: Uri?) {
    var success = false
    try {
      if (result == null) { // inside try to force run finally block.
        return
      }

      requireContext().contentResolver.openFileDescriptor(result, "r")?.use {
        presetRepository.readFrom(FileInputStream(it.fileDescriptor))
      }

      success = true
    } catch (e: Throwable) {
      Log.i(TAG, e.stackTraceToString())
      when (e) {
        is FileNotFoundException,
        is IOException,
        is JsonIOException -> {
          showSnackBar(R.string.failed_to_read_file)
          crashlyticsProvider.apply {
            log("failed to import saved presets")
            recordException(e)
          }
        }
        is JsonSyntaxException,
        is IllegalArgumentException -> showSnackBar(R.string.invalid_import_file_format)
        else -> throw e
      }
    } finally {
      analyticsProvider.logEvent("presets_import_complete", bundleOf("success" to success))
    }
  }

  private fun <T : Preference> findPreference(@StringRes keyResID: Int): T {
    return findPreference(getString(keyResID))
      ?: throw IllegalArgumentException("preference key not found")
  }

  private fun showSnackBar(@StringRes message: Int) {
    Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show()
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
