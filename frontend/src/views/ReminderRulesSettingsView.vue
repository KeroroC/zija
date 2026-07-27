<template>
  <div class="page-container-narrow">
    <header class="page-header">
      <div>
        <h2 class="page-title">提醒规则</h2>
        <p class="page-subtitle">家庭默认值（物品级可覆盖）</p>
      </div>
    </header>

    <el-form :model="form" label-width="120px" v-if="canEdit">
      <el-form-item label="临期提醒">
        <el-switch
          v-model="form.expiryDisabled"
          :active-value="false"
          :inactive-value="true"
        />
        <span class="hint">{{
          form.expiryDisabled ? "已关闭" : "开启"
        }}</span>
      </el-form-item>
      <el-form-item v-if="!form.expiryDisabled" label="提醒天数">
        <el-select
          v-model="form.expiryReminderDays"
          multiple
          filterable
          allow-create
          placeholder="输入天数回车添加（按降序，1-3650）"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="低库存提醒">
        <el-switch
          v-model="form.lowStockDisabled"
          :active-value="false"
          :inactive-value="true"
        />
      </el-form-item>
      <el-form-item v-if="!form.lowStockDisabled" label="低库存阈值">
        <el-input-number
          v-model="form.lowStockThreshold"
          :min="0"
          :step="1"
          :precision="3"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="save">保存</el-button>
      </el-form-item>
    </el-form>
    <p v-else class="readonly">仅管理员可修改。</p>

    <!-- 邮件提醒 -->
    <el-divider v-if="canEdit" />
    <div v-if="canEdit" class="mail-section">
      <h3 class="section-title">邮件提醒</h3>
      <el-form :model="mailForm" label-width="120px">
        <el-form-item label="SMTP 状态">
          <span :class="['zj-badge', mailForm.smtpConfigured ? 'zj-badge-pine' : 'zj-badge-plain']">
            {{ mailForm.smtpConfigured ? "已配置" : "未配置" }}
          </span>
        </el-form-item>
        <el-form-item label="摘要通知">
          <el-switch v-model="mailForm.digestEnabled" />
        </el-form-item>
        <el-form-item v-if="mailForm.digestEnabled" label="摘要频率">
          <el-radio-group v-model="mailForm.digestFrequency">
            <el-radio value="DAILY">每日</el-radio>
            <el-radio value="WEEKLY">每周</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="紧急通知">
          <el-switch v-model="mailForm.urgentEnabled" />
        </el-form-item>
        <el-form-item label="接收角色">
          <el-checkbox-group v-model="mailForm.recipientRoles">
            <el-checkbox value="OWNER">所有者</el-checkbox>
            <el-checkbox value="ADMIN">管理员</el-checkbox>
            <el-checkbox value="MEMBER">成员</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveMail">保存邮件设置</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, computed } from "vue";
import { ElMessage } from "element-plus";
import {
  fetchRules,
  updateRules,
  fetchMailSettings,
  updateMailSettings,
} from "../api/reminder";
import type { MailSetting } from "../api/reminder";
import { useSessionStore } from "../stores/session";
import { ApiError } from "../api/http";

const session = useSessionStore();
const canEdit = computed(
  () => session.role === "OWNER" || session.role === "ADMIN",
);
const form = reactive({
  expiryDisabled: false,
  expiryReminderDays: [] as number[],
  lowStockDisabled: false,
  lowStockThreshold: 1,
  version: 0,
});
const loaded = ref(false);

const mailForm = reactive<MailSetting>({
  digestEnabled: false,
  digestFrequency: "DAILY",
  urgentEnabled: false,
  recipientRoles: [],
  version: 0,
  smtpConfigured: false,
});

onMounted(async () => {
  await Promise.all([load(), loadMail()]);
});

async function load() {
  try {
    const r = await fetchRules();
    Object.assign(form, {
      expiryDisabled: r.expiryDisabled,
      expiryReminderDays: [...r.expiryReminderDays].sort((a, b) => b - a),
      lowStockDisabled: r.lowStockDisabled,
      lowStockThreshold: parseFloat(r.lowStockThreshold),
      version: r.version,
    });
    loaded.value = true;
  } catch (e) {
    if (e instanceof ApiError) ElMessage.error(e.message);
  }
}

async function loadMail() {
  try {
    const r = await fetchMailSettings();
    Object.assign(mailForm, r);
  } catch (e) {
    if (e instanceof ApiError) ElMessage.error(e.message);
  }
}

defineExpose({ save, saveMail });

async function save() {
  const body = {
    expiryDisabled: form.expiryDisabled,
    expiryReminderDays: form.expiryDisabled ? [] : form.expiryReminderDays,
    lowStockDisabled: form.lowStockDisabled,
    lowStockThreshold: String(form.lowStockThreshold),
    version: form.version,
  };
  try {
    const r = await updateRules(body);
    form.version = r.version;
    ElMessage.success("已保存");
  } catch (e) {
    if (
      e instanceof ApiError &&
      e.errorCode === "REMINDER_RULE_VERSION_CONFLICT"
    ) {
      ElMessage.warning("规则已被他人修改，已为您重新加载");
      await load();
    } else if (e instanceof ApiError) {
      ElMessage.error(e.message);
    }
  }
}

async function saveMail() {
  const body = {
    digestEnabled: mailForm.digestEnabled,
    digestFrequency: mailForm.digestFrequency,
    urgentEnabled: mailForm.urgentEnabled,
    recipientRoles: mailForm.recipientRoles,
    version: mailForm.version,
  };
  try {
    const r = await updateMailSettings(body);
    Object.assign(mailForm, r);
    ElMessage.success("邮件设置已保存");
  } catch (e) {
    if (
      e instanceof ApiError &&
      e.errorCode === "REMINDER_MAIL_SETTING_VERSION_CONFLICT"
    ) {
      ElMessage.warning("邮件设置已被他人修改，已为您重新加载");
      await loadMail();
    } else if (e instanceof ApiError) {
      ElMessage.error(e.message);
    }
  }
}
</script>

<style scoped>
.hint {
  margin-left: 12px;
  color: var(--zj-ink-400);
  font-size: 12px;
}
.readonly {
  color: var(--zj-ink-600);
}
.mail-section {
  margin-top: 24px;
}
.section-title {
  font-family: "Noto Serif SC Variable", serif;
  font-size: 16px;
  color: var(--zj-ink-900);
  margin-bottom: 16px;
}
</style>
