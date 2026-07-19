import ElementPlus from "element-plus";
import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { fetchSystemInfo } from "../api/system";
import SystemStatusView from "./SystemStatusView.vue";

vi.mock("../api/system", () => ({
  fetchSystemInfo: vi.fn()
}));

const fetchSystemInfoMock = vi.mocked(fetchSystemInfo);

describe("SystemStatusView", () => {
  beforeEach(() => {
    fetchSystemInfoMock.mockReset();
  });

  it("shows live backend and database status", async () => {
    fetchSystemInfoMock.mockResolvedValue({
      application: "zija",
      version: "0.1.0",
      status: "UP",
      installationId: "34bf30dd-d082-4e26-9dfe-8f30421f4772",
      databaseTime: "2026-07-19T12:00:00Z"
    });

    const wrapper = mount(SystemStatusView, {
      global: {
        plugins: [ElementPlus]
      }
    });
    await flushPromises();

    expect(wrapper.text()).toContain("系统运行正常");
    expect(wrapper.text()).toContain("0.1.0");
    expect(wrapper.text()).toContain("PostgreSQL 已连接");
    wrapper.unmount();
  });

  it("shows a recoverable error when the API is unavailable", async () => {
    fetchSystemInfoMock.mockRejectedValue(
      new Error("System state unavailable")
    );

    const wrapper = mount(SystemStatusView, {
      global: {
        plugins: [ElementPlus]
      }
    });
    await flushPromises();

    expect(wrapper.text()).toContain("暂时无法读取系统状态");
    wrapper.unmount();
  });
});
