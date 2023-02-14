package com.github.ashutoshgngwr.noice.fragment

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.fragment.navArgs
import androidx.paging.PagingData
import androidx.paging.PagingDataAdapter
import androidx.paging.cachedIn
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.recyclerview.widget.SimpleItemAnimator
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.AlarmItemBinding
import com.github.ashutoshgngwr.noice.databinding.AlarmsFragmentBinding
import com.github.ashutoshgngwr.noice.ext.hasSelfPermission
import com.github.ashutoshgngwr.noice.ext.launchAndRepeatOnStarted
import com.github.ashutoshgngwr.noice.ext.showErrorSnackBar
import com.github.ashutoshgngwr.noice.ext.showTimePicker
import com.github.ashutoshgngwr.noice.ext.startAppDetailsSettingsActivity
import com.github.ashutoshgngwr.noice.model.Preset
import com.github.ashutoshgngwr.noice.models.Alarm
import com.github.ashutoshgngwr.noice.repository.AlarmRepository
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.github.ashutoshgngwr.noice.repository.SubscriptionRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

private const val FREE_ALARM_COUNT = 2

@AndroidEntryPoint
class AlarmsFragment : Fragment(), AlarmItemViewController {

  private lateinit var binding: AlarmsFragmentBinding

  private var hasHandledFocusedAlarmArg = false
  private val viewModel: AlarmsViewModel by viewModels()
  private val args: AlarmsFragmentArgs by navArgs()
  private val adapter: AlarmListAdapter by lazy { AlarmListAdapter(layoutInflater, this) }
  private val mainNavController: NavController by lazy {
    Navigation.findNavController(requireActivity(), R.id.main_nav_host_fragment)
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
    binding.lifecycleOwner = viewLifecycleOwner
    binding.addAlarmButton.setOnClickListener { startAddAlarmFlow() }
    (binding.list.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    binding.list.adapter = adapter
    adapter.addLoadStateListener { loadStates ->
      binding.emptyListIndicator.isVisible =
        loadStates.append.endOfPaginationReached && adapter.itemCount < 1

      if (!hasHandledFocusedAlarmArg) {
        if (args.focusedAlarmId < 0) {
          hasHandledFocusedAlarmArg = true
        } else {
          // items may take a while to load, so wait for the item with requested id to appear.
          adapter.snapshot()
            .items
            .indexOfFirst { it.id == args.focusedAlarmId }
            .takeIf { it > -1 }
            ?.also { pos ->
              onAlarmItemExpanded(pos)
              binding.list.scrollToPosition(pos)
              hasHandledFocusedAlarmArg = true
            }
        }
      }
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

  override fun onAlarmItemCollapsed(bindingAdapterPosition: Int) {
    binding.list.beginDelayedLayoutBoundsChangeTransition(bindingAdapterPosition)
    adapter.setExpandedItemPosition(-1, true)
  }

  override fun onAlarmItemExpanded(bindingAdapterPosition: Int) {
    if (adapter.expandedPosition > -1) {
      binding.list.beginDelayedLayoutBoundsChangeTransition(adapter.expandedPosition)
    }

    binding.list.beginDelayedLayoutBoundsChangeTransition(bindingAdapterPosition)
    adapter.setExpandedItemPosition(bindingAdapterPosition, true)
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
    DialogFragment.show(childFragmentManager) {
      title(R.string.select_preset)
      singleChoiceItems(
        items = mutableListOf(getString(R.string.random_preset))
          .apply {
            addAll(
              viewModel.presets
                .value
                .map { it.name }
            )
          }.toTypedArray(),
        currentChoice = 1 + viewModel.presets
          .value
          .indexOfFirst { it.id == alarm.preset?.id },
        onItemSelected = { choice ->
          val preset = if (choice == 0) null else viewModel.presets.value[choice - 1]
          viewModel.save(alarm.copy(preset = preset))
        },
      )

      negativeButton(R.string.cancel)
    }
  }

  override fun onAlarmVibrationToggled(alarm: Alarm, vibrate: Boolean) {
    viewModel.save(alarm.copy(vibrate = vibrate))
  }

  override fun onAlarmDeleteClicked(alarm: Alarm) {
    adapter.setExpandedItemPosition(-1, false)
    viewModel.delete(alarm)
  }

  private fun RecyclerView.beginDelayedLayoutBoundsChangeTransition(position: Int) {
    findViewHolderForAdapterPosition(position)
      .let { it?.itemView as? ViewGroup }
      ?.also { TransitionManager.beginDelayedTransition(it, ChangeBounds()) }
  }
}

@HiltViewModel
class AlarmsViewModel @Inject constructor(
  private val alarmRepository: AlarmRepository,
  presetRepository: PresetRepository,
  subscriptionRepository: SubscriptionRepository,
) : ViewModel() {

  internal val alarmsPagingData: Flow<PagingData<Alarm>> = alarmRepository.pagingDataFlow()
    .cachedIn(viewModelScope)

  internal val presets: StateFlow<List<Preset>> = presetRepository.listFlow()
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
}

class AlarmListAdapter(
  private val layoutInflater: LayoutInflater,
  private val itemViewController: AlarmItemViewController,
) : PagingDataAdapter<Alarm, AlarmViewHolder>(AlarmComparator) {

  var expandedPosition = 0; private set

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
    val binding = AlarmItemBinding.inflate(layoutInflater, parent, false)
    return AlarmViewHolder(binding, itemViewController)
  }

  override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
    val alarm = getItem(position) ?: return
    holder.bind(alarm, expandedPosition == position)
  }

  fun setExpandedItemPosition(position: Int, notifyChanges: Boolean) {
    val previousExpandedPosition = expandedPosition
    expandedPosition = position
    if (notifyChanges) {
      if (previousExpandedPosition > -1) notifyItemChanged(previousExpandedPosition)
      if (position > -1) notifyItemChanged(position)
    }
  }
}

class AlarmViewHolder(
  private val binding: AlarmItemBinding,
  private val controller: AlarmItemViewController,
) : ViewHolder(binding.root) {

  private lateinit var alarm: Alarm
  private var isExpanded = false

  init {
    val expandToggleClickListener = View.OnClickListener {
      if (isExpanded) {
        controller.onAlarmItemCollapsed(bindingAdapterPosition)
      } else {
        controller.onAlarmItemExpanded(bindingAdapterPosition)
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

  fun bind(alarm: Alarm, isExpanded: Boolean) {
    this.alarm = alarm
    this.isExpanded = isExpanded
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
  fun onAlarmItemCollapsed(bindingAdapterPosition: Int)
  fun onAlarmItemExpanded(bindingAdapterPosition: Int)
  fun onAlarmLabelClicked(alarm: Alarm)
  fun onAlarmTimeClicked(alarm: Alarm)
  fun onAlarmToggled(alarm: Alarm, enabled: Boolean)
  fun onAlarmWeeklyScheduleChanged(alarm: Alarm, newWeeklySchedule: Int)
  fun onAlarmPresetClicked(alarm: Alarm)
  fun onAlarmVibrationToggled(alarm: Alarm, vibrate: Boolean)
  fun onAlarmDeleteClicked(alarm: Alarm)
}
