package com.github.ashutoshgngwr.noice.sound

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.mediarouter.media.MediaRouter
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.cast.CastSessionManagerListener
import com.github.ashutoshgngwr.noice.cast.CastVolumeProvider
import com.github.ashutoshgngwr.noice.sound.player.SoundPlayerFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession

/**
 * [PlaybackManager] is responsible for managing playback end-to-end for all sounds.
 * It manages Android's audio focus implicitly. It also manages Playback routing to
 * cast enabled devices on-demand.
 */
class PlaybackManager(private val context: Context) :
  AudioManager.OnAudioFocusChangeListener {

  enum class State {
    PLAYING, PAUSED, STOPPED
  }

  companion object {
    private val TAG = PlaybackManager::javaClass.name
  }

  var state = State.STOPPED
  private var hasAudioFocus = false
  private var playbackDelayed = false
  private var resumeOnFocusGain = false
  private var onPlaybackUpdateListener = { }

  val playbacks = HashMap<String, Playback>(Sound.LIBRARY.size)
  private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  private val audioAttributes = AudioAttributesCompat.Builder()
    .setContentType(AudioAttributesCompat.CONTENT_TYPE_MOVIE)
    .setUsage(AudioAttributesCompat.USAGE_GAME)
    .setLegacyStreamType(AudioManager.STREAM_MUSIC)
    .build()

  private val audioFocusRequest = AudioFocusRequestCompat
    .Builder(AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
    .setAudioAttributes(audioAttributes)
    .setOnAudioFocusChangeListener(this, Handler())
    .setWillPauseWhenDucked(false)
    .build()

  private var playerFactory: SoundPlayerFactory =
    SoundPlayerFactory.LocalSoundPlayerFactory(context, audioAttributes)

  private val playbackStateBuilder = PlaybackStateCompat.Builder()
    .setActions(
      PlaybackStateCompat.ACTION_PLAY_PAUSE
        and PlaybackStateCompat.ACTION_PAUSE
        and PlaybackStateCompat.ACTION_STOP
    )

  val mediaSession = MediaSessionCompat(context, context.packageName).also {
    it.setMetadata(
      MediaMetadataCompat.Builder()
        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, context.getString(R.string.app_name))
        .build()
    )

    it.setCallback(object : MediaSessionCompat.Callback() {
      override fun onPlay() = resume()
      override fun onStop() = stop()
      override fun onPause() = pause()
    })

    it.setPlaybackToLocal(AudioManager.STREAM_MUSIC)
    it.setPlaybackState(playbackStateBuilder.build())
    it.isActive = true
  }

  private val castSessionManagerListener = object : CastSessionManagerListener() {
    fun switchToRemotePlayback(session: CastSession) {
      playerFactory = SoundPlayerFactory.CastSoundPlayerFactory(
        session,
        context.getString(R.string.cast_namespace__default)
      )
      reloadPlaybacks()
      mediaSession.setPlaybackToRemote(CastVolumeProvider(session))
      AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest)
    }

    override fun onSessionStarted(session: CastSession, sessionId: String) =
      switchToRemotePlayback(session)

    override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) =
      switchToRemotePlayback(session)

    override fun onSessionEnded(session: CastSession, error: Int) {
      // onSessionEnded gets called when restarting the activity. So need to ensure that we're not
      // recreating the LocalPlayerFactory again because it will cause playbacks to restarts which
      // means glitches in playback.
      if (playerFactory is SoundPlayerFactory.LocalSoundPlayerFactory) {
        return
      }

      playerFactory = SoundPlayerFactory.LocalSoundPlayerFactory(context, audioAttributes)
      requestAudioFocus()
      mediaSession.setPlaybackToLocal(AudioManager.STREAM_MUSIC)
      reloadPlaybacks()
    }
  }

  init {
    MediaRouter.getInstance(context).setMediaSessionCompat(mediaSession)
    CastContext.getSharedInstance(context).sessionManager.addSessionManagerListener(
      castSessionManagerListener,
      CastSession::class.java
    )
  }

  // implements audio focus change listener
  override fun onAudioFocusChange(focusChange: Int) {
    when (focusChange) {
      AudioManager.AUDIOFOCUS_GAIN -> {
        Log.d(TAG, "Gained audio focus...")
        hasAudioFocus = true
        if (playbackDelayed || resumeOnFocusGain) {
          Log.d(TAG, "Resume playback after audio focus gain...")
          playbackDelayed = false
          resumeOnFocusGain = false
          resume()
        }
      }
      AudioManager.AUDIOFOCUS_LOSS -> {
        Log.d(TAG, "Permanently lost audio focus! Stop playback...")
        hasAudioFocus = false
        resumeOnFocusGain = true
        playbackDelayed = false
        pause()
      }
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
        Log.d(TAG, "Temporarily lost audio focus! Pause playback...")
        hasAudioFocus = false
        resumeOnFocusGain = true
        playbackDelayed = false
        pause()
      }
    }
  }

  // creates audio focus request and handles its response
  private fun requestAudioFocus() {
    if (hasAudioFocus) {
      return
    }

    val result = AudioManagerCompat.requestAudioFocus(audioManager, audioFocusRequest)
    Log.d(TAG, "AudioFocusRequest result: $result")
    when (result) {
      AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
        Log.d(TAG, "Audio focus request was delayed! Pause playback for now.")
        playbackDelayed = true
        hasAudioFocus = false
        resumeOnFocusGain = false
        pause()
      }
      AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
        Log.d(TAG, "Failed to get audio focus! Stop playback...")
        hasAudioFocus = false
        playbackDelayed = false
        resumeOnFocusGain = false
        pause()
      }
      AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
        hasAudioFocus = true
        playbackDelayed = false
        resumeOnFocusGain = false
        resume()
      }
    }
  }

  /**
   * starts playing a sound. It also creates an audio focus request if we don't have it.
   * Playback won't start immediately if audio focus is not present. We always ensure that we
   * have audio focus before starting the playback.
   */
  fun play(sound: Sound) {
    if (!playbacks.containsKey(sound.key)) {
      playbacks[sound.key] = Playback(sound, playerFactory)
    }

    if (playbackDelayed) {
      // If audio focus is delayed, add this sound to playbacks and it will be played whenever the
      // we get audio focus.
      state = State.PAUSED
      notifyChanges()
      return
    }

    if (!hasAudioFocus) {
      // if doesn't have audio focus, request and return because a successful audio focus request
      // will start playback.
      requestAudioFocus()
      return
    }

    state = State.PLAYING
    requireNotNull(playbacks[sound.key]).play()
    notifyChanges()
  }

  /**
   * Stops a playback and releases underlying resources. It abandons focus if all playbacks are
   * stopped.
   */
  fun stop(sound: Sound) {
    requireNotNull(playbacks[sound.key]).apply {
      stop()
    }

    playbacks.remove(sound.key)
    if (playbacks.isEmpty()) {
      state = State.STOPPED
      AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest)
    }

    notifyChanges()
  }

  /**
   * Stops all playbacks and releases underlying resources. Also abandon the audio focus.
   */
  fun stop() {
    state = State.STOPPED
    playbacks.values.forEach {
      it.stop()
    }

    playbacks.clear()
    AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest)
    notifyChanges()
  }

  /**
   * Stops all playbacks but maintains their state so that these can be resumed at a later stage.
   * It abandons audio focus.
   */
  fun pause() {
    state = State.PAUSED
    playbacks.values.forEach {
      it.pause()
    }

    AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest)
    notifyChanges()
  }

  /**
   * Resumes all playbacks from the saved state. It requests
   */
  fun resume() {
    playbacks.values.forEach {
      play(Sound.get(it.soundKey))
    }

    notifyChanges()
  }

  /**
   * Sets the volume for the [Playback] with given key.
   * @param soundKey identifier for the [Playback]. If playback is not found, no action is taken
   * @param volume updated volume for the [Playback]
   */
  fun setVolume(soundKey: String, volume: Int) {
    if (!playbacks.containsKey(soundKey)) {
      return
    }

    requireNotNull(playbacks[soundKey]).setVolume(volume)
  }

  /**
   * Sets the time period for the [Playback] with given key
   * @param soundKey identifier for the [Playback]. If playback is not found, no action is taken
   * @param timePeriod updated time period for the [Playback]
   */
  fun setTimePeriod(soundKey: String, timePeriod: Int) {
    if (!playbacks.containsKey(soundKey)) {
      return
    }

    requireNotNull(playbacks[soundKey]).timePeriod = timePeriod
  }

  /**
   * Attempts to recreate underlying player for playbacks using the [playerFactory]
   */
  private fun reloadPlaybacks() {
    playbacks.values.forEach { it.recreatePlayerWithFactory(playerFactory) }
  }

  /**
   * Performs cleanup. must be called when final cleanup is required for the instance
   */
  fun cleanup() {
    stop()
    mediaSession.release()
    CastContext.getSharedInstance(context).sessionManager.removeSessionManagerListener(
      castSessionManagerListener,
      CastSession::class.java
    )
  }

  /**
   * Allows clients to subscribe for changes in [Playbacks][Playback]
   * @param listener a lambda that is called on every update
   */
  fun setOnPlaybackUpdateListener(listener: () -> Unit) {
    onPlaybackUpdateListener = listener
  }

  private fun notifyChanges() {
    when (state) {
      State.PLAYING -> playbackStateBuilder.setState(
        PlaybackStateCompat.STATE_PLAYING,
        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
        1f
      )


      State.PAUSED -> playbackStateBuilder.setState(
        PlaybackStateCompat.STATE_PAUSED,
        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
        0f
      )


      State.STOPPED -> playbackStateBuilder.setState(
        PlaybackStateCompat.STATE_STOPPED,
        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
        0f
      )

    }

    mediaSession.setPlaybackState(playbackStateBuilder.build())
    onPlaybackUpdateListener()
  }
}
