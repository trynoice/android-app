package com.github.ashutoshgngwr.noice.engine

import android.app.PendingIntent
import android.content.Context
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.session.MediaSession
import com.github.ashutoshgngwr.noice.R
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * A convenient wrapper containing all Media 3 [MediaSession] API interactions.
 */
class SoundPlayerManagerMediaSession(context: Context, sessionActivityPi: PendingIntent) {

  private val defaultPresetName = context.getString(R.string.unsaved_preset)
  private val sessionPlayer = MediaSessionPlayer(context.getString(R.string.now_playing))

  /**
   * The underlying Media 3 [MediaSession] used by this instance.
   */
  val session = MediaSession.Builder(context, sessionPlayer)
    .setId("SoundPlayback")
    .setSessionActivity(sessionActivityPi)
    .build()

  fun setPlaybackToLocal() {
    sessionPlayer.setPlaybackToLocal()
  }

  fun setPlaybackToRemote(volumeProvider: RemoteDeviceVolumeProvider) {
    sessionPlayer.setPlaybackToRemote(volumeProvider)
  }

  /**
   * Translates the given [state] to its corresponding [Player] state and sets it on the current
   * media session.
   */
  fun setState(state: SoundPlayerManager.State) {
    when (state) {
      SoundPlayerManager.State.STOPPED -> sessionPlayer.updatePlaybackState(
        playbackState = Player.STATE_ENDED,
        playWhenReady = false,
      )

      SoundPlayerManager.State.PAUSED -> sessionPlayer.updatePlaybackState(
        playbackState = Player.STATE_READY,
        playWhenReady = false,
      )

      else -> sessionPlayer.updatePlaybackState(
        playbackState = Player.STATE_READY,
        playWhenReady = true,
      )
    }
  }

  /**
   * Sets the given volume on the current media session.
   */
  fun setVolume(volume: Float) {
    sessionPlayer.updateVolumeState(volume)
  }

  /**
   * Sets the given audio attributes on the current media session.
   */
  fun setAudioAttributes(attributes: AudioAttributes) {
    sessionPlayer.updateAudioAttributesState(attributes)
  }

  /**
   * Sets the given [presetName] as the title for the currently playing media item on the current
   * media session. If the [presetName] is `null`, it uses [R.string.unsaved_preset] as title.
   */
  fun setPresetName(presetName: String?) {
    sessionPlayer.updateCurrentPreset(
      id = presetName ?: defaultPresetName,
      name = presetName ?: defaultPresetName,
    )
  }

  /**
   * Sets a callback to receive transport controls, media buttons and commands from controllers and
   * the system.
   */
  fun setCallback(callback: Callback?) {
    sessionPlayer.callback = callback
  }

  /**
   * Releases the underlying media session.
   */
  fun release() {
    session.release()
  }

  private class MediaSessionPlayer(
    playlistName: String,
  ) : SimpleBasePlayer(Looper.getMainLooper()) {

    var callback: Callback? = null
    private var volumeProvider: RemoteDeviceVolumeProvider? = null
    private var state = State.Builder()
      .setAvailableCommands(ALWAYS_AVAILABLE_PLAYER_COMMANDS)
      .setAudioAttributes(SoundPlayerManager.DEFAULT_AUDIO_ATTRIBUTES)
      .setPlaylistMetadata(
        MediaMetadata.Builder()
          .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
          .setTitle(playlistName)
          .build()
      )
      .build()

    private val localDeviceInfo = state.deviceInfo

    fun updateVolumeState(volume: Float) {
      state = state.buildUpon()
        .setVolume(volume)
        .build()
      invalidateState()
    }

    fun updateAudioAttributesState(attributes: AudioAttributes) {
      state = state.buildUpon()
        .setAudioAttributes(attributes)
        .build()
      invalidateState()
    }

    fun updatePlaybackState(@Player.State playbackState: Int, playWhenReady: Boolean) {
      state = state.buildUpon()
        .setPlaybackState(playbackState)
        .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        .build()
      invalidateState()
    }

    fun updateCurrentPreset(id: String, name: String) {
      state = state.buildUpon()
        .setPlaylist(
          listOf(
            MediaItemData.Builder(id)
              .setMediaMetadata(
                MediaMetadata.Builder()
                  .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                  .setTitle(name)
                  .build()
              )
              .build()
          )
        )
        .build()
      invalidateState()
    }

    fun setPlaybackToLocal() {
      volumeProvider = null
      state = state.buildUpon()
        .setAvailableCommands(ALWAYS_AVAILABLE_PLAYER_COMMANDS)
        .setDeviceInfo(localDeviceInfo)
        .build()
      invalidateState()
    }

    fun setPlaybackToRemote(volumeProvider: RemoteDeviceVolumeProvider) {
      this.volumeProvider = volumeProvider
      state = state.buildUpon()
        .setAvailableCommands(
          Player.Commands.Builder()
            .addAll(ALWAYS_AVAILABLE_PLAYER_COMMANDS)
            .addAll(
              Player.COMMAND_GET_DEVICE_VOLUME,
              Player.COMMAND_SET_DEVICE_VOLUME,
              Player.COMMAND_ADJUST_DEVICE_VOLUME,
            )
            .build()
        )
        .setDeviceInfo(
          DeviceInfo(DeviceInfo.PLAYBACK_TYPE_REMOTE, 0, volumeProvider.getMaxVolume())
        )
        .setDeviceVolume(volumeProvider.getVolume())
        .setIsDeviceMuted(volumeProvider.isMuted())
        .build()
      invalidateState()
    }

    override fun getState(): State {
      return state
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
      if (playWhenReady) {
        callback?.onPlay()
      } else {
        callback?.onPause()
      }
      return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
      callback?.onStop()
      return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
      mediaItemIndex: Int,
      positionMs: Long,
      @Player.Command seekCommand: Int,
    ): ListenableFuture<*> {
      when (seekCommand) {
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> callback?.onSkipToPrevious()
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> callback?.onSkipToNext()
        else -> throw IllegalArgumentException("unsupported seek command: $seekCommand")
      }
      return Futures.immediateVoidFuture()
    }

    override fun handleSetVolume(volume: Float): ListenableFuture<*> {
      callback?.onSetVolume(volume)
      return Futures.immediateVoidFuture()
    }

    override fun handleSetDeviceVolume(deviceVolume: Int): ListenableFuture<*> {
      volumeProvider?.setVolume(deviceVolume)
      updateDeviceVolumeState()
      return Futures.immediateVoidFuture()
    }

    override fun handleSetDeviceMuted(muted: Boolean): ListenableFuture<*> {
      volumeProvider?.setMuted(muted)
      updateDeviceVolumeState()
      return Futures.immediateVoidFuture()
    }

    override fun handleIncreaseDeviceVolume(): ListenableFuture<*> {
      volumeProvider?.increaseVolume()
      updateDeviceVolumeState()
      return Futures.immediateVoidFuture()
    }

    override fun handleDecreaseDeviceVolume(): ListenableFuture<*> {
      volumeProvider?.decreaseVolume()
      updateDeviceVolumeState()
      return Futures.immediateVoidFuture()
    }

    private fun updateDeviceVolumeState() {
      volumeProvider?.also { volumeProvider ->
        state = state.buildUpon()
          .setDeviceVolume(volumeProvider.getVolume())
          .setIsDeviceMuted(volumeProvider.isMuted())
          .build()
        invalidateState()
      }
    }

    companion object {
      private val ALWAYS_AVAILABLE_PLAYER_COMMANDS = Player.Commands.Builder()
        .addAll(
          Player.COMMAND_PLAY_PAUSE,
          Player.COMMAND_STOP,
          Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
          Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
          Player.COMMAND_GET_VOLUME,
          Player.COMMAND_SET_VOLUME,
          Player.COMMAND_GET_AUDIO_ATTRIBUTES,
          Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
          Player.COMMAND_GET_MEDIA_ITEMS_METADATA,
        )
        .build()
    }
  }

  /**
   * Receives transport controls, media buttons and commands from controllers and the system.
   */
  interface Callback {
    fun onPlay()
    fun onStop()
    fun onPause()
    fun onSkipToPrevious()
    fun onSkipToNext()
    fun onSetVolume(volume: Float)
  }

  interface RemoteDeviceVolumeProvider {
    fun getMaxVolume(): Int
    fun getVolume(): Int
    fun setVolume(volume: Int)
    fun increaseVolume()
    fun decreaseVolume()
    fun isMuted(): Boolean
    fun setMuted(muted: Boolean)
  }
}
