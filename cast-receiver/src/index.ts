import PlayerManager from "noice/player_manager";

const NAMESPACE = "urn:x-cast:com.github.ashutoshgngwr.noice";

window.addEventListener("load", () => {
  const opts = new cast.framework.CastReceiverOptions();
  opts.disableIdleTimeout = true;
  opts.customNamespaces = {};
  opts.customNamespaces[NAMESPACE] = cast.framework.system.MessageType.JSON;
  const ctx = cast.framework.CastReceiverContext.getInstance();
  const manager = new PlayerManager();
  manager.onIdleTimeout(() => ctx.stop());
  ctx.addCustomMessageListener(
    NAMESPACE,
    (event: cast.framework.system.Event) => {
      manager.handlePlayerEvent(event.data);
    }
  );
  ctx.start(opts);
});
