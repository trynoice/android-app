package com.github.ashutoshgngwr.noice.fragment

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.fragment.app.Fragment
import androidx.fragment.app.clearFragmentResultListener
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.paging.PagingData
import androidx.paging.PagingDataAdapter
import androidx.paging.cachedIn
import androidx.paging.map
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.AlarmItemBinding
import com.github.ashutoshgngwr.noice.databinding.AlarmsFragmentBinding
import com.github.ashutoshgngwr.noice.ext.getSerializableCompat
import com.github.ashutoshgngwr.noice.ext.hasSelfPermission
import com.github.ashutoshgngwr.noice.ext.launchAndRepeatOnStarted
import com.github.ashutoshgngwr.noice.ext.showErrorSnackBar
import com.github.ashutoshgngwr.noice.ext.showTimePicker
import com.github.ashutoshgngwr.noice.ext.startAppDetailsSettingsActivity
import com.github.ashutoshgngwr.noice.metrics.AnalyticsProvider
import com.github.ashutoshgngwr.noice.models.Alarm
import com.github.ashutoshgngwr.noice.models.Preset
import com.github.ashutoshgngwr.noice.repository.AlarmRepository
import com.github.ashutoshgngwr.noice.repository.SubscriptionRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

private const val FREE_ALARM_COUNT = 2

@AndroidEntryPoint
class AlarmsFragment : Fragment(), AlarmViewHolder.ViewController {

  @set:Inject
  internal var analyticsProvider: AnalyticsProvider? = null

  private lateinit var binding: AlarmsFragmentBinding

  private val viewModel: AlarmsViewModel by viewModels()
  private val adapter: AlarmListAdapter by lazy { AlarmListAdapter(layoutInflater, this) }
  private val mainNavController: NavController by lazy {
    Navigation.findNavController(requireActivity(), R.id.main_nav_host_fragment)
  }

  private val homeNavController: NavController by lazy {
    Navigation.findNavController(requireActivity(), R.id.home_nav_host_fragment)
  }

  private val requestPermissionsLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission(),
    this::onPostNotificationsPermissionRequestResult,
  )

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = AlarmsFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.addAlarmButton.setOnClickListener { startAddAlarmFlow() }
    binding.list.adapter = adapter

    var lastAutoScrollPosition = -1
    adapter.addLoadStateListener { loadStates ->
      binding.emptyListIndicator.isVisible =
        loadStates.append.endOfPaginationReached && adapter.itemCount < 1

      adapter.snapshot()
        .items
        .indexOfFirst { it.isExpanded }
        .takeIf { it > -1 && it != lastAutoScrollPosition }
        ?.also { lastAutoScrollPosition = it }
        ?.also { binding.list.scrollToPosition(it) }
    }

    viewLifecycleOwner.launchAndRepeatOnStarted {
      viewModel.alarmsPagingData.collectLatest(adapter::submitData)
    }

    viewLifecycleOwner.launchAndRepeatOnStarted {
      viewModel.isSubscribed
        .filterNot { it }
        .map { viewModel.disableAll(FREE_ALARM_COUNT) }
        .filter { it > 0 }
        .collect { showErrorSnackBar(R.string.alarms_disabled_due_to_subscription_expiration) }
    }

    analyticsProvider?.setCurrentScreen(this::class)
  }

  private fun startAddAlarmFlow() {
    if (!viewModel.hasScheduleAlarmsPermission()) {
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

  override fun onAlarmItemCollapsed(alarm: Alarm) {
    viewModel.setExpandedAlarmId(null)
  }

  override fun onAlarmItemExpanded(alarm: Alarm) {
    viewModel.setExpandedAlarmId(alarm.id)
  }

  override fun onAlarmLabelClicked(alarm: Alarm) {
    DialogFragment.show(childFragmentManager) {
      title(R.string.edit_label)
      val valueGetter = input(
        hintRes = R.string.add_label,
        preFillValue = alarm.label ?: "",
      )

      negativeButton(R.string.cancel)
      positiveButton(R.string.save) {
        val label = valueGetter.invoke()
        viewModel.save(alarm.copy(label = label.ifBlank { null }))
      }
    }
  }

  override fun onAlarmTimeClicked(alarm: Alarm) {
    showTimePicker(hour = alarm.minuteOfDay / 60, alarm.minuteOfDay % 60) { hour, minute ->
      viewModel.save(
        alarm.copy(
          isEnabled = alarm.isEnabled || viewModel.canEnableMoreAlarms(),
          minuteOfDay = hour * 60 + minute,
        )
      )
    }
  }

  override fun onAlarmToggled(alarm: Alarm, enabled: Boolean) {
    if (enabled && !viewModel.hasScheduleAlarmsPermission()) {
      showAlarmPermissionRationale()
      return
    }

    if (enabled && !viewModel.canEnableMoreAlarms()) {
      mainNavController.navigate(R.id.view_subscription_plans)
      return
    }

    viewModel.save(alarm.copy(isEnabled = enabled))
  }

  override fun onAlarmWeeklyScheduleChanged(alarm: Alarm, newWeeklySchedule: Int) {
    viewModel.save(alarm.copy(weeklySchedule = newWeeklySchedule))
  }

  override fun onAlarmPresetClicked(alarm: Alarm) {
    val resultKey = "PresetPickerResult.alarm-${alarm.id}"
    clearFragmentResultListener(resultKey) // clear previous listener
    setFragmentResultListener(resultKey) { _, result ->
      result.getSerializableCompat(PresetPickerFragment.EXTRA_SELECTED_PRESET, Preset::class)
        .also { viewModel.save(alarm.copy(preset = it)) }
    }

    PresetPickerFragmentArgs(fragmentResultKey = resultKey, selectedPreset = alarm.preset)
      .toBundle()
      .also { homeNavController.navigate(R.id.preset_picker, it) }
  }

  override fun onAlarmVibrationToggled(alarm: Alarm, vibrate: Boolean) {
    viewModel.save(alarm.copy(vibrate = vibrate))
  }

  override fun onAlarmDeleteClicked(alarm: Alarm) {
    viewModel.setExpandedAlarmId(null)
    viewModel.delete(alarm)
  }
}

@HiltViewModel
class AlarmsViewModel @Inject constructor(
  private val alarmRepository: AlarmRepository,
  subscriptionRepository: SubscriptionRepository,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {

  private val expandedAlarmId = MutableStateFlow(
    AlarmsFragmentArgs.fromSavedStateHandle(savedStateHandle)
      .focusedAlarmId
      .takeIf { it != -1 }
  )

  internal val alarmsPagingData: Flow<PagingData<AlarmItem>> = alarmRepository.pagingDataFlow()
    .cachedIn(viewModelScope)
    .combine(expandedAlarmId) { pagingData, expandedAlarmId ->
      pagingData.map { AlarmItem(it, it.id == expandedAlarmId) }
    }

  internal val isSubscribed: StateFlow<Boolean> = subscriptionRepository.isSubscribed()
    .stateIn(viewModelScope, SharingStarted.Eagerly, true)

  private val enabledCount = alarmRepository.countEnabled()
    .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

  internal fun hasScheduleAlarmsPermission(): Boolean {
    return alarmRepository.canScheduleAlarms()
  }

  internal fun create(hour: Int, minute: Int) {
    save(
      Alarm(
        id = 0,
        label = null,
        isEnabled = canEnableMoreAlarms(),
        minuteOfDay = hour * 60 + minute,
        weeklySchedule = 0,
        preset = null,
        vibrate = true,
      )
    )
  }

  internal fun save(alarm: Alarm) {
    viewModelScope.launch {
      alarmRepository.save(alarm)
    }
  }

  internal fun delete(alarm: Alarm) {
    viewModelScope.launch {
      alarmRepository.delete(alarm)
    }
  }

  internal suspend fun disableAll(offset: Int): Int {
    return alarmRepository.disableAll(offset)
  }

  internal fun canEnableMoreAlarms(): Boolean {
    return hasScheduleAlarmsPermission() && (isSubscribed.value || enabledCount.value < FREE_ALARM_COUNT)
  }

  internal fun setExpandedAlarmId(id: Int?) {
    expandedAlarmId.value = id
  }
}

class AlarmListAdapter(
  private val layoutInflater: LayoutInflater,
  private val viewController: AlarmViewHolder.ViewController,
) : PagingDataAdapter<AlarmItem, AlarmViewHolder>(diffCallback) {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
    val binding = AlarmItemBinding.inflate(layoutInflater, parent, false)
    return AlarmViewHolder(binding, viewController)
  }

  override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
    val item = getItem(position) ?: return
    holder.bind(item)
  }

  companion object {
    private val diffCallback = object : DiffUtil.ItemCallback<AlarmItem>() {
      override fun areItemsTheSame(oldItem: AlarmItem, newItem: AlarmItem): Boolean {
        return oldItem.alarm.id == newItem.alarm.id
      }

      override fun areContentsTheSame(oldItem: AlarmItem, newItem: AlarmItem): Boolean {
        return oldItem == newItem
      }
    }
  }
}

class AlarmViewHolder(
  private val binding: AlarmItemBinding,
  private val controller: ViewController,
) : ViewHolder(binding.root) {

  private lateinit var alarm: Alarm
  private var isExpanded = false

  init {
    val expandToggleClickListener = View.OnClickListener {
      if (isExpanded) {
        controller.onAlarmItemCollapsed(alarm)
      } else {
        controller.onAlarmItemExpanded(alarm)
      }
    }

    binding.root.setOnClickListener(expandToggleClickListener)
    binding.expandToggle.setOnClickListener(expandToggleClickListener)
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

  fun bind(item: AlarmItem) {
    this.alarm = item.alarm
    this.isExpanded = item.isExpanded
    val context = binding.root.context

    binding.expandToggle.animate()
      .rotation(if (isExpanded) 180f else 0f)
      .setDuration(context.resources.getInteger(android.R.integer.config_shortAnimTime).toLong())
      .start()

    // on enabling these views just before the expansion animation, both of them inherit focused
    // background from `selectableItemBackground` selector. To prevent it, enable them with a short
    // delay.
    binding.root.postDelayed(100L) {
      binding.label.isClickable = isExpanded
      binding.label.isEnabled = isExpanded
      binding.time.isClickable = isExpanded
      binding.time.isEnabled = isExpanded
    }

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

    binding.enableSwitch.setChecked(alarm.isEnabled, false)

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


  interface ViewController {
    fun onAlarmItemCollapsed(alarm: Alarm)
    fun onAlarmItemExpanded(alarm: Alarm)
    fun onAlarmLabelClicked(alarm: Alarm)
    fun onAlarmTimeClicked(alarm: Alarm)
    fun onAlarmToggled(alarm: Alarm, enabled: Boolean)
    fun onAlarmWeeklyScheduleChanged(alarm: Alarm, newWeeklySchedule: Int)
    fun onAlarmPresetClicked(alarm: Alarm)
    fun onAlarmVibrationToggled(alarm: Alarm, vibrate: Boolean)
    fun onAlarmDeleteClicked(alarm: Alarm)
  }
}

data class AlarmItem(val alarm: Alarm, val isExpanded: Boolean)
