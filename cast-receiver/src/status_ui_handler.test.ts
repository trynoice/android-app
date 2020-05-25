import StatusUIHandler from "./status_ui_handler";
import { PlayerStatusEventType } from "./types";

describe("StatusUIHandler", () => {
  let container: HTMLDivElement;
  let handler: StatusUIHandler;

  beforeEach(() => {
    container = document.createElement("div");
    handler = new StatusUIHandler(container);
  });

  describe("#showIdleStatus()", () => {
    it("should display idle status", () => {
      handler.showIdleStatus();
      expect(
        container.querySelector(`#${StatusUIHandler.READY_TO_CAST_ID}`)
      ).toBeTruthy();
    });

    it("should clear the container before displaying idle status", () => {
      container.innerHTML = `<span id="test">test</span>`;
      handler.showIdleStatus();
      expect(container.querySelector("#test")).not.toBeTruthy();
      expect(
        container.querySelector(`#${StatusUIHandler.READY_TO_CAST_ID}`)
      ).toBeTruthy();
    });
  });

  describe("#handlePlayerStatusEvent()", () => {
    describe("idle status", () => {
      beforeEach(() => {
        container.innerHTML = `<span id="${StatusUIHandler.READY_TO_CAST_ID}"></span>`;
      });

      it("should hide idle status on valid event", () => {
        handler.handlePlayerStatusEvent(PlayerStatusEventType.Added, "test");
        expect(
          container.querySelector(`#${StatusUIHandler.READY_TO_CAST_ID}`)
        ).not.toBeTruthy();
      });

      it("should not hide idle status on invalid event", () => {
        handler.handlePlayerStatusEvent(PlayerStatusEventType.Removed, "test");
        expect(
          container.querySelector(`#${StatusUIHandler.READY_TO_CAST_ID}`)
        ).toBeTruthy();
      });
    });

    it("should display icon with loading opacity on add event", () => {
      handler.handlePlayerStatusEvent(PlayerStatusEventType.Added, "test");
      const icon: HTMLElement = container.querySelector("#test");
      expect(icon).toBeTruthy();
      expect(icon.style.opacity).toEqual(`${StatusUIHandler.LOADING_OPACITY}`);
    });

    it("should display icon with paused opacity on pause event", () => {
      handler.handlePlayerStatusEvent(PlayerStatusEventType.Paused, "test");
      const icon: HTMLElement = container.querySelector("#test");
      expect(icon).toBeTruthy();
      expect(icon.style.opacity).toEqual(`${StatusUIHandler.PAUSED_OPACITY}`);
    });

    it("should display icon with normal opacity on start event", () => {
      handler.handlePlayerStatusEvent(PlayerStatusEventType.Started, "test");
      const icon: HTMLElement = container.querySelector("#test");
      expect(icon).toBeTruthy();
      expect(icon.style.opacity).toEqual(`${StatusUIHandler.NORMAL_OPACITY}`);
    });

    it("should not display icon on remove event", () => {
      // add then remove
      handler.handlePlayerStatusEvent(PlayerStatusEventType.Added, "test-1");
      handler.handlePlayerStatusEvent(PlayerStatusEventType.Removed, "test-1");

      // just remove
      handler.handlePlayerStatusEvent(PlayerStatusEventType.Removed, "test-2");

      // expecting both to be null
      expect(container.querySelector("#test-1")).not.toBeTruthy();
      expect(container.querySelector("#test-2")).not.toBeTruthy();
    });
  });
});
