import { Event } from "chromecast-caf-receiver/cast.framework.system";
import PlayerManager from "./player_manager";
import StatusUIHandler from "./status_ui_handler";
import { PlayerManagerStatus } from "./types";

const NAMESPACE = "urn:x-cast:com.github.ashutoshgngwr.noice";

function main(): void {
  const opts = new cast.framework.CastReceiverOptions();

  // disable default idle timeout implementation (only works if cast-media-player
  // implementation is used.)
  opts.disableIdleTimeout = true;
  opts.customNamespaces = {};
  opts.customNamespaces[NAMESPACE] = cast.framework.system.MessageType.JSON;

  const ctx = cast.framework.CastReceiverContext.getInstance();
  const manager = new PlayerManager();
  const uiHandler = new StatusUIHandler(
    document.querySelector("#status") ?? document.createElement("div")
  );

  manager.onStatusUpdate((event: PlayerManagerStatus) => {
    switch (event) {
      case PlayerManagerStatus.Idle:
        uiHandler.enableStatus(StatusUIHandler.IDLE_STATUS_ID, true);
        uiHandler.enableStatus(StatusUIHandler.CASTING_STATUS_ID, false);
        break;
      case PlayerManagerStatus.IdleTimedOut:
        ctx.stop();
        break;
      default:
        uiHandler.enableStatus(StatusUIHandler.IDLE_STATUS_ID, false);
        uiHandler.enableStatus(StatusUIHandler.CASTING_STATUS_ID, true);
        break;
    }

    uiHandler.enableStatus(
      StatusUIHandler.LOADER_STATUS_ID,
      event === PlayerManagerStatus.Playing && manager.isBuffering()
    );
  });

  ctx.addCustomMessageListener(NAMESPACE, (event: Event): void => {
    manager.handlePlayerEvent(event.data);
  });

  // In an ideal case, the playback should pause and resume when connection
  // suspends and resumes. Since communication between sender and receiver is
  // only one-way in our implementation, the state can only be maintained at
  // sender's side. Hence we need stop the receiver if the connection breaks.
  ctx.addEventListener(
    cast.framework.system.EventType.SENDER_DISCONNECTED,
    (): void => ctx.stop()
  );

  // show idle status by default
  uiHandler.enableStatus(StatusUIHandler.IDLE_STATUS_ID, true);
  ctx.start(opts);
}

window.addEventListener("load", main);
