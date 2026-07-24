# 知家前端视觉重设计：审计报告与视觉规范

> 方法：redesign-existing-projects skill（Scan → Diagnose → Fix）。
> 本文档为设计改进方案与视觉规范，不含实现代码。实施时应按第 6 章的优先级分阶段落地。
> 目标气质：**高端、精致、宁静** —— 一本装帧克制的家庭账册，而非一个鲜艳的 SaaS 后台。

---

## 1. Scan：现状扫描结论

| 维度 | 现状 |
|---|---|
| 技术栈 | Vue 3 + TypeScript + Element Plus 2.10，无 Tailwind，无 CSS 预处理器 |
| 样式组织 | 单一全局样式 `src/styles/index.css`（96 行）+ 各组件 `<style scoped>`，**无设计令牌 / 无 CSS 变量体系**（仅个别处引用 Element 内置变量） |
| 字体 | `Inter, "PingFang SC", system-ui` 声明于 CSS，但 **index.html 从未加载任何字体文件**，Inter 实际不生效，全靠系统字体兜底 |
| 色彩 | 深绿家族（`#1a3a32 / #264f46 / #397262 / #4a9a80`）+ 浅灰绿底 `#f4f7f6`；MembersPage 混入琥珀/蓝色/亮绿三套外来色（Tailwind 默认色值） |
| 布局 | 左侧固定边栏 224px + 顶栏 + 主区；各页面容器宽度/内边距各自为政（见 §3.3） |
| 图标 | **项目无任何图标库依赖**；位置管理用文本符号 `+ ✏ ↗ ×` 充当操作按钮 |
| 页面 | 登录、初始化、首页（系统状态）、成员、审计日志、物品、位置、个人资料、邀请兑换、Owner 恢复、家庭设置 |

---

## 2. Diagnose：审计问题清单

按 Skill 审计维度逐项列出问题与证据位置。严重度：● 高 ◐ 中 ○ 低。

### 2.1 字体排印（Typography）

| # | 严重度 | 问题 | 证据 |
|---|---|---|---|
| T1 | ● | 字体声明为摆设：Inter 未加载，全站实际渲染为系统默认字体，中英文混排无设计感 | `styles/index.css:4-5`，`index.html`（无字体 link） |
| T2 | ● | 标题无存在感：`h1/h2` 使用浏览器默认字号字重（约 24px/700），无字距、行高设计 | 所有页面；`index.css:90` 仅重置 margin |
| T3 | ◐ | 字重只有 400/600/700 三档跳跃，缺 500 过渡层，次级信息只能靠灰色区分 | 全局 |
| T4 | ◐ | 表格中的日期、版本号、数量使用比例字体，纵向不对齐，数据感弱 | `ItemsPage.vue` 更新时间列、`LocationsPage` 版本号、`AuditLogPage` 时间列 |
| T5 | ○ | 品牌字标「知家」与正文同字体，无识别度 | `AppShell.vue:8`、`LoginPage.vue:10` |
| T6 | ○ | 登录按钮「登 录」用手动空格拉开字距，脆弱且不可复用 | `LoginPage.vue:31` |

### 2.2 色彩与表面（Color & Surfaces）

| # | 严重度 | 问题 | 证据 |
|---|---|---|---|
| C1 | ● | **多主色冲突**：系统为松绿家族，MembersPage 却引入琥珀（`#fef3c7/#92400e`）、亮蓝（`#dbeafe/#1e40af`）、亮绿（`#f0fdf4/#166534/#22c55e`）三套异族色，破坏单一色调 | `MembersPage.vue:348-377` |
| C2 | ● | 登录页三个模糊彩色圆斑 = 典型「AI 弥散渐变」指纹，廉价且与登录后的浅色界面气质断裂 | `LoginPage.vue:84-112` |
| C3 | ◐ | 玻璃拟态卡片（`backdrop-filter: blur(20px)` + 半透明白）与系统其余部分的实色白卡风格不一致 | `LoginPage.vue:113-122` |
| C4 | ◐ | 阴影为纯黑低透明度（`0 8px 32px rgba(0,0,0,0.3)`），未按背景色调制 | `LoginPage.vue:121` |
| C5 | ◐ | 侧边栏激活态为整块实色填充（`#397262`），视觉重、与现代侧栏语言不符 | `index.css:63-66` |
| C6 | ○ | 完全平面、零质感：无噪点/纹理，浅色大面背景略显「未完工」 | 全局 |
| C7 | ○ | 角色标签 `el-tag type="success"` 绿色药丸与松绿主色叠用，色彩噪音 | `AppShell.vue:33` |

### 2.3 布局（Layout）

| # | 严重度 | 问题 | 证据 |
|---|---|---|---|
| L1 | ● | **容器规范缺失**：ItemsPage/LocationsPage 用 `padding: 20px`（叠加在主区已有 24px 上 = 双层内边距），MembersPage `max-width: 960px` 居中，ProfilePage 400px，BootstrapPage 480px，系统状态页无约束 —— 五种页面五种骨架 | `ItemsPage.vue:311`、`LocationsPage.vue:223`、`MembersPage.vue:266`、`ProfilePage.vue:47`、`BootstrapPage.vue:67` |
| L2 | ◐ | 页面标题区不统一：有的页面有副标题（成员、审计），有的没有（物品、位置） | 各页面 header |
| L3 | ◐ | 物品页 7 个筛选控件平铺 flex-wrap，视觉嘈杂、无主次 | `ItemsPage.vue:8-41` |
| L4 | ◐ | 位置详情面板为 1px 边框 + 4px 圆角的裸面板，与圆角卡片体系（成员表 12px）不一致 | `LocationsPage.vue:239-244` vs `MembersPage.vue:298` |
| L5 | ○ | 圆角体系混乱：4 / 8 / 12 / 16px 并存，无规则 | 登录卡 16px、成员表 12px、详情面板 4px、头像 50% |
| L6 | ○ | `min-width: 1024px` 全局锁定 + 响应式仅有一处（物品页 <1024px 隐藏次要列），无系统性断点策略 | `index.css:17`、`ItemsPage.vue:352-357` |

### 2.4 交互与状态（Interactivity & States）

| # | 严重度 | 问题 | 证据 |
|---|---|---|---|
| S1 | ● | 可点击表格行无 `cursor: pointer`、无 hover 反馈，用户无法发现「点行看详情」 | `ItemsPage.vue:43`、`AuditLogPage.vue:33` |
| S2 | ◐ | 表格加载态为 Element 默认旋转遮罩，无骨架屏（仅系统状态页用了 `el-skeleton`） | 所有列表页 |
| S3 | ◐ | 空状态为裸文本「暂无审计记录」或「—」，无引导性设计 | `AuditLogPage.vue:51`、`ItemsPage.vue:47` |
| S4 | ◐ | 键盘焦点环未审计/未定制，Element 默认焦点样式在深色登录页上不可见 | 登录页输入框 |
| S5 | ○ | 动效为零配置：无统一过渡时长/缓动，交互反馈生硬 | 全局 |
| S6 | ○ | 无当前页之外的导航反馈细节（侧栏激活态存在但粗糙，见 C5） | `index.css` |

### 2.5 组件模式（Component Patterns）

| # | 严重度 | 问题 | 证据 |
|---|---|---|---|
| P1 | ● | 文本符号当图标按钮：`+`、`✏`、`↗`、`×`，字体渲染不可控、无对齐保证 | `LocationsPage.vue:21-24` |
| P2 | ◐ | 药丸形角色徽章（`border-radius: 12px` + 彩色实底）= 通用 AI 徽章样式 | `MembersPage.vue:340-361` |
| P3 | ◐ | 圆形头像 + 首字母，五种近似绿色随机取色，与「精致」目标有差距（可接受但可升级） | `MembersPage.vue:141,312-323` |
| P4 | ○ | 所有操作依赖 Dialog 弹窗（邀请、转移、重命名、移动），简单操作可内联 | Members/Locations 页 |
| P5 | ○ | 「操作」列按钮为多枚实色小按钮并排，视觉重 | `MembersPage.vue:51-61` |

### 2.6 战略遗漏（Strategic Omissions）

| # | 严重度 | 问题 |
|---|---|---|
| O1 | ◐ | **无 favicon**（`index.html` 无 link 标签） |
| O2 | ◐ | **无 404 页面**（router 无 `pathMatch` 兜底路由，输错 URL 白屏） |
| O3 | ○ | 无路由级 `<title>` 切换，浏览器标签永远显示「知家 · zija」 |
| O4 | ○ | 表单校验依赖原生 `required`，无统一的中文校验提示与内联错误样式 |

### 2.7 内容（Content）

整体良好：中文文案具体、无 AI 腔、无感叹号滥用、无 Lorem Ipsum。登录页标语「让每一件物品都有迹可循」契合气质，保留。

---

## 3. 设计方向

### 3.1 概念：「松间账册」（Pine Ledger）

知家是**私有的、长期的、低频但郑重**的家庭工具。视觉关键词：

- **宁静**：暖白纸面底色、极低饱和度、大量留白、无装饰性渐变与色块堆砌。
- **精致**：衬线中文标题（书卷气）+ 严格的网格与基线对齐 + 发丝级边框与调制阴影。
- **高端**：单一深松绿作为唯一强调色，其余全部中性；密度适中、呼吸感优先。

### 3.2 三个「不做」

1. **不做暗色侧边栏 + 彩色徽徽章 + 圆斑渐变**的通用后台美学；
2. **不引入第二种强调色**（蓝/琥珀/亮绿全部清除）；
3. **不动技术栈**：保留 Element Plus，通过 CSS 变量覆盖 + 全局令牌层改造，不重写组件。

---

## 4. 视觉规范（Design Tokens）

实施落点：新建 `src/styles/tokens.css` 定义变量，`index.css` 消费变量并覆盖 Element Plus 的 `--el-*` 变量。以下为规范本体。

### 4.1 色彩

**基调：暖纸白 + 深松绿，单一强调色，所有灰色统一偏暖绿一族。**

| 令牌 | 色值 | 用途 |
|---|---|---|
| `--zj-canvas` | `#F6F5F1` | 主区背景（暖纸白，替代冷灰 `#f4f7f6`） |
| `--zj-surface` | `#FFFFFF` | 卡片、表格、顶栏 |
| `--zj-surface-sunken` | `#EFEDE6` | 凹陷区、筛选条底、禁用态底 |
| `--zj-ink-900` | `#1F2721` | 主文字（带绿墨感，非纯黑） |
| `--zj-ink-600` | `#5A655D` | 次级文字、副标题 |
| `--zj-ink-400` | `#98A29A` | 占位、辅助、时间戳 |
| `--zj-line` | `#E5E3DB` | 发丝边框 |
| `--zj-line-strong` | `#D6D3C8` | 强调边框（表格外框等） |
| **松绿（唯一强调色）** | | |
| `--zj-pine-800` | `#1C3A2F` | 侧边栏底、登录页底 |
| `--zj-pine-700` | `#24493B` | 侧边栏 hover |
| `--zj-pine-600` | `#2E5D4B` | **主按钮 / 主色**（替代 `#397262`） |
| `--zj-pine-500` | `#3D7260` | 主色 hover |
| `--zj-pine-100` | `#DDE9E2` | 选中行、激活指示 |
| `--zj-pine-50` | `#EFF4F0` | 行 hover 底 |
| **语义色（仅功能场景，全部去饱和）** | | |
| `--zj-success` | `#2E5D4B` | = pine-600，成功不另立色相 |
| `--zj-warning` | `#9C7426` | 低饱和赭金（仅警告/转移所有权） |
| `--zj-danger` | `#A3492F` | 低饱和砖红（仅删除/失败） |
| `--zj-on-dark-100` | `#E8EEE9` | 深底上主文字 |
| `--zj-on-dark-400` | `#93A99C` | 深底上次级文字 |

**角色徽章去彩色化**：OWNER 用松绿描边、ADMIN 用 `--zj-ink-600` 描边、MEMBER 用 `--zj-line-strong` 描边 + 中性文字。状态点保留但降饱和：活跃 `#3D7260`、停用 `#B9BDB5`。删除 MembersPage 的琥珀/蓝/亮绿三色（C1）。

**阴影（全部带松绿/墨色色调，禁止纯黑）**：

| 令牌 | 值 | 用途 |
|---|---|---|
| `--zj-shadow-sm` | `0 1px 2px rgba(31,39,33,.05)` | 卡片静置 |
| `--zj-shadow-md` | `0 1px 2px rgba(31,39,33,.04), 0 8px 24px rgba(31,39,33,.07)` | 抽屉、下拉 |
| `--zj-shadow-lg` | `0 2px 6px rgba(28,58,47,.06), 0 24px 48px rgba(28,58,47,.12)` | 弹窗、登录卡 |

### 4.2 字体排印

**字体族**（私有部署，字体应自托管打包，如 `@fontsource-variable`，不走 CDN）：

| 角色 | 字体栈 | 说明 |
|---|---|---|
| 展示/标题 | `"Noto Serif SC", "Songti SC", serif` | 品牌字标、h1/h2、空状态标题 —— 书卷气来源 |
| 界面正文 | `"Inter Variable", "PingFang SC", "Microsoft YaHei", system-ui, sans-serif` | 组件、表格、表单 |
| 数字/代码 | `"JetBrains Mono", ui-monospace, SFMono-Regular, monospace` | 邀请链接、安装 ID、IP 地址；表格数字列用 `font-variant-numeric: tabular-nums` |

**字阶**（1.25 比例，克制）：

| 令牌 | 规格 | 用途 |
|---|---|---|
| `--zj-text-caption` | 12px / 1.5 / 400，字距 +0.04em | 表格表头（配合 sentence case）、时间戳、辅助标签 |
| `--zj-text-label` | 13px / 1.55 / 500 | 表单标签、筛选控件 |
| `--zj-text-body` | 14px / 1.6 / 400 | 正文、表格单元格 |
| `--zj-text-body-strong` | 14px / 1.6 / 500 | 行内强调（成员名等） |
| `--zj-text-subtitle` | 16px / 1.5 / 500 | 卡片标题、抽屉标题 |
| `--zj-text-h2` | 20px / 1.4 / 600，衬线 | 页面标题 |
| `--zj-text-h1` | 26px / 1.35 / 600，衬线，字距 -0.01em | 登录品牌字、空状态主标题 |
| 品牌字标 | 24px / 600 衬线，字距 +0.08em | 侧栏「知家」+ 小字 `ZIJA`（11px，字距 +0.22em，sans） |

规则：标题统一 sentence case（中文无碍，英文不 Title Case）；按钮「登 录」改 `letter-spacing: 0.3em; text-indent: 0.3em` 实现视觉居中（修 T6）；正文段落最大宽度 65 字符；标题 `text-wrap: balance`。

### 4.3 间距与网格

- 基准 **4px 网格**；间距令牌：`4 / 8 / 12 / 16 / 24 / 32 / 48 / 64`。
- **统一页面骨架**（修 L1/L2）：主区 `padding: 32px 40px`；页面容器 `max-width: 1120px; margin: 0 auto`；页头 = 衬线 h2 + 13px 副标题 + 右侧主操作，下距 24px。窄表单页（初始化/改密码）统一 `max-width: 440px`。
- 卡片内边距 24px；表格行高 ≥ 52px；筛选区与表格间距 16px。
- 光学修正：垂直内边距下 > 上（如卡片 `padding: 22px 24px 26px`）。

### 4.4 圆角与边框

| 令牌 | 值 | 用途 |
|---|---|---|
| `--zj-radius-sm` | 6px | 输入框、按钮、标签、缩略图 |
| `--zj-radius-md` | 10px | 卡片、表格容器、详情面板 |
| `--zj-radius-lg` | 14px | 抽屉、弹窗、登录卡 |

规则：容器圆角 > 内部元素圆角（修 L5）；优先用底色分层，边框仅 `--zj-line` 1px 发丝级。

### 4.5 动效

| 令牌 | 值 |
|---|---|
| `--zj-ease-out` | `cubic-bezier(0.22, 1, 0.36, 1)` |
| `--zj-dur-fast` | 150ms（hover、焦点） |
| `--zj-dur-med` | 240ms（抽屉、弹窗、展开） |

规则：只动 `transform` 与 `opacity`；按钮按下 `scale(0.98)`；表格行入场 fade + `translateY(4px)`  stagger 30ms（仅首屏）；抽屉/弹窗用 Element 默认时机改缓动；**尊重 `prefers-reduced-motion`**（全局关闭非必要动效）。

### 4.6 图标

- 引入 **Phosphor**（`@phosphor-icons/vue`，常规 weight 1.5px 视觉等宽），替换位置树的 `+ ✏ ↗ ×`（修 P1）：`Plus / PencilSimple / ArrowBendUpRight / X`，操作默认隐藏、行 hover 浮现。
- 全局统一线性风格、16px 基准尺寸；导航可选配 Phosphor 图标提升侧栏精致度。
- 新增 **favicon**：深松绿方底 + 衬线「知」字（修 O1）。

---

## 5. 分页面改进方案

### 5.1 全局骨架（AppShell）

- **侧边栏**：底色 `--zj-pine-800`；激活项改为「4px 左指示条 + `--zj-pine-100` 文字 + 8% 白底」，禁用项 `--zj-on-dark-400` 50% 透明度（修 C5）；菜单分两组（物品/库存/位置/提醒/报表 · 成员/审计/设置），组间加 11px 全大写字距分组标签；品牌区衬线字标，高度 64px 与顶栏对齐。
- **顶栏**：高度 56px；左侧 `家庭：{name}` 用 `--zj-ink-600`；右侧角色改为中性描边徽章（修 C7），登出改为文字按钮 + `SignOut` 图标。
- **主区**：`--zj-canvas` 底，统一 §4.3 页面骨架；叠加一层 **全局噪点纹理**（固定定位、pointer-events: none、3% 不透明度 SVG noise），消除平面感（修 C6）。
- 路由切换时更新 `document.title`（修 O3）；新增 404 页：衬线大字「这一页不在家」+ 返回首页链接（修 O2）。

### 5.2 登录页（气质定调页）

**推倒圆斑玻璃拟态（修 C2/C3/C4）**，改为：

- 全屏 `--zj-pine-800` 深松绿底 + 细颗粒噪点 + 极微弱径向提亮（中心 6% 白），无任何漂浮色块；
- 居中 **实色暖白卡片**（`--zj-canvas`，`--zj-radius-lg`，`--zj-shadow-lg`，宽 400px，padding 40px）：衬线「知家」32px 居中，下方 12px 字距拉开的小字 `HOUSEHOLD LEDGER`（或中文副题）；
- 输入框白底发丝边框，focus 时 2px `--zj-pine-500` 焦点环（修 S4）；主按钮 pine-600 实色，hover pine-500，active `scale(0.98)`；
- 底部标语保留「让每一件物品都有迹可循」，颜色 `--zj-on-dark-400`；
- 错误提示：行内红色文字于表单顶部（保留现有 ElMessage 逻辑亦可，但建议长驻行内）。

邀请兑换、Owner 恢复两页沿用同一壳（同底色同卡片），保证入口页气质一致。

### 5.3 物品资料页（ItemsPage）

- 页头规范化（h2 + 副标题「维护家庭物品的主数据」+ 右侧主按钮）。
- **筛选降噪（修 L3）**：第一行只保留 搜索 + 管理类型 + 状态 + 排序；分类/品牌/标签收进「更多筛选」展开区；筛选条置于 `--zj-surface-sunken` 圆角条内。
- 表格：去掉 Element 默认竖线与厚边框，仅保留行分隔发丝线；表头 12px `--zj-ink-400` 字距 +0.04em；行 hover `--zj-pine-50` + `cursor: pointer`（修 S1）；封面缩略图 36px 圆角 6px；日期列 `tabular-nums`；类型/状态徽章去彩色药丸 → 圆点 + 中性文字（消耗品赭金点、耐用品松绿点）。
- 加载态：表格区域骨架屏（列形骨架，非旋转遮罩，修 S2）；空状态：衬线标题「还没有物品」+ 引导文案 + 主按钮（修 S3）。
- 详情抽屉：标题区放 64px 封面 + 衬线名称，属性改为 label/value 双列（label `--zj-ink-400` 13px），底部操作左对齐；枚举值（`expiryReminderMode` 等）需中文化映射而非直接渲染英文。

### 5.4 成员管理页（MembersPage）

- **清除三色徽章（修 C1/P2）**：按 §4.1 描边徽章规范重做；状态点降饱和。
- 头像保留圆形（首字母方案合理），但取色收敛为 `--zj-pine-800/700/600` 三档；停用行透明度 0.55 改为整行文字 `--zj-ink-400`（更精致）。
- 操作列（修 P5）：主操作「停用/启用」保留小按钮，「设为管理员」「转移所有权」收进行尾 `More` 下拉菜单；转移所有权按钮用 `--zj-warning` 文字按钮而非实色 warning 按钮。
- 邀请链接 alert：改为卡片内嵌代码块（mono 字体）+ 复制按钮右置。

### 5.5 位置管理页（LocationsPage）

- 树节点操作图标化 + hover 浮现（修 P1，见 §4.6）。
- 工作区改为 8:4 双卡片：左卡片树、右卡片详情（修 L4，统一 `--zj-radius-md` + `--zj-shadow-sm`，去掉裸边框面板）。
- 详情面板：路径用面包屑样式（` / ` 分隔）；ID/版本用 mono 小号字；「库存将在阶段四启用」改为正式空状态组件样式（细线框 + 居中提示）。
- 树选中节点：左侧 2px pine 指示 + `--zj-pine-50` 底。

### 5.6 审计日志页（AuditLogPage）

- 时间线节点圆点降饱和（成功 pine-500，失败 `--zj-danger`），连接线 1px `--zj-line-strong`；
- 事件卡片去边框，仅 `--zj-shadow-sm`，hover 浮起至 `--zj-shadow-md` + `cursor: pointer`（修 S1）；
- 日期分组标签改衬线 16px，时间列 `tabular-nums`；IP 地址 mono 12px `--zj-ink-400`；
- 筛选条与分页样式同物品页规范。

### 5.7 系统状态 / 初始化 / 个人资料页

- 系统状态：`el-result` 的大彩图标改为 48px 细线 Phosphor `CheckCircle`（pine-500），`el-descriptions` 去竖边框仅留横行线，安装标识 mono 字体 + 复制按钮。
- 初始化 / 个人资料：统一 440px 居中窄卡（修 L1），卡片用 `--zj-surface` + `--zj-shadow-sm`；页面置于主区垂直偏上（`padding-top: 48px`）而非 `margin: 4rem auto` 硬编码。

---

## 6. 实施优先级（对应 Skill 的 Fix Priority）

| 阶段 | 内容 | 预期效果 |
|---|---|---|
| **P1 令牌与字体** | 建 `tokens.css`；自托管加载 Noto Serif SC + Inter Variable；覆盖 Element `--el-color-primary` 等变量；清理 MembersPage 外来色 | 风险最低、观感提升最大，全站立刻「换骨」 |
| **P2 骨架与登录页** | AppShell 侧栏/顶栏改造；统一页面容器骨架；登录页（+邀请/恢复页）重做 | 气质定调 |
| **P3 组件与状态** | 表格规范（hover/骨架/空态/数字字体）；徽章与按钮体系；Phosphor 图标替换文本符号；焦点环 | 交互「活」起来 |
| **P4 遗漏补齐** | favicon、404 页、路由 title、表单内联校验样式 | 完成度 |
| **P5 动效打磨** | 统一缓动、入场 stagger、`prefers-reduced-motion` | 最后的精致感 |

每阶段完成后运行 `npm --prefix frontend test` 与 `npm --prefix frontend run build` 验证，不破坏现有功能与测试（视觉类改动需同步更新快照/断言中涉及 class 或文案的部分）。

---

## 7. 验收清单

- [ ] 全站仅一个强调色（松绿），搜索代码库不再出现 `#fef3c7 / #dbeafe / #22c55e` 等外来色值
- [ ] 页面标题全部为衬线字体；表格数字列等宽对齐
- [ ] 五个页面共用同一套容器/页头骨架，无双层 padding
- [ ] 登录页无任何模糊圆斑与玻璃拟态
- [ ] 所有可点击行/卡片有 hover 反馈与 pointer 光标
- [ ] 列表页均有骨架屏与设计的空状态
- [ ] 键盘 Tab 全站可见焦点环
- [ ] 位置树无文本符号按钮
- [ ] 有 favicon、有 404 页、路由切换 title 更新
- [ ] `make frontend-test` 与 `make frontend-build` 通过
