<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">库存管理</h2>
      <div class="page-actions">
        <el-button type="primary" data-testid="btn-inbound" @click="openInbound">入库</el-button>
        <el-button data-testid="btn-consume" @click="openConsume">领用</el-button>
        <el-button data-testid="btn-loss" @click="openLoss">报损</el-button>
        <el-button data-testid="btn-transfer" @click="openTransfer">移位</el-button>
        <el-button data-testid="btn-stocktake" @click="openStocktake">发起盘点</el-button>
      </div>
    </div>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="当前库存" name="stock"><CurrentStockTab ref="stockTabRef" /></el-tab-pane>
      <el-tab-pane label="批次" name="lots"><LotsTab ref="lotsTabRef" /></el-tab-pane>
      <el-tab-pane label="流水" name="movements"><MovementsTab ref="movementsTabRef" /></el-tab-pane>
      <el-tab-pane label="盘点" name="stocktakes"><StocktakesTab ref="stocktakesTabRef" /></el-tab-pane>
    </el-tabs>

    <InboundDialog v-model="showInbound" @done="onOperationDone" />
    <ConsumeDialog v-model="showConsume" @done="onOperationDone" />
    <LossDialog v-model="showLoss" @done="onOperationDone" />
    <TransferDialog v-model="showTransfer" @done="onOperationDone" />
    <StocktakeDialog
      v-model="showStocktake"
      :stocktake-id="null"
      :start-step="0"
      @saved="onStocktakeDone"
    />
    <ConsistencyDialog v-model="showConsistency" />
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CurrentStockTab from './inventory/CurrentStockTab.vue'
import LotsTab from './inventory/LotsTab.vue'
import MovementsTab from './inventory/MovementsTab.vue'
import StocktakesTab from './inventory/StocktakesTab.vue'
import InboundDialog from './inventory/InboundDialog.vue'
import ConsumeDialog from './inventory/ConsumeDialog.vue'
import LossDialog from './inventory/LossDialog.vue'
import TransferDialog from './inventory/TransferDialog.vue'
import StocktakeDialog from './inventory/StocktakeDialog.vue'
import ConsistencyDialog from './inventory/ConsistencyDialog.vue'

const route = useRoute()
const router = useRouter()

const activeTab = ref('stock')

const showInbound = ref(false)
const showConsume = ref(false)
const showLoss = ref(false)
const showTransfer = ref(false)
const showStocktake = ref(false)
const showConsistency = ref(false)

const stockTabRef = ref<InstanceType<typeof CurrentStockTab> | null>(null)
const lotsTabRef = ref<InstanceType<typeof LotsTab> | null>(null)
const movementsTabRef = ref<InstanceType<typeof MovementsTab> | null>(null)
const stocktakesTabRef = ref<InstanceType<typeof StocktakesTab> | null>(null)

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
  } else if (action === 'consistency') {
    openConsistency()
  }
  if (action) {
    router.replace({ query: { ...route.query, action: undefined } })
  }
}, { immediate: true })

function openInbound() {
  showInbound.value = true
}

function openConsume() {
  showConsume.value = true
}

function openLoss() {
  showLoss.value = true
}

function openTransfer() {
  showTransfer.value = true
}

function openStocktake() {
  showStocktake.value = true
}

function openConsistency() {
  showConsistency.value = true
}

function onOperationDone() {
  stockTabRef.value?.loadData?.()
  lotsTabRef.value?.loadData?.()
  movementsTabRef.value?.loadData?.()
}

function onStocktakeDone() {
  stockTabRef.value?.loadData?.()
  lotsTabRef.value?.loadData?.()
  movementsTabRef.value?.loadData?.()
  stocktakesTabRef.value?.loadData?.()
}
</script>

<style scoped>
.page-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
