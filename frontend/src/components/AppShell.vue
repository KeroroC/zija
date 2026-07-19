<template>
  <el-container class="app-shell">
    <el-aside class="app-sidebar" width="224px">
      <div class="brand">
        <span class="brand-cn">知家</span>
        <span class="brand-en">ZIJA</span>
      </div>
      <el-menu
        router
        class="app-menu"
        :default-active="$route.path"
      >
        <el-menu-item index="/">首页</el-menu-item>
        <el-menu-item index="/items" disabled>物品资料</el-menu-item>
        <el-menu-item index="/inventory" disabled>库存管理</el-menu-item>
        <el-menu-item index="/locations" disabled>位置管理</el-menu-item>
        <el-menu-item index="/reminders" disabled>提醒中心</el-menu-item>
        <el-menu-item index="/reports" disabled>报表与导出</el-menu-item>
        <el-menu-item index="/settings" disabled>家庭设置</el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="app-header">
        <span class="header-context">家庭：我的家</span>
        <el-tag effect="plain" type="success">管理员</el-tag>
      </el-header>
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>
