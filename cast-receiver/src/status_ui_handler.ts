import { PlayerStatusEventType } from "noice/types";
import { Icons } from "noice/library";

/**
 * StatusUIHandler is responsible for managing status UI of the application.
 * It implements a callback for subscribing to the PlayerManager events (binding
 * in index.ts) and updates the div container (passed as a constructor argument).
 */
export default class StatusUIHandler {
  private static readonly READY_TO_CAST_MSG = "Ready to cast";
  private static readonly LOADING_OPACITY = 0.5;
  private static readonly PAUSED_OPACITY = 0.65;
  private static readonly NORMAL_OPACITY = 0.9;

  private container: HTMLDivElement;
  private readyToCastElement: HTMLSpanElement;

  constructor(statusContainer: HTMLDivElement) {
    this.container = statusContainer;
    this.readyToCastElement = this.createReadyToCastElement();
  }

  private createIcon(id: string): HTMLElement {
    const template = document.createElement("template");
    template.innerHTML = `<svg id="${id}" class="sound"><use xlink:href="${Icons[id]}" /><svg>`;
    return template.content.firstChild as HTMLElement;
  }

  private createReadyToCastElement(): HTMLSpanElement {
    const element = document.createElement("span");
    element.classList.add("caption");
    element.id = "ready-to-cast";
    element.appendChild(this.createIcon("cast"));
    element.appendChild(
      document.createTextNode(StatusUIHandler.READY_TO_CAST_MSG)
    );

    return element;
  }

  /**
   * Puts the ready to cast message on status display. If status display is already
   * showing ready to cast message, the method does nothing.
   */
  showIdleStatus(): void {
    if (this.isShowingIdleStatus() === true) {
      return;
    }

    this.clearStatus();
    this.container.appendChild(this.readyToCastElement);
  }

  private isShowingIdleStatus(): boolean {
    if (this.container.querySelector(`span#${this.readyToCastElement.id}`)) {
      return true;
    }
    return false;
  }

  private clearStatus(): void {
    this.container.innerHTML = "";
  }

  /**
   * Implements the callback for the PlayerManager events. Based on the events, it
   * manipulates the status display for each sound by adding its icon and using
   * opacity to show their playback states.
   * @param type event type
   * @param soundKey sound for which the event occurred
   */
  handlePlayerStatusEvent(type: PlayerStatusEventType, soundKey: string): void {
    let icon: HTMLElement = this.container.querySelector(`#${soundKey}`);
    if (type === PlayerStatusEventType.Removed) {
      if (icon) {
        this.container.removeChild(icon);
      }
      return;
    }

    if (this.isShowingIdleStatus()) {
      this.clearStatus();
    }

    if (!icon) {
      icon = this.createIcon(soundKey);
      this.container.appendChild(icon);
    }

    switch (type) {
      case PlayerStatusEventType.Added:
        icon.style.opacity = `${StatusUIHandler.LOADING_OPACITY}`;
        break;
      case PlayerStatusEventType.Paused:
        icon.style.opacity = `${StatusUIHandler.PAUSED_OPACITY}`;
        break;
      default:
        icon.style.opacity = `${StatusUIHandler.NORMAL_OPACITY}`;
        break;
    }
  }
}
