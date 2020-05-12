export default class IdleTimeoutHandler {
  private readonly TIMEOUT_EVENT = "timedout";

  private eventBus: Text = document.createTextNode(null);
  private idleTimer: number;
  private idleTime: number = 10 * 1000;

  constructor(idleTime?: number) {
    if (idleTime) {
      this.idleTime = idleTime;
    }
  }

  stop(): void {
    window.clearTimeout(this.idleTimer);
  }

  start(): void {
    this.stop();
    this.idleTimer = window.setTimeout(
      () => this.eventBus.dispatchEvent(new Event(this.TIMEOUT_EVENT)),
      this.idleTime
    );
  }

  onTimeout(f: () => void): void {
    this.eventBus.addEventListener(this.TIMEOUT_EVENT, f);
  }
}
