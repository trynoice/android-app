import { Howl } from "howler";
import SoundLibrary from "noice/library";

interface PlayerEvent {
  soundKey: string;
  isLooping: boolean;
  action?: string;
  volume: number;
}

interface PlayerMap {
  [key: string]: Howl;
}

function isEmptyMap(map): boolean {
  for (const key in map) {
    if (key) return false;
  }

  return true;
}

export default class PlayerManager {
  private readonly ACTION_PLAY = "play";
  private readonly ACTION_PAUSE = "pause";
  private readonly ACTION_STOP = "stop";

  private players: PlayerMap = {};


  private createPlayer(soundKey: string, isLooping: boolean): Howl {
    return new Howl({
      src: SoundLibrary[soundKey],
      autoplay: false,
      html5: false,
      loop: isLooping,
      pool: 1,
      preload: true,
    });
  }

  private play(soundKey: string): void {
    const player = this.players[soundKey];
    if (player.playing() === false) {
      player.play();
      if (player.loop()) {
        player.fade(0, player.volume(), 1500);
      }
    }
  }

  private pause(soundKey: string): void {
    if (this.players[soundKey].playing()) {
      this.players[soundKey].pause();
    }
  }

  private stop(soundKey: string): void {
    const player = this.players[soundKey];
    delete this.players[soundKey];
    if (player.playing()) {
      player.fade(player.volume(), 0, 1000);
      player.once("fade", () => {
        player.stop();
        player.unload();
      });
    } else {
      player.unload();
    }
  }

  handlePlayerEvent(event: PlayerEvent): void {
    if (event.soundKey in this.players === false) {
      if (event.action === undefined || event.action === null) {
        return;
      }

      this.players[event.soundKey] = this.createPlayer(
        event.soundKey,
        event.isLooping
      );
    }

    this.players[event.soundKey].volume(event.volume);
    switch (event.action) {
      case this.ACTION_PLAY:
        this.play(event.soundKey);
        break;
      case this.ACTION_PAUSE:
        this.pause(event.soundKey);
        break;
      case this.ACTION_STOP:
        this.stop(event.soundKey);
        break;
    }
  }
}
