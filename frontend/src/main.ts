import { createApp } from "vue";
import { createPinia } from "pinia";
import { provideGlobalConfig } from "element-plus";
import zhCn from "element-plus/es/locale/lang/zh-cn";
import "@fontsource-variable/inter";
import "@fontsource-variable/noto-serif-sc";
// Element Plus API-only usages (ElMessageBox.confirm, ElMessage, ...) have no
// <el-message-box>/<el-message> template counterpart, so ElementPlusResolver's
// on-demand CSS import cannot discover them. Without these styles the message-box
// fallback is plain inline-block positioned by the document flow, and ElMessage
// likewise has no position rule — both render unstyled. Import the CSS for the
// imperative APIs we actually use.
import "element-plus/theme-chalk/el-message-box.css";
import "element-plus/theme-chalk/el-message.css";
import "element-plus/theme-chalk/el-overlay.css";
import App from "./App.vue";
import { router } from "./router";
import "./styles/index.css";

const app = createApp(App);
app.use(createPinia());
app.use(router);
provideGlobalConfig({ locale: zhCn }, app, true);
app.mount("#app");
