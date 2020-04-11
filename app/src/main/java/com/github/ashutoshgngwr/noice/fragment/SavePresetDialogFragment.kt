package com.github.ashutoshgngwr.noice.fragment

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import com.github.ashutoshgngwr.noice.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.android.synthetic.main.dialog_save_preset.view.*

class SavePresetDialogFragment : BottomSheetDialogFragment() {

  var preset: PresetFragment.Preset? = null

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    // see https://github.com/material-components/material-components-android/issues/99
    val ctxWrapper = ContextThemeWrapper(requireContext(), R.style.AppTheme)
    return inflater
      .cloneInContext(ctxWrapper)
      .inflate(R.layout.dialog_save_preset, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    view.button_cancel.setOnClickListener {
      dismiss()
    }

    view.button_save.setOnClickListener {
      val name = view.edit_name.text.toString()
      when {
        name.isBlank() -> {
          view.layout_edit_name.error = getString(R.string.preset_name_cannot_be_empty)
        }
        preset == null -> {
          requireNotNull(targetFragment).onActivityResult(targetRequestCode, RESULT_CANCELED, null)
          dismiss()
        }
        else -> {
          requireNotNull(preset).name = name
          PresetFragment.Preset.appendToUserPreferences(requireContext(), requireNotNull(preset))
          requireNotNull(targetFragment).onActivityResult(targetRequestCode, RESULT_OK, null)
          dismiss()
        }
      }
    }
  }
}
