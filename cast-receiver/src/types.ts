/**
 * PlayerAction declares action messages sent with a PlayerEvent by the sender
 * application to control a player instance.
 */
export enum PlayerAction {
  Create = "create",
  Play = "play",
  Pause = "pause",
  Stop = "stop",
}

/**
 * A PlayerEvent is used to manipulate playback of a particular sound. These
 * events are sent by the sender android application.
 */
export interface PlayerEvent {
  soundKey: string;
  isLooping: boolean;
  action?: string;
  volume: number;
}

/**
 * An alias to ensure type safety
 */
export interface PlayerMap {
  [key: string]: Howl;
}

/**
 * PlayerStatusEventType declares the types of player status events emmitted by
 * the PlayerManager. These events are subscribed by the StatusUIHandler (bindings
 * are present in index.ts).
 */
export enum PlayerStatusEventType {
  Added = "playeradded",
  Started = "playerstarted",
  Paused = "playerpaused",
  Removed = "playerremoved",
}
