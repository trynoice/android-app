export enum PlayerAction {
  Create = "create",
  Play = "play",
  Pause = "pause",
  Stop = "stop",
}

export interface PlayerMap {
  [key: string]: Howl;
}

export interface PlayerEvent {
  soundKey: string;
  isLooping: boolean;
  action?: string;
  volume: number;
}

export enum PlayerStatusEventType {
  Added = "playeradded",
  Started = "playerstarted",
  Paused = "playerpaused",
  Removed = "playerremoved",
}
