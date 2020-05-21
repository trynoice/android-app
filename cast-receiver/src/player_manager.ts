import { Howl } from "howler";
import { Sounds } from "noice/library";
import {
  PlayerMap,
  PlayerEvent,
  PlayerAction,
  PlayerStatusEventType,
} from "noice/types";

export default class PlayerManager {
  private static readonly IDLE_TIMEOUT = 300 * 1000;

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

  private isIdle(): boolean {
    for (const key in this.players) {
      if (key) return false;
    }
    return true;
  }

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

  private play(soundKey: string): void {
    const player = this.players[soundKey];
    if (player.playing() === false) {
      player.once("play", (): void => {
        if (this.onPlayerStatusCallback) {
          this.onPlayerStatusCallback(PlayerStatusEventType.Started, soundKey);
        }

        if (player.loop()) {
          player.fade(0, player.volume(), 1500);
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

  private stop(soundKey: string): void {
    const player = this.players[soundKey];
    delete this.players[soundKey];
    if (player.playing()) {
      player.fade(player.volume(), 0, 750);
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
      player.fade(player.volume(), volume, 500);
    } else {
      player.volume(volume);
    }
  }

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

  onPlayerStatus(f: (type: PlayerStatusEventType, key: string) => void): void {
    this.onPlayerStatusCallback = f;
  }

  onIdle(f: () => void): void {
    this.onIdleCallback = f;
  }

  onIdleTimeout(f: () => void): void {
    this.onIdleTimeoutCallback = f;
  }
}
