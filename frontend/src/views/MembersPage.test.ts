import ElementPlus, { ElOption } from "element-plus";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { householdApi } from "../api/household";
import { invitationApi } from "../api/invitation";
import { memberApi } from "../api/member";
import MembersPage from "./MembersPage.vue";
import type { MemberInfo } from "../types/identity";

vi.mock("../api/member", () => ({
  memberApi: {
    list: vi.fn(),
    updateRole: vi.fn(),
    updateStatus: vi.fn()
  }
}));

vi.mock("../api/invitation", () => ({
  invitationApi: {
    create: vi.fn()
  }
}));

vi.mock("../api/household", () => ({
  householdApi: {
    transferOwnership: vi.fn()
  }
}));

const listMock = vi.mocked(memberApi.list);
const updateRoleMock = vi.mocked(memberApi.updateRole);
const updateStatusMock = vi.mocked(memberApi.updateStatus);
const createInviteMock = vi.mocked(invitationApi.create);
const transferMock = vi.mocked(householdApi.transferOwnership);

const owner: MemberInfo = {
  id: "m-owner",
  accountId: "a-owner",
  username: "owner",
  displayName: "所有者",
  role: "OWNER",
  status: "ACTIVE"
};

const admin: MemberInfo = {
  id: "m-admin",
  accountId: "a-admin",
  username: "admin",
  displayName: "管理员",
  role: "ADMIN",
  status: "ACTIVE"
};

const member: MemberInfo = {
  id: "m-member",
  accountId: "a-member",
  username: "member",
  displayName: "成员",
  role: "MEMBER",
  status: "ACTIVE"
};

const deactivatedMember: MemberInfo = {
  ...member,
  id: "m-deactivated",
  accountId: "a-deactivated",
  username: "deactivated",
  displayName: "已停用成员",
  status: "DEACTIVATED"
};

const sessionState: {
  role: "OWNER" | "ADMIN" | "MEMBER" | null;
  currentMember: {
    householdId: string;
    memberId: string;
    accountId: string;
    username: string;
    displayName: string;
    role: "OWNER" | "ADMIN" | "MEMBER";
    status: "ACTIVE" | "DEACTIVATED";
  } | null;
  logout: ReturnType<typeof vi.fn>;
  clearLocalSession: ReturnType<typeof vi.fn>;
} = {
  role: "OWNER",
  currentMember: {
    householdId: "h1",
    memberId: owner.id,
    accountId: owner.accountId,
    username: owner.username,
    displayName: owner.displayName,
    role: "OWNER",
    status: "ACTIVE"
  },
  logout: vi.fn(),
  clearLocalSession: vi.fn()
};

const pushMock = vi.fn();

vi.mock("../stores/session", () => ({
  useSessionStore: () => sessionState
}));

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: pushMock })
}));

describe("MembersPage", () => {
  let wrapper: VueWrapper | null = null;

  beforeEach(() => {
    listMock.mockReset().mockResolvedValue([owner, admin, member]);
    updateRoleMock.mockReset().mockResolvedValue(undefined);
    updateStatusMock.mockReset().mockResolvedValue(undefined);
    createInviteMock.mockReset().mockResolvedValue({
      id: "inv-1",
      token: "raw-token",
      role: "MEMBER",
      expiresAt: "2026-07-21T12:00:00Z",
      path: "/invitation/redeem#token=raw-token"
    });
    transferMock.mockReset().mockResolvedValue(undefined);
    pushMock.mockReset();
    sessionState.logout.mockReset();
    sessionState.clearLocalSession.mockReset();
    sessionState.role = "OWNER";
    sessionState.currentMember = {
      householdId: "h1",
      memberId: owner.id,
      accountId: owner.accountId,
      username: owner.username,
      displayName: owner.displayName,
      role: "OWNER",
      status: "ACTIVE"
    };
  });

  afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
  });

  it("lists members and allows owner to create an invitation", async () => {
    wrapper = mount(MembersPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    expect(listMock).toHaveBeenCalledOnce();
    expect(wrapper.text()).toContain("owner");
    expect(wrapper.text()).toContain("admin");
    expect(wrapper.text()).toContain("member");

    await wrapper.get('[data-testid="create-invite"]').trigger("click");
    await wrapper.get('[data-testid="confirm-invite"]').trigger("click");
    await flushPromises();

    expect(createInviteMock).toHaveBeenCalledWith("MEMBER", 24);
    expect(wrapper.text()).toContain("/invitation/redeem#token=raw-token");
  });

  it("transfers ownership and forces re-login for the current owner", async () => {
    wrapper = mount(MembersPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    await wrapper.get('[data-testid="transfer-m-admin"]').trigger("click");
    await wrapper.get('[data-testid="confirm-transfer"]').trigger("click");
    await flushPromises();

    expect(transferMock).toHaveBeenCalledWith("m-admin");
    expect(sessionState.clearLocalSession).toHaveBeenCalledOnce();
    expect(pushMock).toHaveBeenCalledWith({ name: "login" });
  });

  it("allows the owner to create an administrator invitation", async () => {
    wrapper = mount(MembersPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    await wrapper.get('[data-testid="create-invite"]').trigger("click");
    wrapper.findComponent({ name: "ElSelect" }).vm.$emit("update:modelValue", "ADMIN");
    await wrapper.get('[data-testid="confirm-invite"]').trigger("click");
    await flushPromises();

    expect(createInviteMock).toHaveBeenCalledWith("ADMIN", 24);
  });

  it("hides invite and transfer actions for ordinary members", async () => {
    sessionState.role = "MEMBER";
    sessionState.currentMember = {
      householdId: "h1",
      memberId: member.id,
      accountId: member.accountId,
      username: member.username,
      displayName: member.displayName,
      role: "MEMBER",
      status: "ACTIVE"
    };

    wrapper = mount(MembersPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    expect(wrapper.find('[data-testid="create-invite"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="transfer-m-admin"]').exists()).toBe(false);
    expect(wrapper.findAll("tbody tr").every((row) => row.findAll("button").length === 0))
      .toBe(true);
  });

  it("enforces owner and administrator action boundaries", async () => {
    listMock.mockResolvedValue([owner, admin, member, deactivatedMember]);
    wrapper = mount(MembersPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    const ownerRow = wrapper.findAll("tbody tr").find((row) => row.text().includes(owner.username));
    const adminRow = wrapper.findAll("tbody tr").find((row) => row.text().includes(admin.username));
    const memberRow = wrapper.findAll("tbody tr").find((row) => row.text().includes(member.username));

    expect(ownerRow?.findAll("button")).toHaveLength(0);
    expect(adminRow?.text()).toContain("停用");
    expect(adminRow?.text()).toContain("取消管理员");
    expect(adminRow?.text()).toContain("转移所有权");
    expect(memberRow?.text()).toContain("停用");
    expect(memberRow?.text()).toContain("设为管理员");
    expect(memberRow?.text()).toContain("转移所有权");
    expect(wrapper.find('[data-testid="transfer-m-deactivated"]').exists()).toBe(false);

    wrapper.unmount();
    sessionState.role = "ADMIN";
    sessionState.currentMember = {
      householdId: "h1",
      memberId: admin.id,
      accountId: admin.accountId,
      username: admin.username,
      displayName: admin.displayName,
      role: "ADMIN",
      status: "ACTIVE"
    };
    wrapper = mount(MembersPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    const adminViewOwnerRow = wrapper.findAll("tbody tr")
      .find((row) => row.text().includes(owner.username));
    const adminViewSelfRow = wrapper.findAll("tbody tr")
      .find((row) => row.text().includes(admin.username));
    const adminViewMemberRow = wrapper.findAll("tbody tr")
      .find((row) => row.text().includes(member.username));

    expect(wrapper.find('[data-testid="create-invite"]').exists()).toBe(true);
    expect(adminViewOwnerRow?.findAll("button")).toHaveLength(0);
    expect(adminViewSelfRow?.findAll("button")).toHaveLength(0);
    expect(adminViewMemberRow?.text()).toContain("停用");
    expect(adminViewMemberRow?.text()).not.toContain("设为管理员");
    expect(wrapper.find('[data-testid^="transfer-"]').exists()).toBe(false);

    await wrapper.get('[data-testid="create-invite"]').trigger("click");
    expect(wrapper.findAllComponents(ElOption).map((option) => option.props("value")))
      .toEqual(["MEMBER"]);
  });

  it("keeps the current session when ownership transfer fails", async () => {
    transferMock.mockRejectedValueOnce(new Error("transfer rejected"));
    wrapper = mount(MembersPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    await wrapper.get('[data-testid="transfer-m-admin"]').trigger("click");
    await wrapper.get('[data-testid="confirm-transfer"]').trigger("click");
    await flushPromises();

    expect(sessionState.clearLocalSession).not.toHaveBeenCalled();
    expect(pushMock).not.toHaveBeenCalled();
    expect(wrapper.get('[data-testid="confirm-transfer"]').isVisible()).toBe(true);
  });
});
