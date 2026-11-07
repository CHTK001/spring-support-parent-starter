/**
 * 全局模块声明：所有 .vue 单文件组件在示例站类型检查中按 `Component` 对待。
 *
 * 解决 packages/ui 中 `import ReCode from "./index.vue"` 这类引入没有提供
 * index.d.ts 的场景（ReCode / ReCropper / ReLazy），避免 TS7016 implicit any。
 */
declare module "*.vue" {
  import type { DefineComponent } from "vue";

  const component: DefineComponent;
  export default component;
}
