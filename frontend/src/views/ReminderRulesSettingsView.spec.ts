import { describe, it, expect, vi, beforeEach } from "vitest";
import { mount, flushPromises } from "@vue/test-utils";
import ElementPlus from "element-plus";

vi.mock("../stores/session", () => ({
  useSessionStore: () => ({ role: "OWNER" }),
}));
vi.mock("../api/reminder", () => ({
  fetchRules: vi.fn(),
  updateRules: vi.fn(),
}));

import ReminderRulesSettingsView from "./ReminderRulesSettingsView.vue";
import { fetchRules, updateRules } from "../api/reminder";
import { ApiError } from "../api/http";

beforeEach(() => {
  vi.clearAllMocks();
  (fetchRules as ReturnType<typeof vi.fn>).mockResolvedValue({
    expiryDisabled: false,
    expiryReminderDays: [30, 7, 1],
    lowStockDisabled: false,
    lowStockThreshold: "1",
    version: 0,
  });
});

const mountV = () =>
  mount(ReminderRulesSettingsView, {
    global: { plugins: [ElementPlus] },
  });

describe("ReminderRulesSettingsView", () => {
  it("loads rules on mount", async () => {
    const w = mountV();
    await flushPromises();
    expect(fetchRules).toHaveBeenCalled();
    expect(w.text()).toContain("提醒规则");
  });

  it("saving with stale version shows conflict and reloads", async () => {
    (updateRules as ReturnType<typeof vi.fn>).mockRejectedValueOnce(
      new ApiError("版本冲突", "REMINDER_RULE_VERSION_CONFLICT", 409),
    );
    const w = mountV();
    await flushPromises();
    await (w.vm as any).save();
    await flushPromises();
    expect(updateRules).toHaveBeenCalled();
    expect(fetchRules).toHaveBeenCalledTimes(2); // initial + reload after conflict
  });

  it("disables days input when expiry switch off", async () => {
    (fetchRules as ReturnType<typeof vi.fn>).mockResolvedValue({
      expiryDisabled: true,
      expiryReminderDays: [],
      lowStockDisabled: false,
      lowStockThreshold: "1",
      version: 0,
    });
    const w = mountV();
    await flushPromises();
    expect(w.text()).toContain("提醒规则");
  });
});
