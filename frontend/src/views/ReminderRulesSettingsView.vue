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
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, computed } from "vue";
import { ElMessage } from "element-plus";
import { fetchRules, updateRules } from "../api/reminder";
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

onMounted(async () => {
  await load();
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

defineExpose({ save });

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
</style>
