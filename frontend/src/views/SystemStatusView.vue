<template>
  <section>
    <h1>系统状态</h1>
    <el-card>
      <el-skeleton :rows="3" animated />
      <p class="status-hint">正在连接系统…</p>
    </el-card>
  </section>
</template>
