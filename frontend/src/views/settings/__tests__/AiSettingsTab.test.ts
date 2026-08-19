import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import ElementPlus from "element-plus";

vi.mock("../../../api/ai", () => ({
  fetchAiSettings: vi.fn(),
  fetchAiStatus: vi.fn(),
  updateAiSettings: vi.fn(),
}));
vi.mock("../../../stores/session", () => ({
  useSessionStore: () => ({ role: "OWNER" }),
}));

import AiSettingsTab from "../AiSettingsTab.vue";
import {
  fetchAiSettings,
  fetchAiStatus,
  updateAiSettings,
} from "../../../api/ai";
import { ApiError } from "../../../api/http";
import { AI_CONFIGURATION_VERSION_CONFLICT } from "../../../types/errorCodes";
import { Refresh } from "@element-plus/icons-vue";

const settings = {
  enabled: false,
  providerId: "ollama",
  credentialConfigured: false,
  outboundEnabled: false,
  requestsPerMinute: 20,
  memberRequestsPerMinute: 10,
  maxContextTokens: 8192,
  maxConcurrentRequests: 2,
  requestTimeoutSeconds: 30,
  version: 0,
};

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(fetchAiSettings).mockResolvedValue(settings);
  vi.mocked(fetchAiStatus).mockResolvedValue({
    available: false,
    reasonCode: "AI_DISABLED",
    detail: "AI is disabled",
    providerId: "ollama",
    chatModel: null,
    embeddingModel: null,
    outboundEnabled: false,
    requestsPerMinute: 20,
    memberRequestsPerMinute: 10,
    maxContextTokens: 8192,
    maxConcurrentRequests: 2,
    requestTimeoutSeconds: 30,
  });
  vi.mocked(updateAiSettings).mockResolvedValue({
    ...settings,
    enabled: true,
    credentialConfigured: true,
    version: 1,
  });
});

const mountView = () => mount(AiSettingsTab, {
  global: { plugins: [ElementPlus], components: { Refresh } },
});

describe("AiSettingsTab", () => {
  it("shows an explicit unavailable status and editable resource limits", async () => {
    const wrapper = mountView();
    await flushPromises();

    expect(fetchAiSettings).toHaveBeenCalledOnce();
    expect(fetchAiStatus).toHaveBeenCalledOnce();
    expect(wrapper.text()).toContain("不可用");
    expect(wrapper.text()).toContain("每分钟请求数");
    expect(wrapper.text()).toContain("上下文上限");
  });

  it("saves a write-only credential and refreshes provider status", async () => {
    const wrapper = mountView();
    await flushPromises();

    const vm = wrapper.vm as unknown as {
      form: typeof settings & { credential: string; clearCredential: boolean };
      save: () => Promise<void>;
    };
    vm.form.enabled = true;
    vm.form.credential = "provider-secret";
    await vm.save();
    await flushPromises();

    expect(updateAiSettings).toHaveBeenCalledWith(expect.objectContaining({
      enabled: true,
      credential: "provider-secret",
      clearCredential: false,
      memberRequestsPerMinute: 10,
      version: 0,
    }));
    expect(vm.form.credential).toBe("");
    expect(fetchAiStatus).toHaveBeenCalledTimes(2);
  });

  it("reloads settings after an optimistic-lock conflict", async () => {
    vi.mocked(updateAiSettings).mockRejectedValueOnce(
      new ApiError("版本冲突", AI_CONFIGURATION_VERSION_CONFLICT, 409),
    );
    const wrapper = mountView();
    await flushPromises();

    await (wrapper.vm as unknown as { save: () => Promise<void> }).save();
    await flushPromises();

    expect(fetchAiSettings).toHaveBeenCalledTimes(2);
  });
});
