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
        <div class="qa-scope-bar">
          <el-segmented v-model="scopeMode" :options="scopeOptions" :disabled="submitting" />
          <el-select
            v-if="scopeMode !== 'FACT'"
            v-model="selectedScopeId"
            data-testid="qa-scope-select"
            filterable
            :loading="scopeLoading"
            :disabled="submitting"
            :placeholder="scopeMode === 'ITEM' ? '选择物品' : '选择批次'"
            class="qa-scope-select"
          >
            <el-option
              v-for="option in scopeChoices"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </div>
        <el-input
          v-model="question"
          type="textarea"
          :rows="2"
          resize="none"
          maxlength="2000"
          :placeholder="questionPlaceholder"
          :disabled="submitting"
          class="qa-input"
          @keydown.enter.exact.prevent="submit"
        />
        <div class="qa-composer-footer">
          <span class="qa-hint">{{ scopeHint }}</span>
          <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="submit">
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
            <div v-if="turn.answer.reasonCode !== 'ANSWERED'" class="qa-unavailable">
              <span class="zj-badge zj-badge-warn">{{ turn.answer.reasonCode }}</span>
              <p class="qa-summary">{{ turn.answer.summary }}</p>
              <el-button
                v-if="attachmentEntry(turn.answer.jumps)"
                text
                data-testid="qa-attachment-entry"
                class="qa-attachment-entry"
                @click="goJump(attachmentEntry(turn.answer.jumps)!)"
              >
                <el-icon><Paperclip /></el-icon>
                {{ attachmentEntry(turn.answer.jumps)?.label ?? "附件管理" }}
              </el-button>
            </div>
            <template v-else>
              <p class="qa-summary">{{ turn.answer.summary }}</p>

              <!-- 回答依据：来源类别 + 数据时间 -->
              <div class="qa-sources">
                <span
                  v-for="(source, j) in factSources(turn.answer.sources)"
                  :key="j"
                  class="zj-badge zj-badge-pine"
                >
                  {{ source.label }}
                </span>
                <span class="qa-datetime" v-if="turn.answer.dataTime">
                  数据时间 {{ formatDateTime(turn.answer.dataTime) }}
                </span>
              </div>

              <div v-if="knowledgeSources(turn.answer.sources).length" class="qa-groundings">
                <article
                  v-for="(source, j) in knowledgeSources(turn.answer.sources)"
                  :key="`${source.attachmentId ?? 'source'}-${j}`"
                  class="qa-grounding"
                >
                  <header class="qa-grounding-header">
                    <el-button text class="qa-grounding-file" @click="openSource(source)">
                      <el-icon><Paperclip /></el-icon>
                      {{ source.attachmentName ?? source.label }}
                    </el-button>
                    <span class="zj-badge zj-badge-plain">{{ source.mountLabel }}</span>
                  </header>
                  <p v-if="evidenceLocation(source)" class="qa-grounding-location">
                    {{ evidenceLocation(source) }}
                  </p>
                  <blockquote v-if="source.excerpt" class="qa-grounding-excerpt">
                    {{ source.excerpt }}
                  </blockquote>
                </article>
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
                  <el-icon class="qa-jump-icon">
                    <Paperclip v-if="jump.type === 'ATTACHMENT'" />
                    <Location v-else />
                  </el-icon>
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
        <p class="qa-empty-title">问问家里的物品与资料</p>
        <p class="qa-empty-hint">
          试试「牛奶还有多少？」「哪些批次快到期了？」「看看低库存物品」或
          「牛奶最近有没有入库？」
        </p>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { ChatDotRound, Location, Paperclip } from "@element-plus/icons-vue";
import { askHouseholdQuestion } from "../api/ai";
import { fetchItems } from "../api/catalog";
import { fetchLots } from "../api/inventory";
import type {
  HouseholdFactAnswer,
  QaAnswerSource,
  QaJump,
  QaQuestionScope,
} from "../types/ai";
import type { CatalogItem } from "../types/catalog";
import type { LotSummary } from "../types/inventory";
import { ApiError } from "../api/http";

const router = useRouter();
const question = ref("");
const submitting = ref(false);
const turns = ref<Array<{ question: string; answer: HouseholdFactAnswer }>>([]);
const scopeMode = ref<"FACT" | "ITEM" | "LOT">("FACT");
const selectedScopeId = ref("");
const scopeLoading = ref(false);
const items = ref<CatalogItem[]>([]);
const lots = ref<LotSummary[]>([]);
const SCOPE_PAGE_SIZE = 100;
const scopeOptions = [
  { label: "家庭事实", value: "FACT" },
  { label: "物品资料", value: "ITEM" },
  { label: "批次资料", value: "LOT" },
];
let scopeLoadGeneration = 0;

const scopeChoices = computed(() => {
  if (scopeMode.value === "ITEM") {
    return items.value.map((item) => ({
      value: item.id,
      label: item.status === "ARCHIVED" ? `${item.name}（已归档）` : item.name,
    }));
  }
  if (scopeMode.value === "LOT") {
    return lots.value.map((lot) => ({ value: lot.lotId, label: lotLabel(lot) }));
  }
  return [];
});

const canSubmit = computed(
  () => Boolean(question.value.trim())
    && !submitting.value
    && (scopeMode.value === "FACT" || Boolean(selectedScopeId.value)),
);

const scopeHint = computed(() => scopeMode.value === "FACT"
  ? "仅查询当前家庭事实，不修改任何数据"
  : "仅依据当前范围内已准备好的附件回答");

const questionPlaceholder = computed(() => scopeMode.value === "FACT"
  ? "例如：牛奶还有多少、放在哪里？哪些批次快到期了？"
  : "例如：滤网怎么清洁？出现异响时如何处理？");

watch(scopeMode, async (mode) => {
  selectedScopeId.value = "";
  if (mode === "FACT") return;
  const generation = ++scopeLoadGeneration;
  scopeLoading.value = true;
  try {
    if (mode === "ITEM" && items.value.length === 0) {
      items.value = await loadAllScopeOptions((page) => fetchItems({
        page,
        pageSize: SCOPE_PAGE_SIZE,
      }));
    } else if (mode === "LOT" && lots.value.length === 0) {
      lots.value = await loadAllScopeOptions((page) => fetchLots({
        page,
        pageSize: SCOPE_PAGE_SIZE,
      }));
    }
  } catch {
    ElMessage.error(mode === "ITEM" ? "物品列表加载失败" : "批次列表加载失败");
  } finally {
    if (generation === scopeLoadGeneration) {
      scopeLoading.value = false;
    }
  }
});

async function loadAllScopeOptions<T>(
  fetchPage: (page: number) => Promise<{ items: T[]; total: number }>,
): Promise<T[]> {
  const all: T[] = [];
  for (let page = 1; ; page += 1) {
    const result = await fetchPage(page);
    all.push(...result.items);
    if (all.length >= result.total || result.items.length === 0) {
      return all;
    }
  }
}

async function submit() {
  const text = question.value.trim();
  if (!text || submitting.value) return;
  submitting.value = true;
  try {
    const scope: QaQuestionScope | undefined = scopeMode.value === "FACT"
      ? undefined
      : { type: scopeMode.value, id: selectedScopeId.value };
    const answer = scope
      ? await askHouseholdQuestion(text, scope)
      : await askHouseholdQuestion(text);
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
    case "ATTACHMENT":
      router.push({ path: "/files", query: jump.attachmentId ? { highlight: jump.attachmentId } : {} });
      break;
    default:
      break;
  }
}

function factSources(sources: QaAnswerSource[]): QaAnswerSource[] {
  return sources.filter((source) => source.category !== "KNOWLEDGE_SOURCE");
}

function knowledgeSources(sources: QaAnswerSource[]): QaAnswerSource[] {
  return sources.filter((source) => source.category === "KNOWLEDGE_SOURCE");
}

function attachmentEntry(jumps: QaJump[]): QaJump | undefined {
  return jumps.find((jump) => jump.type === "ATTACHMENT");
}

function openSource(source: QaAnswerSource) {
  goJump({ type: "ATTACHMENT", label: source.attachmentName ?? source.label,
    attachmentId: source.attachmentId });
}

function evidenceLocation(source: QaAnswerSource): string {
  const parts: string[] = [];
  if (source.pageNumber != null) parts.push(`第 ${source.pageNumber} 页`);
  if (source.sectionPath) parts.push(source.sectionPath);
  if (source.charStart != null && source.charEnd != null) {
    parts.push(`字符 ${source.charStart}-${source.charEnd}`);
  }
  return parts.join(" · ");
}

function lotLabel(lot: LotSummary): string {
  return `${lot.itemName} · ${lot.lotNumber || lot.serialNumber || "未编号批次"}`;
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

.qa-scope-bar {
  display: flex;
  align-items: center;
  gap: var(--zj-space-3);
  margin-bottom: var(--zj-space-3);
}

.qa-scope-select {
  flex: 1;
  max-width: 360px;
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

.qa-groundings {
  display: grid;
  gap: var(--zj-space-2);
  margin-bottom: var(--zj-space-3);
}

.qa-grounding {
  border-left: 3px solid var(--zj-pine-500);
  background: var(--zj-pine-50);
  padding: var(--zj-space-2) var(--zj-space-3);
}

.qa-grounding-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zj-space-2);
}

.qa-grounding-file {
  min-width: 0;
  padding: 0;
  color: var(--zj-pine-600);
}

.qa-grounding-location {
  margin: var(--zj-space-1) 0 0;
  color: var(--zj-ink-400);
  font-size: var(--zj-text-caption);
  font-variant-numeric: tabular-nums;
}

.qa-grounding-excerpt {
  margin: var(--zj-space-2) 0 0;
  color: var(--zj-ink-600);
  font-size: var(--zj-text-body-sm);
  line-height: 1.6;
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

.qa-attachment-entry {
  align-self: flex-start;
  color: var(--zj-pine-600);
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

@media (max-width: 720px) {
  .qa-scope-bar {
    align-items: stretch;
    flex-direction: column;
  }

  .qa-scope-select {
    max-width: none;
  }

  .qa-composer-footer,
  .qa-grounding-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .qa-question,
  .qa-answer {
    max-width: 100%;
  }
}
</style>
