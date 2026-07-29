import { createApp } from "vue";
import { createPinia } from "pinia";
import { provideGlobalConfig } from "element-plus";
import zhCn from "element-plus/es/locale/lang/zh-cn";
import "@fontsource-variable/inter";
import "@fontsource-variable/noto-serif-sc";
import App from "./App.vue";
import { router } from "./router";
import "./styles/index.css";

const app = createApp(App);
app.use(createPinia());
app.use(router);
provideGlobalConfig({ locale: zhCn }, app, true);
app.mount("#app");
