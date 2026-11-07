/**
 * 纯工具文件：测试 TS 对 ref(string literal) 的推断，以及 :attr binding 的类型检查。
 */
import { ref } from 'vue';

const layout = ref("table");
const layouts = ref(["table"]);
const sample = ref("示例");
const boolSample = ref(false);

export { layout, layouts, sample, boolSample };
