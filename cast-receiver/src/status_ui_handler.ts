import { PlayerStatusEventType } from "noice/types";
import { Icons } from "noice/library";

export default class StatusUIHandler {
  private static readonly READY_TO_CAST_MSG = "Ready to cast";
  private static readonly LOADING_OPACITY = 0.5;
  private static readonly PAUSED_OPACITY = 0.65;
  private static readonly NORMAL_OPACITY = 0.9;

  private container: HTMLDivElement;
  private readyToCastElement: HTMLSpanElement;

  constructor(statusContainer: HTMLDivElement) {
    this.container = statusContainer;
    this.readyToCastElement = document.createElement("span");
    this.readyToCastElement.classList.add("caption");
    this.readyToCastElement.id = "ready-to-cast";
    this.readyToCastElement.appendChild(this.createIcon("cast"));
    this.readyToCastElement.appendChild(
      document.createTextNode(StatusUIHandler.READY_TO_CAST_MSG)
    );
  }

  private createIcon(id: string): HTMLElement {
    const template = document.createElement("template");
    template.innerHTML = `<svg id="${id}" class="sound"><use xlink:href="${Icons[id]}" /><svg>`;
    return template.content.firstChild as HTMLElement;
  }

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
