<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">全局搜索</h1>
        <p class="page-subtitle">跨物品、批次、位置搜索</p>
      </div>
    </div>

    <!-- 搜索输入 -->
    <div class="search-bar">
      <el-input
        v-model="query"
        placeholder="搜索物品、批次或位置"
        clearable
        size="large"
        class="search-input"
        @keyup.enter="doSearch"
        @input="onInput"
        @clear="clearResults"
      >
        <template #prefix>
          <el-icon class="search-input-icon"><Search /></el-icon>
        </template>
        <template #append>
          <el-button class="search-btn" @click="doSearch" :loading="loading">搜索</el-button>
        </template>
      </el-input>
      <p class="search-hint">支持名称、品牌、标签、分类、批次号、序列号与位置路径</p>
      <p v-if="searched" class="search-summary">共 <span class="zj-num">{{ resultCount }}</span> 条匹配</p>
    </div>

    <!-- 搜索结果 -->
    <div v-if="searched" class="search-results">
      <div v-if="resultCount === 0" class="search-none">
        <div class="search-none-icon" aria-hidden="true">
          <el-icon><Search /></el-icon>
        </div>
        <p class="search-none-title">没有找到相关内容</p>
        <p class="search-none-hint">换个关键词，或减少限定条件再试。</p>
      </div>

      <el-collapse v-else v-model="expandedGroups" class="search-collapse">
        <!-- 物品 -->
        <el-collapse-item name="items">
          <template #title>
            <span class="group-title">
              <el-icon class="group-icon"><Box /></el-icon>
              物品
              <span v-if="results.items.length" class="group-count zj-num">{{ results.items.length }}</span>
            </span>
          </template>
          <template v-if="results.items.length">
            <div v-for="item in results.items" :key="item.itemId" class="result-row"
                 role="button" tabindex="0" @click="goToItem(item.itemId)"
                 @keyup.enter="goToItem(item.itemId)">
              <div class="result-type" aria-hidden="true"><el-icon><Box /></el-icon></div>
              <div class="result-main">
                <div class="result-title">{{ item.name }}</div>
                <div class="result-meta">
                  <span v-if="item.brand"><span class="meta-label">品牌</span>{{ item.brand }}</span>
                  <span v-if="item.category"><span class="meta-label">分类</span>{{ item.category }}</span>
                  <span v-if="item.unit"><span class="meta-label">单位</span>{{ item.unit }}</span>
                  <span v-if="item.tags"><span class="meta-label">标签</span>{{ item.tags }}</span>
                </div>
              </div>
              <div class="result-matched">
                <el-tag v-for="f in item.matchedFields" :key="f" size="small" effect="plain" class="matched-tag">
                  {{ fieldLabel(f) }}
                </el-tag>
              </div>
            </div>
          </template>
          <div v-else class="empty-state">无匹配物品</div>
        </el-collapse-item>

        <!-- 批次 -->
        <el-collapse-item name="lots">
          <template #title>
            <span class="group-title">
              <el-icon class="group-icon"><Tickets /></el-icon>
              批次
              <span v-if="results.lots.length" class="group-count zj-num">{{ results.lots.length }}</span>
            </span>
          </template>
          <template v-if="results.lots.length">
            <div v-for="lot in results.lots" :key="lot.lotId" class="result-row"
                 role="button" tabindex="0" @click="goToLot(lot.lotId)"
                 @keyup.enter="goToLot(lot.lotId)">
              <div class="result-type" aria-hidden="true"><el-icon><Tickets /></el-icon></div>
              <div class="result-main">
                <div class="result-title">{{ lot.itemName }}</div>
                <div class="result-meta">
                  <span v-if="lot.lotNumber"><span class="meta-label">批次号</span>{{ lot.lotNumber }}</span>
                  <span v-if="lot.serialNumber"><span class="meta-label">序列号</span>{{ lot.serialNumber }}</span>
                </div>
              </div>
              <div class="result-matched">
                <el-tag v-for="f in lot.matchedFields" :key="f" size="small" effect="plain" class="matched-tag">
                  {{ fieldLabel(f) }}
                </el-tag>
              </div>
            </div>
          </template>
          <div v-else class="empty-state">无匹配批次</div>
        </el-collapse-item>

        <!-- 位置 -->
        <el-collapse-item name="locations">
          <template #title>
            <span class="group-title">
              <el-icon class="group-icon"><Location /></el-icon>
              位置
              <span v-if="results.locations.length" class="group-count zj-num">{{ results.locations.length }}</span>
            </span>
          </template>
          <template v-if="results.locations.length">
            <div v-for="loc in results.locations" :key="loc.locationId" class="result-row"
                 role="button" tabindex="0" @click="goToLocation(loc.locationId)"
                 @keyup.enter="goToLocation(loc.locationId)">
              <div class="result-type" aria-hidden="true"><el-icon><Location /></el-icon></div>
              <div class="result-main">
                <div class="result-title">{{ loc.name }}</div>
                <div class="result-meta result-meta-path">{{ loc.path }}</div>
              </div>
              <div class="result-matched">
                <el-tag v-for="f in loc.matchedFields" :key="f" size="small" effect="plain" class="matched-tag">
                  {{ fieldLabel(f) }}
                </el-tag>
              </div>
            </div>
          </template>
          <div v-else class="empty-state">无匹配位置</div>
        </el-collapse-item>
      </el-collapse>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Box, Tickets, Location, Search } from '@element-plus/icons-vue'
import { searchReporting } from '../../api/reporting'
import type { SearchResult } from '../../types/reporting'

const router = useRouter()
const query = ref('')
const loading = ref(false)
const searched = ref(false)
const expandedGroups = ref(['items', 'lots', 'locations'])
const results = ref<SearchResult>({ items: [], lots: [], locations: [] })

const resultCount = computed(
  () => results.value.items.length + results.value.lots.length + results.value.locations.length,
)

/** 命中的字段英文名 → 中文标签 */
const FIELD_LABELS: Record<string, string> = {
  name: '名称',
  brand: '品牌',
  tags: '标签',
  category: '分类',
  lotNumber: '批次号',
  serialNumber: '序列号',
  path: '路径',
}

function fieldLabel(field: string): string {
  return FIELD_LABELS[field] ?? field
}

let debounceTimer: ReturnType<typeof setTimeout> | null = null

// 即输即搜：输入防抖 300ms，避免每个按键都打后端
function onInput() {
  debounceTimer && clearTimeout(debounceTimer)
  if (!query.value.trim()) {
    clearResults()
    return
  }
  debounceTimer = setTimeout(doSearch, 300)
}

function doSearch() {
  if (!query.value.trim()) return
  loading.value = true
  debounceTimer && clearTimeout(debounceTimer)
  searchReporting(query.value.trim())
    .then((r) => {
      results.value = r
      searched.value = true
    })
    .finally(() => {
      loading.value = false
    })
}

function clearResults() {
  debounceTimer && clearTimeout(debounceTimer)
  searched.value = false
  results.value = { items: [], lots: [], locations: [] }
}

function goToItem(itemId: string) {
  router.push({ path: '/items', query: { highlight: itemId } })
}

function goToLot(lotId: string) {
  router.push({ name: 'inventory', query: { lotId } })
}

function goToLocation(locationId: string) {
  router.push({ path: '/locations', query: { highlight: locationId } })
}
</script>

<style scoped>
.search-bar {
  max-width: 640px;
  margin-bottom: 24px;
}

.search-input-icon {
  font-size: 16px;
  color: var(--zj-ink-400);
}

.search-btn {
  font-weight: 500;
}

.search-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--zj-ink-400);
}

.search-summary {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--zj-ink-400);
}

.search-collapse {
  border-top: 0;
  border-bottom: 0;
}

.search-collapse :deep(.el-collapse-item__header) {
  height: auto;
  padding: 14px 4px;
  border-bottom: 1px solid var(--zj-line);
  background: transparent;
  font-size: 15px;
  color: var(--zj-ink-900);
}

.search-collapse :deep(.el-collapse-item__wrap) {
  border-bottom: 0;
  background: transparent;
}

.search-collapse :deep(.el-collapse-item__content) {
  padding-bottom: 8px;
}

.group-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.group-icon {
  font-size: 16px;
  color: var(--zj-pine-600);
}

.group-count {
  padding: 0 8px;
  border-radius: 999px;
  background: var(--zj-pine-50);
  color: var(--zj-pine-600);
  font-size: 12px;
  line-height: 20px;
}

/* ---------- 结果行 ---------- */
.result-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 8px;
  border-radius: var(--zj-radius-sm);
  cursor: pointer;
  transition: background-color var(--zj-dur-fast) var(--zj-ease-out);
}

.result-row:hover {
  background: var(--zj-pine-50);
}

.result-row:focus-visible {
  outline: 2px solid rgba(61, 114, 96, 0.55);
  outline-offset: -2px;
}

.result-type {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: var(--zj-radius-sm);
  background: var(--zj-surface-sunken);
  color: var(--zj-ink-400);
}

.result-type .el-icon {
  font-size: 15px;
}

.result-main {
  flex: 1;
  min-width: 0;
}

.result-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--zj-ink-900);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
  margin-top: 3px;
  font-size: 12.5px;
  color: var(--zj-ink-600);
}

.result-meta-path {
  color: var(--zj-ink-400);
}

.meta-label {
  margin-right: 4px;
  color: var(--zj-ink-400);
}

.result-matched {
  display: flex;
  flex-shrink: 0;
  gap: 4px;
}

.matched-tag {
  border-color: var(--zj-line-strong);
  color: var(--zj-ink-600);
}

/* ---------- 空状态 ---------- */
.search-none {
  padding: 48px 0 56px;
  text-align: center;
}

.search-none-icon {
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

.search-none-icon .el-icon {
  font-size: 24px;
}

.search-none-title {
  margin: 0;
  font-family: var(--zj-serif);
  font-size: 18px;
  font-weight: 600;
  color: var(--zj-ink-900);
}

.search-none-hint {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--zj-ink-400);
}

.empty-state {
  padding: 12px 8px;
  color: var(--zj-ink-400);
  font-size: 13px;
}
</style>
