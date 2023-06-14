import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import {createApp} from 'vue'
import App from './App.vue'
import { InstallCodemirro } from "codemirror-editor-vue3"

let app = createApp(App);
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component)
}
app.use(InstallCodemirro)
    .use(ElementPlus, {locale: zhCn})
    .mount('#app')
