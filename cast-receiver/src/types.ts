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
 * A PlayerControlEvent is used to manipulate playback of a particular sound. These
 * events are sent by the sender android application.
 */
export interface PlayerControlEvent {
  src: string[];
  isLooping: boolean;
  action?: PlayerAction;
  volume: number;
  fadeInDuration?: number;
}

/**
 * PlayerManagerStatus declares the types of status events emitted by
 * the PlayerManager to notify clients of its state.
 */
export enum PlayerManagerStatus {
  Idle = "idle",
  IdleTimedOut = "idletimedout",
  Playing = "playing",
}
