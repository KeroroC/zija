<template>
  <div class="page-container qa-page">
    <header class="page-header">
      <div>
        <h1 class="page-title">家庭问答</h1>
        <p class="page-subtitle">用自然语言查询物品、批次、库存位、位置、流水与提醒</p>
      </div>
    </header>

    <section class="qa-shell">
      <!-- 输入区 -->
      <div class="qa-composer">
        <el-input
          v-model="question"
          type="textarea"
          :rows="2"
          resize="none"
          maxlength="2000"
          placeholder="例如：牛奶还有多少、放在哪里？哪些批次快到期了？"
          :disabled="submitting"
          class="qa-input"
          @keydown.enter.exact.prevent="submit"
        />
        <div class="qa-composer-footer">
          <span class="qa-hint">仅查询当前家庭事实，不修改任何数据</span>
          <el-button type="primary" :loading="submitting" :disabled="!question.trim()" @click="submit">
            提问
          </el-button>
        </div>
      </div>

      <!-- 对话记录（仅当前浏览器会话，不存服务端） -->
      <div v-if="turns.length" class="qa-thread">
        <div v-for="(turn, i) in turns" :key="i" class="qa-turn">
          <div class="qa-question">
            <span class="qa-question-label">问</span>
            <span class="qa-question-text">{{ turn.question }}</span>
          </div>

          <div class="qa-answer">
            <div v-if="!turn.answer.modelAvailable" class="qa-unavailable">
              <span class="zj-badge zj-badge-warn">{{ turn.answer.reasonCode }}</span>
              <p class="qa-summary">{{ turn.answer.summary }}</p>
            </div>
            <template v-else>
              <p class="qa-summary">{{ turn.answer.summary }}</p>

              <!-- 回答依据：来源类别 + 数据时间 -->
              <div class="qa-sources">
                <span
                  v-for="(source, j) in turn.answer.sources"
                  :key="j"
                  class="zj-badge zj-badge-pine"
                >
                  {{ source.label }}
                </span>
                <span class="qa-datetime" v-if="turn.answer.dataTime">
                  数据时间 {{ formatDateTime(turn.answer.dataTime) }}
                </span>
              </div>

              <!-- 权威页面跳转 -->
              <div v-if="turn.answer.jumps.length" class="qa-jumps">
                <el-button
                  v-for="(jump, j) in turn.answer.jumps"
                  :key="j"
                  size="small"
                  text
                  class="qa-jump"
                  @click="goJump(jump)"
                >
                  <el-icon class="qa-jump-icon"><Location /></el-icon>
                  {{ jump.label }}
                </el-button>
              </div>

              <!-- 结构化结果 -->
              <div
                v-for="(result, r) in turn.answer.structuredResults"
                :key="r"
                class="qa-result"
              >
                <div class="qa-result-title">{{ result.title }}</div>
                <el-table
                  v-if="result.rows.length"
                  :data="result.rows"
                  size="small"
                  class="qa-result-table"
                >
                  <el-table-column
                    v-for="col in columnsOf(result.rows)"
                    :key="col"
                    :prop="col"
                    :label="col"
                    min-width="96"
                  />
                </el-table>
                <p v-else class="qa-result-empty">暂无数据</p>
              </div>
            </template>
          </div>
        </div>
      </div>

      <div v-else-if="!submitting" class="qa-empty">
        <div class="qa-empty-icon" aria-hidden="true">
          <el-icon><ChatDotRound /></el-icon>
        </div>
        <p class="qa-empty-title">问问家里的库存情况</p>
        <p class="qa-empty-hint">
          试试「牛奶还有多少？」「哪些批次快到期了？」「看看低库存物品」或
          「牛奶最近有没有入库？」
        </p>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { ChatDotRound, Location } from "@element-plus/icons-vue";
import { askHouseholdQuestion } from "../api/ai";
import type { HouseholdFactAnswer, QaJump } from "../types/ai";
import { ApiError } from "../api/http";

const router = useRouter();
const question = ref("");
const submitting = ref(false);
const turns = ref<Array<{ question: string; answer: HouseholdFactAnswer }>>([]);

async function submit() {
  const text = question.value.trim();
  if (!text || submitting.value) return;
  submitting.value = true;
  try {
    const answer = await askHouseholdQuestion(text);
    turns.value.push({ question: text, answer });
    question.value = "";
  } catch (e) {
    if (e instanceof ApiError) {
      ElMessage.error(e.message);
    } else {
      ElMessage.error("提问失败，请稍后重试");
    }
  } finally {
    submitting.value = false;
  }
}

/** 从行数据推断列名（保持插入顺序）。 */
function columnsOf(rows: Array<Record<string, string>>): string[] {
  const cols: string[] = [];
  for (const row of rows) {
    for (const key of Object.keys(row)) {
      if (!cols.includes(key)) cols.push(key);
    }
  }
  return cols;
}

function goJump(jump: QaJump) {
  switch (jump.type) {
    case "ITEM":
      router.push({ path: "/items", query: { highlight: jump.itemId ?? "" } });
      break;
    case "LOT":
      router.push({ name: "inventory", query: { lotId: jump.lotId ?? "" } });
      break;
    case "LOCATION":
      router.push({ path: "/locations", query: { highlight: jump.locationId ?? "" } });
      break;
    case "MOVEMENT":
      router.push({ name: "report-movements" });
      break;
    case "REMINDER":
      router.push({ name: "reminders" });
      break;
    default:
      break;
  }
}

function formatDateTime(iso: string): string {
  try {
    return new Date(iso).toLocaleString("zh-CN", { hour12: false });
  } catch {
    return iso;
  }
}
</script>

<style scoped>
.qa-page {
  max-width: 1120px;
}

.qa-shell {
  display: flex;
  flex-direction: column;
  gap: var(--zj-space-5);
}

/* ---------- 输入区 ---------- */
.qa-composer {
  background: var(--zj-surface);
  border: 1px solid var(--zj-line);
  border-radius: var(--zj-radius-md);
  padding: var(--zj-space-4);
  box-shadow: var(--zj-shadow-sm);
}

.qa-input :deep(.el-textarea__inner) {
  background: var(--zj-surface);
  color: var(--zj-ink-900);
}

.qa-composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--zj-space-3);
}

.qa-hint {
  font-size: var(--zj-text-caption);
  color: var(--zj-ink-400);
}

/* ---------- 对话记录 ---------- */
.qa-thread {
  display: flex;
  flex-direction: column;
  gap: var(--zj-space-5);
}

.qa-turn {
  display: flex;
  flex-direction: column;
  gap: var(--zj-space-2);
}

.qa-question {
  display: flex;
  align-items: flex-start;
  gap: var(--zj-space-2);
  align-self: flex-end;
  max-width: 70%;
}

.qa-question-label {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--zj-pine-600);
  color: var(--zj-on-dark-100);
  font-size: 12px;
  line-height: 20px;
  text-align: center;
}

.qa-question-text {
  background: var(--zj-pine-50);
  border: 1px solid var(--zj-pine-100);
  border-radius: var(--zj-radius-md);
  padding: var(--zj-space-2) var(--zj-space-3);
  color: var(--zj-ink-900);
  font-size: var(--zj-text-body-sm);
}

.qa-answer {
  background: var(--zj-surface);
  border: 1px solid var(--zj-line);
  border-radius: var(--zj-radius-md);
  padding: var(--zj-space-4);
  box-shadow: var(--zj-shadow-sm);
  max-width: 90%;
}

.qa-summary {
  margin: 0 0 var(--zj-space-3);
  color: var(--zj-ink-900);
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.qa-sources {
  display: flex;
  align-items: center;
  gap: var(--zj-space-2);
  margin-bottom: var(--zj-space-3);
}

.qa-datetime {
  font-size: var(--zj-text-caption);
  color: var(--zj-ink-400);
  font-variant-numeric: tabular-nums;
}

.qa-jumps {
  display: flex;
  flex-wrap: wrap;
  gap: var(--zj-space-1);
  margin-bottom: var(--zj-space-3);
}

.qa-jump {
  color: var(--zj-pine-600);
}

.qa-jump-icon {
  margin-right: 2px;
}

.qa-result {
  margin-top: var(--zj-space-3);
  border-top: 1px solid var(--zj-line);
  padding-top: var(--zj-space-3);
}

.qa-result-title {
  font-size: var(--zj-text-body-sm);
  font-weight: 600;
  color: var(--zj-ink-600);
  margin-bottom: var(--zj-space-2);
}

.qa-result-table {
  width: 100%;
}

.qa-result-empty {
  color: var(--zj-ink-400);
  font-size: var(--zj-text-body-sm);
}

.qa-unavailable {
  display: flex;
  flex-direction: column;
  gap: var(--zj-space-2);
}

.zj-badge-warn {
  align-self: flex-start;
  border-color: var(--zj-warning);
  color: var(--zj-warning);
}

/* ---------- 空状态 ---------- */
.qa-empty {
  padding: 56px 0 64px;
  text-align: center;
}

.qa-empty-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  margin: 0 auto 16px;
  border-radius: var(--zj-radius-md);
  background: var(--zj-surface-sunken);
  color: var(--zj-ink-300);
}

.qa-empty-icon .el-icon {
  font-size: 24px;
}

.qa-empty-title {
  margin: 0;
  font-family: var(--zj-serif);
  font-size: 18px;
  font-weight: 600;
  color: var(--zj-ink-900);
}

.qa-empty-hint {
  margin: 8px auto 0;
  max-width: 440px;
  font-size: 13px;
  color: var(--zj-ink-400);
}
</style>
