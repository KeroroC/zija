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
        placeholder="输入关键词搜索..."
        clearable
        @keyup.enter="doSearch"
        @input="onInput"
        @clear="clearResults"
      >
        <template #append>
          <el-button @click="doSearch" :loading="loading">搜索</el-button>
        </template>
      </el-input>
      <p v-if="searched" class="search-summary">共 {{ resultCount }} 条匹配</p>
    </div>

    <!-- 搜索结果 -->
    <div v-if="searched" class="search-results">
      <div v-if="resultCount === 0" class="search-none">
        <p class="search-none-title">没有找到相关内容</p>
        <p class="search-none-hint">换个关键词，或减少限定条件再试。</p>
      </div>
      <el-collapse v-else v-model="expandedGroups">
        <el-collapse-item title="物品" name="items">
          <template v-if="results.items.length">
            <div v-for="item in results.items" :key="item.itemId" class="result-card"
                 @click="goToItem(item.itemId)">
              <div class="result-title">{{ item.name }}</div>
              <div class="result-meta">
                <span v-if="item.brand">{{ item.brand }}</span>
                <span v-if="item.category">{{ item.category }}</span>
              </div>
              <div class="result-matched">
                <el-tag v-for="f in item.matchedFields" :key="f" size="small" type="info">{{ f }}</el-tag>
              </div>
            </div>
          </template>
          <div v-else class="empty-state">无匹配物品</div>
        </el-collapse-item>

        <el-collapse-item title="批次" name="lots">
          <template v-if="results.lots.length">
            <div v-for="lot in results.lots" :key="lot.lotId" class="result-card">
              <div class="result-title">{{ lot.itemName }}</div>
              <div class="result-meta">
                <span v-if="lot.lotNumber">批次号: {{ lot.lotNumber }}</span>
                <span v-if="lot.serialNumber">序列号: {{ lot.serialNumber }}</span>
              </div>
              <div class="result-matched">
                <el-tag v-for="f in lot.matchedFields" :key="f" size="small" type="info">{{ f }}</el-tag>
              </div>
            </div>
          </template>
          <div v-else class="empty-state">无匹配批次</div>
        </el-collapse-item>

        <el-collapse-item title="位置" name="locations">
          <template v-if="results.locations.length">
            <div v-for="loc in results.locations" :key="loc.locationId" class="result-card"
                 @click="goToLocation(loc.locationId)">
              <div class="result-title">{{ loc.name }}</div>
              <div class="result-meta">{{ loc.path }}</div>
              <div class="result-matched">
                <el-tag v-for="f in loc.matchedFields" :key="f" size="small" type="info">{{ f }}</el-tag>
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

function goToLocation(locationId: string) {
  router.push({ path: '/locations', query: { highlight: locationId } })
}
</script>

<style scoped>
.search-bar {
  max-width: 600px;
  margin-bottom: 24px;
}

.search-summary {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--zj-ink-400);
  font-variant-numeric: tabular-nums;
}

.search-none {
  padding: 48px 0 56px;
  text-align: center;
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

.result-card {
  padding: 12px 16px;
  border-bottom: 1px solid var(--zj-line);
  cursor: pointer;
  transition: background var(--zj-dur-fast) var(--zj-ease-out);
}

.result-card:hover {
  background: var(--zj-pine-50);
}

.result-card:last-child {
  border-bottom: none;
}

.result-title {
  font-weight: 500;
  color: var(--zj-ink-900);
}

.result-meta {
  font-size: 13px;
  color: var(--zj-ink-600);
  margin-top: 4px;
  display: flex;
  gap: 8px;
}

.result-matched {
  margin-top: 6px;
  display: flex;
  gap: 4px;
}

.empty-state {
  padding: 16px;
  color: var(--zj-ink-400);
  font-size: 13px;
}
</style>
