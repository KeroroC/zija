import ElementPlus from "element-plus";
import { mount, type VueWrapper } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import NotFoundPage from "./NotFoundPage.vue";

const pushMock = vi.fn();

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: pushMock })
}));

describe("NotFoundPage", () => {
  let wrapper: VueWrapper | null = null;

  afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
    pushMock.mockReset();
  });

  it("shows a friendly 404 message and navigates home", async () => {
    wrapper = mount(NotFoundPage, { global: { plugins: [ElementPlus] } });

    expect(wrapper.text()).toContain("这一页不在家");

    await wrapper.get("button").trigger("click");
    expect(pushMock).toHaveBeenCalledWith({ name: "home" });
  });
});
