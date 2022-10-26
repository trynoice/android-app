package com.github.ashutoshgngwr.noice.fragment

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.AlarmsFragmentBinding
import com.github.ashutoshgngwr.noice.ext.hasSelfPermission
import com.github.ashutoshgngwr.noice.ext.showErrorSnackBar
import com.github.ashutoshgngwr.noice.ext.startAppDetailsSettingsActivity
import com.github.ashutoshgngwr.noice.provider.AlarmProvider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AlarmsFragment : Fragment() {

  private lateinit var binding: AlarmsFragmentBinding

  @set:Inject
  internal lateinit var alarmProvider: AlarmProvider

  private val requestPermissionsLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission(),
    this::onPostNotificationsPermissionRequestResult,
  )

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = AlarmsFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.lifecycleOwner = viewLifecycleOwner
    binding.addAlarmButton.setOnClickListener { startAddAlarmFlow() }
  }

  private fun startAddAlarmFlow() {
    if (!alarmProvider.canScheduleAlarms()) {
      showAlarmPermissionRationale()
      return
    }

    if (!hasPostNotificationsPermission()) {
      requestPostNotificationsPermission()
      return
    }

    // TODO: start the flow to add a new alarm
  }

  private fun showAlarmPermissionRationale() {
    DialogFragment.show(childFragmentManager) {
      title(R.string.alarm_permission_rationale_title)
      message(R.string.alarm_permission_rationale_description)
      negativeButton(R.string.cancel)
      positiveButton(R.string.settings) { requireContext().startAppDetailsSettingsActivity() }
    }
  }

  private fun hasPostNotificationsPermission(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      return requireContext().hasSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
    }

    return true
  }

  private fun requestPostNotificationsPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      return
    }

    if (!shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
      requestPermissionsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      return
    }

    DialogFragment.show(childFragmentManager) {
      title(R.string.post_notifications_permission_rationale_title)
      message(R.string.post_notifications_permission_rationale_description)
      negativeButton(R.string.cancel)
      positiveButton(R.string.proceed) {
        requestPermissionsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }

  private fun onPostNotificationsPermissionRequestResult(granted: Boolean) {
    if (granted) {
      return
    }

    showErrorSnackBar(getString(R.string.alarm_post_notifications_permission_denied))
      .setAction(R.string.settings) { requireContext().startAppDetailsSettingsActivity() }
  }
}
