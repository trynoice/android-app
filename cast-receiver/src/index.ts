import PlayerManager from "./player_manager";
import StatusUIHandler from "./status_ui_handler";
import { PlayerStatusEventType } from "./types";

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
  manager.onIdleTimeout(() => ctx.stop());

  const uiHandler = new StatusUIHandler(document.querySelector("#status"));
  manager.onIdle(() => uiHandler.showIdleStatus());
  manager.onPlayerStatus((type: PlayerStatusEventType, soundKey: string) =>
    uiHandler.handlePlayerStatusEvent(type, soundKey)
  );

  ctx.addCustomMessageListener(
    NAMESPACE,
    (event: cast.framework.system.Event) => {
      manager.handlePlayerEvent(event.data);
    }
  );

  // In an ideal case, the playback should pause and resume on connection suspension
  // and resume. Since communitcation between sender and receiver is only one-way in
  // our implementation, the state can only be maintained at sender's side. Hence we
  // need stop the receiver if the connection breaks.
  ctx.addEventListener(
    cast.framework.system.EventType.SENDER_DISCONNECTED,
    (): void => ctx.stop()
  );

  // show idle status by default
  uiHandler.showIdleStatus();
  ctx.start(opts);
}

window.addEventListener("load", main);
