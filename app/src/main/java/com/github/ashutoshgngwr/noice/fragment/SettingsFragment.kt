package com.github.ashutoshgngwr.noice.fragment

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.github.ashutoshgngwr.noice.BuildConfig
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.ext.showErrorSnackBar
import com.github.ashutoshgngwr.noice.ext.showSuccessSnackBar
import com.github.ashutoshgngwr.noice.metrics.AnalyticsProvider
import com.github.ashutoshgngwr.noice.metrics.CrashlyticsProvider
import com.github.ashutoshgngwr.noice.models.AudioQuality
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import com.github.ashutoshgngwr.noice.worker.SoundDownloadsRefreshWorker
import com.google.gson.JsonIOException
import com.google.gson.JsonSyntaxException
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : PreferenceFragmentCompat() {

  companion object {
    private val TAG = SettingsFragment::class.simpleName
  }

  @set:Inject
  internal lateinit var settingsRepository: SettingsRepository

  @set:Inject
  internal lateinit var presetRepository: PresetRepository

  @set:Inject
  internal var analyticsProvider: AnalyticsProvider? = null

  @set:Inject
  internal var crashlyticsProvider: CrashlyticsProvider? = null

  private val createDocumentActivityLauncher = registerForActivityResult(
    ActivityResultContracts.CreateDocument("application/json"),
    this::onCreateDocumentResult
  )

  private val openDocumentActivityLauncher = registerForActivityResult(
    ActivityResultContracts.OpenDocument(),
    this::onOpenDocumentResult
  )

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.settings, rootKey)
    findPreference<Preference>(R.string.audio_bitrate_key).apply {
      val entries = arrayOf(
        getString(R.string.audio_quality_low),
        getString(R.string.audio_quality_medium),
        getString(R.string.audio_quality_high),
        getString(R.string.audio_quality_ultra),
      )

      val values = arrayOf(
        AudioQuality.LOW,
        AudioQuality.MEDIUM,
        AudioQuality.HIGH,
        AudioQuality.ULTRA_HIGH,
      )

      summary = entries[values.indexOf(settingsRepository.getAudioQuality())]
      setOnPreferenceClickListener {
        DialogFragment.show(childFragmentManager) {
          title(R.string.audio_quality)
          message(R.string.audio_quality_summary)
          singleChoiceItems(
            items = entries,
            currentChoice = values.indexOf(settingsRepository.getAudioQuality()),
            onItemSelected = { position ->
              settingsRepository.setAudioQuality(values[position])
              summary = entries[position]
              SoundDownloadsRefreshWorker.refreshDownloads(requireContext())
            }
          )
          negativeButton(R.string.cancel)
        }

        true
      }
    }

    findPreference<Preference>(R.string.export_presets_key).setOnPreferenceClickListener {
      createDocumentActivityLauncher.launch("noice-saved-presets.json")
      true
    }

    findPreference<Preference>(R.string.import_presets_key).setOnPreferenceClickListener {
      openDocumentActivityLauncher.launch(arrayOf("application/json"))
      true
    }

    findPreference<Preference>(R.string.remove_all_app_shortcuts_key).setOnPreferenceClickListener {
      DialogFragment.show(childFragmentManager) {
        title(R.string.remove_all_app_shortcuts)
        message(R.string.remove_all_app_shortcuts_confirmation)
        negativeButton(R.string.cancel)
        positiveButton(R.string.delete) {
          ShortcutManagerCompat.removeAllDynamicShortcuts(requireContext())
          showSuccessSnackBar(R.string.all_app_shortcuts_removed)
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
            }
          )
          negativeButton(R.string.cancel)
        }

        true
      }
    }

    findPreference<SwitchPreferenceCompat>(R.string.use_material_you_colors_key).apply {
      isVisible = Build.VERSION.SDK_INT >= 31
      setOnPreferenceChangeListener { _, _ -> requireActivity().recreate(); true }
    }

    findPreference<Preference>(R.string.remove_all_sound_downloads_key).setOnPreferenceClickListener {
      DialogFragment.show(childFragmentManager) {
        title(R.string.remove_all_sound_downloads)
        message(R.string.remove_all_sound_downloads_confirmation)
        negativeButton(R.string.cancel)
        positiveButton(R.string.delete) {
          SoundDownloadsRefreshWorker.removeAllSoundDownloads(requireContext())
          showSuccessSnackBar(R.string.sound_downloads_scheduled_for_removal)
        }
      }

      true
    }

    findPreference<SwitchPreferenceCompat>(R.string.should_share_usage_data_key)
      .also { it.isVisible = !BuildConfig.IS_FREE_BUILD }
      .setOnPreferenceChangeListener { _, checked ->
        if (checked is Boolean) {
          settingsRepository.setShouldShareUsageData(checked)
        }

        true
      }

    analyticsProvider?.setCurrentScreen(this::class)
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  internal fun onCreateDocumentResult(result: Uri?) = viewLifecycleOwner.lifecycleScope.launch {
    try {
      if (result == null) { // inside try to force run finally block.
        return@launch
      }

      requireContext().contentResolver.openFileDescriptor(result, "w")?.use {
        val os = FileOutputStream(it.fileDescriptor)
        os.channel.truncate(0L)
        presetRepository.exportTo(os)
        os.close()
      }

      showSuccessSnackBar(R.string.export_presets_successful)
    } catch (e: Throwable) {
      Log.w(TAG, "failed to export saved presets", e)
      crashlyticsProvider?.log("failed to export saved presets")
      crashlyticsProvider?.recordException(e)
      when (e) {
        is FileNotFoundException,
        is IOException,
        is JsonIOException -> showErrorSnackBar(R.string.failed_to_write_file)

        else -> throw e
      }
    }
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  internal fun onOpenDocumentResult(result: Uri?) = viewLifecycleOwner.lifecycleScope.launch {
    try {
      if (result == null) { // inside try to force run finally block.
        return@launch
      }

      requireContext().contentResolver.openFileDescriptor(result, "r")?.use {
        presetRepository.importFrom(FileInputStream(it.fileDescriptor))
      }

      showSuccessSnackBar(R.string.import_presets_successful)
    } catch (e: Throwable) {
      Log.w(TAG, "failed to import saved presets", e)
      when (e) {
        is FileNotFoundException,
        is IOException,
        is JsonIOException -> {
          showErrorSnackBar(R.string.failed_to_read_file)
          crashlyticsProvider?.log("failed to import saved presets")
          crashlyticsProvider?.recordException(e)
        }

        is JsonSyntaxException,
        is IllegalArgumentException -> showErrorSnackBar(R.string.invalid_import_file_format)

        else -> {
          crashlyticsProvider?.log("failed to import saved presets")
          crashlyticsProvider?.recordException(e)
          throw e
        }
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
