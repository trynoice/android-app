import StatusUIHandler from "./status_ui_handler";

describe("StatusUIHandler", () => {
  let container: HTMLDivElement;
  let handler: StatusUIHandler;

  beforeEach(() => {
    container = document.createElement("div");
    handler = new StatusUIHandler(container);
  });

  describe("#enableStatus()", () => {
    it("should enable/disable valid status IDs", () => {
      const statusIDs = [
        StatusUIHandler.IDLE_STATUS_ID,
        StatusUIHandler.CASTING_STATUS_ID,
        StatusUIHandler.LOADER_STATUS_ID,
      ];

      statusIDs.forEach((statusID: string) => {
        handler.enableStatus(statusID, true);
        expect(container.querySelector(`#${statusID}`)).toBeTruthy();
        handler.enableStatus(statusID, false);
        expect(container.querySelector(`#${statusID}`)).not.toBeTruthy();
      });
    });

    it("should do noop for invalid status ID", () => {
      const statusID = "invalid";
      handler.enableStatus(statusID, true);
      expect(container.querySelector(`#${statusID}`)).not.toBeTruthy();
    });
  });
});
