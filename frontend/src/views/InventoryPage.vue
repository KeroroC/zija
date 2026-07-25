<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">库存管理</h2>
      <div class="page-actions">
        <el-button type="primary" @click="openInbound">入库</el-button>
        <el-button @click="openConsume">领用</el-button>
        <el-button @click="openLoss">报损</el-button>
        <el-button @click="openTransfer">移位</el-button>
        <el-button @click="openStocktake">发起盘点</el-button>
      </div>
    </div>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="当前库存" name="stock"><CurrentStockTab /></el-tab-pane>
      <el-tab-pane label="批次" name="lots"><LotsTab /></el-tab-pane>
      <el-tab-pane label="流水" name="movements"><MovementsTab /></el-tab-pane>
      <el-tab-pane label="盘点" name="stocktakes"><StocktakesTab /></el-tab-pane>
    </el-tabs>
    <!-- Dialogs will be added in Task 38 -->
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import CurrentStockTab from './inventory/CurrentStockTab.vue'
import LotsTab from './inventory/LotsTab.vue'
import MovementsTab from './inventory/MovementsTab.vue'
import StocktakesTab from './inventory/StocktakesTab.vue'

const route = useRoute()

const activeTab = ref('stock')

// Listen to route.query.action to switch tabs or trigger dialogs
watch(() => route.query.action, (action) => {
  if (action === 'inbound') {
    openInbound()
  } else if (action === 'consume') {
    openConsume()
  } else if (action === 'loss') {
    openLoss()
  } else if (action === 'transfer') {
    openTransfer()
  } else if (action === 'stocktake') {
    openStocktake()
  }
}, { immediate: true })

function openInbound() {
  // TODO: Task 38 - open inbound dialog
}

function openConsume() {
  // TODO: Task 38 - open consume dialog
}

function openLoss() {
  // TODO: Task 38 - open loss dialog
}

function openTransfer() {
  // TODO: Task 38 - open transfer dialog
}

function openStocktake() {
  // TODO: Task 38 - open stocktake dialog
}
</script>

<style scoped>
.page-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
