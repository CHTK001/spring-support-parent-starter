/**
 * Vue 单文件组件类型声明
 *
 * @author CH
 * @since 4.0.0.42
 */
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}
