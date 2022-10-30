package com.github.ashutoshgngwr.noice.fragment

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.PagingDataAdapter
import androidx.paging.cachedIn
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.recyclerview.widget.SimpleItemAnimator
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.AlarmItemBinding
import com.github.ashutoshgngwr.noice.databinding.AlarmsFragmentBinding
import com.github.ashutoshgngwr.noice.ext.hasSelfPermission
import com.github.ashutoshgngwr.noice.ext.showErrorSnackBar
import com.github.ashutoshgngwr.noice.ext.showTimePicker
import com.github.ashutoshgngwr.noice.ext.startAppDetailsSettingsActivity
import com.github.ashutoshgngwr.noice.models.Alarm
import com.github.ashutoshgngwr.noice.repository.AlarmRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class AlarmsFragment : Fragment(), AlarmItemViewController {

  private lateinit var binding: AlarmsFragmentBinding

  private val viewModel: AlarmsViewModel by viewModels()
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

    val adapter = AlarmListAdapter(layoutInflater, this)
    adapter.addLoadStateListener { loadStates ->
      binding.emptyListIndicator.isVisible =
        loadStates.append.endOfPaginationReached && adapter.itemCount < 1
    }

    binding.list.adapter = adapter

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.alarmsPagingData.collect { pagingData ->
        // prevent flicker when an item updates by temporarily disabling animations.
        val animator = binding.list.itemAnimator as? SimpleItemAnimator
        animator?.supportsChangeAnimations = false
        adapter.submitData(pagingData)
        animator?.supportsChangeAnimations = true
      }
    }
  }

  private fun startAddAlarmFlow() {
    if (!viewModel.canScheduleAlarms()) {
      showAlarmPermissionRationale()
      return
    }

    if (!hasPostNotificationsPermission()) {
      requestPostNotificationsPermission()
      return
    }

    showTimePicker { hour, minute -> viewModel.create(hour, minute) }
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

  override fun onAlarmLabelClicked(alarm: Alarm) {
    Log.d("AlarmsFragment", "onAlarmLabelClicked:")
  }

  override fun onAlarmTimeClicked(alarm: Alarm) {
    Log.d("AlarmsFragment", "onAlarmTimeClicked:")
  }

  override fun onAlarmToggled(alarm: Alarm, enabled: Boolean) {
    Log.d("AlarmsFragment", "onAlarmToggled: enabled=$enabled")
  }

  override fun onAlarmWeeklyScheduleChanged(alarm: Alarm, newWeeklySchedule: Int) {
    Log.d("AlarmsFragment", "onAlarmWeeklyScheduleChanged: schedule=$newWeeklySchedule")
  }

  override fun onAlarmPresetClicked(alarm: Alarm) {
    Log.d("AlarmsFragment", "onAlarmPresetClicked:")
  }

  override fun onAlarmVibrationToggled(alarm: Alarm, vibrate: Boolean) {
    Log.d("AlarmsFragment", "onAlarmVibrationToggled: vibrate=$vibrate")
  }

  override fun onAlarmDeleteClicked(alarm: Alarm) {
    Log.d("AlarmsFragment", "onAlarmDeleteClicked:")
  }
}

@HiltViewModel
class AlarmsViewModel @Inject constructor(
  private val alarmRepository: AlarmRepository,
) : ViewModel() {

  internal val alarmsPagingData: StateFlow<PagingData<Alarm>> = alarmRepository.list()
    .cachedIn(viewModelScope)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), PagingData.empty())

  internal fun canScheduleAlarms(): Boolean {
    return alarmRepository.canScheduleAlarms()
  }

  internal fun create(hour: Int, minute: Int) {
    viewModelScope.launch(Dispatchers.IO) {
      alarmRepository.create(hour * 60 + minute)
    }
  }
}

class AlarmListAdapter(
  private val layoutInflater: LayoutInflater,
  private val itemViewController: AlarmItemViewController,
) : PagingDataAdapter<Alarm, AlarmViewHolder>(AlarmComparator) {

  private var expandedPosition = -1

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
    val binding = AlarmItemBinding.inflate(layoutInflater, parent, false)
    return AlarmViewHolder(binding, itemViewController)
  }

  override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
    val alarm = getItem(position) ?: return
    holder.bind(alarm, expandedPosition == position)
    holder.binding.expandToggle.setOnClickListener {
      val previousExpandedPosition = expandedPosition
      expandedPosition = if (expandedPosition == position) -1 else position
      if (expandedPosition > -1) notifyItemChanged(expandedPosition)
      if (previousExpandedPosition > -1) notifyItemChanged(previousExpandedPosition)
    }
  }
}

class AlarmViewHolder(
  val binding: AlarmItemBinding,
  private val controller: AlarmItemViewController,
) : ViewHolder(binding.root) {

  private lateinit var alarm: Alarm

  init {
    binding.label.setOnClickListener { controller.onAlarmLabelClicked(alarm) }
    binding.time.setOnClickListener { controller.onAlarmTimeClicked(alarm) }
    binding.enableSwitch.setOnCheckedChangeListener { _, checked ->
      controller.onAlarmToggled(alarm, checked)
    }

    binding.sundayToggle.setOnCheckedChangeListener { _, checked ->
      toggleWeekDay(Calendar.SUNDAY, checked)
    }

    binding.mondayToggle.setOnCheckedChangeListener { _, checked ->
      toggleWeekDay(Calendar.MONDAY, checked)
    }

    binding.tuesdayToggle.setOnCheckedChangeListener { _, checked ->
      toggleWeekDay(Calendar.TUESDAY, checked)
    }

    binding.wednesdayToggle.setOnCheckedChangeListener { _, checked ->
      toggleWeekDay(Calendar.WEDNESDAY, checked)
    }

    binding.thursdayToggle.setOnCheckedChangeListener { _, checked ->
      toggleWeekDay(Calendar.THURSDAY, checked)
    }

    binding.fridayToggle.setOnCheckedChangeListener { _, checked ->
      toggleWeekDay(Calendar.FRIDAY, checked)
    }

    binding.saturdayToggle.setOnCheckedChangeListener { _, checked ->
      toggleWeekDay(Calendar.SATURDAY, checked)
    }

    binding.preset.setOnClickListener { controller.onAlarmPresetClicked(alarm) }
    binding.vibrate.setOnClickListener {
      binding.vibrate.toggle()
      controller.onAlarmVibrationToggled(alarm, binding.vibrate.isChecked)
    }

    binding.delete.setOnClickListener { controller.onAlarmDeleteClicked(alarm) }
  }

  private fun toggleWeekDay(day: Int, enabled: Boolean) {
    val mask = 1 shl (day - 1)
    val new = if (enabled) alarm.weeklySchedule or mask else alarm.weeklySchedule and mask.inv()
    controller.onAlarmWeeklyScheduleChanged(alarm, new)
  }

  fun bind(alarm: Alarm, isExpanded: Boolean) {
    this.alarm = alarm
    val context = binding.root.context

    binding.expandToggle.setIconResource(
      if (isExpanded) R.drawable.ic_round_keyboard_arrow_up_24 else R.drawable.ic_round_keyboard_arrow_down_24
    )

    binding.label.isVisible = isExpanded || alarm.label != null
    binding.scheduleToggleContainer.isVisible = isExpanded
    binding.preset.isVisible = isExpanded
    binding.vibrate.isVisible = isExpanded
    binding.delete.isVisible = isExpanded

    binding.label.isActivated = alarm.isEnabled
    binding.label.text = alarm.label

    binding.time.isActivated = alarm.isEnabled
    Calendar.getInstance()
      .apply { set(Calendar.HOUR_OF_DAY, alarm.minuteOfDay / 60) }
      .apply { set(Calendar.MINUTE, alarm.minuteOfDay % 60) }
      .let { DateUtils.formatDateTime(context, it.timeInMillis, DateUtils.FORMAT_SHOW_TIME) }
      .also { binding.time.text = it }

    binding.nextTrigger.isActivated = alarm.isEnabled
    binding.nextTrigger.text = if (alarm.isEnabled) {
      DateUtils.getRelativeTimeSpanString(
        alarm.getTriggerTimeMillis(),
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY,
      )
    } else {
      context.getString(R.string.not_scheduled)
    }

    binding.enableSwitch.isChecked = alarm.isEnabled

    binding.sundayToggle.isActivated = alarm.isEnabled
    binding.mondayToggle.isActivated = alarm.isEnabled
    binding.tuesdayToggle.isActivated = alarm.isEnabled
    binding.wednesdayToggle.isActivated = alarm.isEnabled
    binding.thursdayToggle.isActivated = alarm.isEnabled
    binding.fridayToggle.isActivated = alarm.isEnabled
    binding.saturdayToggle.isActivated = alarm.isEnabled

    binding.sundayToggle.isChecked = alarm.weeklySchedule and 0b1 != 0
    binding.mondayToggle.isChecked = alarm.weeklySchedule and 0b10 != 0
    binding.tuesdayToggle.isChecked = alarm.weeklySchedule and 0b100 != 0
    binding.wednesdayToggle.isChecked = alarm.weeklySchedule and 0b1000 != 0
    binding.thursdayToggle.isChecked = alarm.weeklySchedule and 0b10000 != 0
    binding.fridayToggle.isChecked = alarm.weeklySchedule and 0b100000 != 0
    binding.saturdayToggle.isChecked = alarm.weeklySchedule and 0b1000000 != 0

    binding.preset.text = alarm.preset?.name ?: context.getString(R.string.random_preset)
    binding.vibrate.isChecked = alarm.vibrate
  }
}

object AlarmComparator : DiffUtil.ItemCallback<Alarm>() {

  override fun areItemsTheSame(oldItem: Alarm, newItem: Alarm): Boolean {
    return oldItem.id == newItem.id
  }

  override fun areContentsTheSame(oldItem: Alarm, newItem: Alarm): Boolean {
    return oldItem == newItem
  }
}

interface AlarmItemViewController {
  fun onAlarmLabelClicked(alarm: Alarm)
  fun onAlarmTimeClicked(alarm: Alarm)
  fun onAlarmToggled(alarm: Alarm, enabled: Boolean)
  fun onAlarmWeeklyScheduleChanged(alarm: Alarm, newWeeklySchedule: Int)
  fun onAlarmPresetClicked(alarm: Alarm)
  fun onAlarmVibrationToggled(alarm: Alarm, vibrate: Boolean)
  fun onAlarmDeleteClicked(alarm: Alarm)
}
