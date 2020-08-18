import { Icons } from "./library";

/**
 * StatusUIHandler is responsible for managing status UI of the application.
 * It manages the div container passed via the constructor argument.
 */
export default class StatusUIHandler {
  static readonly IDLE_STATUS_ID = "idle";
  static readonly CASTING_STATUS_ID = "casting";
  static readonly LOADER_STATUS_ID = "loader";

  private static readonly IDLE_STATUS_ICON = "cast";
  private static readonly CASTING_STATUS_ICON = "cast";
  private static readonly LOADER_STATUS_ICON = "loader";

  private static readonly IDLE_STATUS_MSG = "Ready to cast";
  private static readonly CASTING_STATUS_MSG = "A device is casting";

  private container: HTMLDivElement;
  private statusElements: Map<string, HTMLElement> = new Map();

  constructor(statusContainer: HTMLDivElement) {
    this.container = statusContainer;
    this.statusElements.set(
      StatusUIHandler.IDLE_STATUS_ID,
      this.createStatusElement(
        StatusUIHandler.IDLE_STATUS_ID,
        StatusUIHandler.IDLE_STATUS_ICON,
        StatusUIHandler.IDLE_STATUS_MSG
      )
    );

    this.statusElements.set(
      StatusUIHandler.CASTING_STATUS_ID,
      this.createStatusElement(
        StatusUIHandler.CASTING_STATUS_ID,
        StatusUIHandler.CASTING_STATUS_ICON,
        StatusUIHandler.CASTING_STATUS_MSG
      )
    );

    this.statusElements.set(
      StatusUIHandler.LOADER_STATUS_ID,
      this.createStatusElement(
        StatusUIHandler.LOADER_STATUS_ID,
        StatusUIHandler.LOADER_STATUS_ICON,
        ""
      )
    );
  }

  private createIcon(id: string): HTMLElement {
    const template = document.createElement("template");
    template.innerHTML = `<svg id="${id}" class="icon"><use xlink:href="${Icons[id]}" /><svg>`;
    return template.content.firstChild as HTMLElement;
  }

  private createStatusElement(
    id: string,
    iconID: string,
    message: string
  ): HTMLSpanElement {
    const element = document.createElement("div");
    element.classList.add("statusline", "caption");
    element.id = id;
    element.appendChild(this.createIcon(iconID));
    element.appendChild(document.createTextNode(message));
    return element;
  }

  /**
   * Enables the status with given ID.
   *
   * @param statusID ID of status to toggle. must be one of
   * StatusUIHandler.IDLE_STATUS_ID or StatusUIHandler.CASTING_STATUS_ID
   * @param enabled whether to display or hide the given status
   */
  enableStatus(statusID: string, enabled: boolean): void {
    if (this.statusElements.has(statusID) === false) {
      return;
    }

    const isShowing = this.isShowingStatus(statusID);
    if ((isShowing && enabled) || (!isShowing && !enabled)) {
      return;
    }

    const element = this.statusElements.get(statusID);
    if (enabled) {
      this.container.appendChild(element);
    } else {
      this.container.removeChild(element);
    }
  }

  private isShowingStatus(id: string): boolean {
    return (
      this.statusElements.has(id) &&
      this.container.querySelector(`#${id}`) != null
    );
  }
}
