import { Howl } from "howler";
import { Sounds } from "./library";
import {
  PlayerMap,
  PlayerEvent,
  PlayerAction,
  PlayerStatusEventType,
} from "./types";

/**
 * PlayerManager manages various player instances for all the sounds.
 * It also implements a callback for subscribing to sender application
 * messagees (bindings in index.ts). Based on these messages, it loads
 * various sounds and controls their playback. It also keeps track of
 * idle status and runs an idle timer that runs out on 5m of inactivity.
 */
export default class PlayerManager {
  private static readonly IDLE_TIMEOUT = 300 * 1000;
  private static readonly FADE_IN_DURATION = 1500;
  private static readonly FADE_OUT_DURATION = 750;
  private static readonly FADE_VOLUME_DURATION = 750;

  private players: PlayerMap = {};
  private idleTimer: number;
  private onIdleTimeoutCallback?: () => void;
  private onIdleCallback?: () => void;
  private onPlayerStatusCallback?: (
    type: PlayerStatusEventType,
    soundKey: string
  ) => void;

  constructor() {
    this.startIdleTimer();
  }

  /**
   * check if any player instance is present. Paused state isn't considered idle.
   */
  private isIdle(): boolean {
    for (const key in this.players) {
      if (key) return false;
    }
    return true;
  }

  /**
   * Calls the onIdle callback and starts a timer that invokes onIdleTimeout
   * callback when finished.
   */
  private startIdleTimer(): void {
    this.stopIdleTimer();
    this.idleTimer = window.setTimeout(() => {
      if (this.onIdleTimeoutCallback) {
        this.onIdleTimeoutCallback();
      }
    }, PlayerManager.IDLE_TIMEOUT);

    if (this.onIdleCallback) {
      this.onIdleCallback();
    }
  }

  private stopIdleTimer(): void {
    window.clearTimeout(this.idleTimer);
  }

  private createPlayer(
    soundKey: string,
    isLooping: boolean,
    volume: number
  ): Howl {
    return new Howl({
      src: Sounds[soundKey],
      autoplay: false,
      html5: false,
      loop: isLooping,
      pool: 1,
      volume: volume,
      preload: true,
    });
  }

  /**
   * start playback for a player. It waits for the Howl instance to be ready
   * and sound to be loaded before starting playback. It invokes the onPlayerStatus
   * callback when the player actually starts.
   */
  private play(soundKey: string): void {
    const player = this.players[soundKey];
    if (player.playing() === false) {
      player.once("play", (): void => {
        if (this.onPlayerStatusCallback) {
          this.onPlayerStatusCallback(PlayerStatusEventType.Started, soundKey);
        }

        // fade-in only looping sounds because non-looping sounds need to maintain
        // their abruptness thingy.
        if (player.loop()) {
          player.fade(0, player.volume(), PlayerManager.FADE_IN_DURATION);
        }
      });

      player.play();
    }
  }

  private pause(soundKey: string): void {
    this.players[soundKey].pause();
    if (this.onPlayerStatusCallback) {
      this.onPlayerStatusCallback(PlayerStatusEventType.Paused, soundKey);
    }
  }

  /**
   * Stops a given player if it is playing. It also releases the underlying
   * resources regardless of the player's playing state.
   */
  private stop(soundKey: string): void {
    const player = this.players[soundKey];
    delete this.players[soundKey];
    if (player.playing()) {
      player.fade(player.volume(), 0, PlayerManager.FADE_OUT_DURATION);
      player.once("fade", () => {
        player.stop();
        player.unload();
      });
    } else {
      player.stop();
      player.unload();
    }

    if (this.onPlayerStatusCallback) {
      this.onPlayerStatusCallback(PlayerStatusEventType.Removed, soundKey);
    }
  }

  private setVolume(soundKey: string, volume: number): void {
    const player = this.players[soundKey];
    if (player.playing()) {
      player.fade(player.volume(), volume, PlayerManager.FADE_VOLUME_DURATION);
    } else {
      player.volume(volume);
    }
  }

  /**
   * Implements the callback to handle messages received from the Sender application.
   * Based on the action taken, PlayerManager invokes an appropriate callback registered
   * to it via on* methods.
   */
  handlePlayerEvent(event: PlayerEvent): void {
    if (event.soundKey in this.players === false) {
      if (event.action === undefined || event.action === null) {
        return;
      }

      if (event.action !== PlayerAction.Create) {
        // shouldn't be here?
        return;
      }

      if (this.isIdle() === true) {
        this.stopIdleTimer();
      }

      this.players[event.soundKey] = this.createPlayer(
        event.soundKey,
        event.isLooping,
        event.volume
      );

      if (this.onPlayerStatusCallback) {
        this.onPlayerStatusCallback(
          PlayerStatusEventType.Added,
          event.soundKey
        );
      }

      return;
    }

    this.setVolume(event.soundKey, event.volume);
    switch (event.action) {
      case PlayerAction.Play:
        this.play(event.soundKey);
        break;
      case PlayerAction.Pause:
        this.pause(event.soundKey);
        break;
      case PlayerAction.Stop:
        this.stop(event.soundKey);
        if (this.isIdle() === true) {
          this.startIdleTimer();
        }
        break;
    }
  }

  /**
   * Registers a callback to notify when status of an underlying
   * sound player instance changes.
   */
  onPlayerStatus(f: (type: PlayerStatusEventType, key: string) => void): void {
    this.onPlayerStatusCallback = f;
  }

  /**
   * Registers a callback to notify when PlayerManager becomes idle.
   */
  onIdle(f: () => void): void {
    this.onIdleCallback = f;
  }

  /**
   * Registers a callback to notify when PlayerManager's idle timer has timed out.
   */
  onIdleTimeout(f: () => void): void {
    this.onIdleTimeoutCallback = f;
  }
}
