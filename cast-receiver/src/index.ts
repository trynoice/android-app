import PlayerManager from "noice/player_manager";
import StatusUIHandler from "noice/status_ui_handler";
import { PlayerStatusEventType } from "noice/types";

const NAMESPACE = "urn:x-cast:com.github.ashutoshgngwr.noice";

function main(): void {
  const opts = new cast.framework.CastReceiverOptions();
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

  ctx.addEventListener(
    cast.framework.system.EventType.SENDER_DISCONNECTED,
    (): void => ctx.stop()
  );

  ctx.start(opts);
  uiHandler.showIdleStatus();
}

window.addEventListener("load", main);
