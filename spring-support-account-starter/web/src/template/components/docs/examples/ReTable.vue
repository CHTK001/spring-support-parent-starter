<!--
  ReTable 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReTable
    :data="data"
    :loader="loader"
    :params="params"
    :columns="columns"
    :loading="false"
    :settings="settings"
    :stripe="false"
    rowKey="示例"
    emptyText="示例文本"
    :pagination="false"
    :total="0"
    :page="0"
    :pageSize="0"
    :pageSizes="pageSizes"
    :maxPageSize="0"
    :paginationMode="'pager'"
    :paginationModeSwitch="false"
    :paginationModePersist="false"
    :autoReload="false"
    :config="config"
    tableId="示例"
    :storage="storage"
    :toolbar="false"
    :toolbarConfig="toolbarConfig"
    :contextMenu="contextMenu"
    columnsTriggerText="示例文本"
    settingsTriggerText="示例文本"
    :layout="'table'"
    :layoutSwitch="false"
    :layoutPersist="false"
    :layouts="layouts"
    :layoutLabels="layoutLabels"
    :layoutCard="layoutCard"
    :layoutView="layoutView"
    :layoutTable="layoutTable"
    :treeProps="treeProps"
    :engine="'element'"
    :debug="false"
    :rowDraggable="false"
    sortField="示例"
    :batchDelete="false"
    :batchDeleteHandler="batchDeleteHandler"
    :resizable="false"
    :resizeTipMode="'tip'"
    :defaultExpandAll="false"
    :showIndex="false"
    indexLabel="示例文本"
    :indexWidth="'示例'"
    :indexAlign="'left'"
    :indexFixed="false"
    :indexContinuous="false"
    :showSummary="false"
    :summaryMethod="summaryMethod"
    :spanMethod="spanMethod"
    :expandable="false"
    :expandWidth="'示例'"
    :virtualScroll="false"
    :virtualScrollGt="0"
    :virtualScrollX="false"
    :virtualScrollXGt="0"
    :areaSelect="false"
    :areaClip="false"
    :columnDraggable="false"
    :editable="false"
    :headerShowOverflowTooltip="false"
    :editCache="editCache"
    :editSaveHandler="editSaveHandler"
    :showSearch="false"
    :searchVisible="false"
    :showSearchToggle="false"
    :showSearchTitle="false"
    :showSelection="false"
    :selection="false"
    :size="'large'"
    :compact="false"
    :border="false"
    :search="false"
    :searchGap="0"
    :searchModel="searchModel"
    :autoRefresh="false"
    :cache="false"
    :cacheTtl="0"
    cacheKey="示例"
    :cacheStorage="'local'"
    :rows="rows"
    :selectable="false"
    :selectedCount="0"
    :editing="false"
    :processing="false"
    :mode="'batch'"
    :indexOffset="0"
  />
</template>

<script setup lang="ts">
import ReTable from '@repo/ui/re-table';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const data = ref<PropOf<typeof ReTable, 'data'>>([]);
const loader = ref<PropOf<typeof ReTable, 'loader'>>((params: any) => Promise.resolve({ total: 0, data: [], records: [], rows: [], list: [], items: [], content: [] }));
const params = ref<PropOf<typeof ReTable, 'params'>>({ '示例': undefined });
const columns = ref<PropOf<typeof ReTable, 'columns'>>([{ prop: '示例', label: '示例', width: '示例', minWidth: '示例', maxWidth: '示例', fixed: false, align: 'left', sortable: false, hidden: false, formatter: (row: any, column: any, cellValue: any) => '示例', slot: '示例', headerSlot: '示例', headerFitLines: () => [], showOverflowTooltip: false, className: '示例', editable: false, editType: 'text', editOptions: [], resizable: false }]);
const settings = ref<PropOf<typeof ReTable, 'settings'>>({ stripe: false, border: false, compact: false, highlightCurrentRow: false, crosshairHighlight: false, resizable: false, pageCache: false, shortcuts: false });
const pageSizes = ref<PropOf<typeof ReTable, 'pageSizes'>>([]);
const config = ref<PropOf<typeof ReTable, 'config'>>({ pagination: { enable: false, page: 0, pageSize: 0, pageSizes: [], mode: 'pager', modeSwitch: false, modePersist: false, autoReload: false, remember: false }, toolbar: { enable: false, batchDelete: false, batchSelect: false, batchEdit: false, refresh: false, columnSettings: false, tableSettings: false, position: 'top' }, search: false, selection: { enable: false } });
const storage = ref<PropOf<typeof ReTable, 'storage'>>({ get: (key: any) => ({ order: [], hidden: [], widths: { '示例': 0 } }), set: (key: any, state: any) => undefined, remove: (key: any) => undefined });
const toolbarConfig = ref<PropOf<typeof ReTable, 'toolbarConfig'>>({ enable: false, batchDelete: false, batchSelect: false, batchEdit: false, refresh: false, columnSettings: false, tableSettings: false, position: 'top' });
const contextMenu = ref<PropOf<typeof ReTable, 'contextMenu'>>([{ key: '示例', label: '示例', icon: undefined, disabled: false, divided: false, children: [] }]);
const layouts = ref<PropOf<typeof ReTable, 'layouts'>>(['table']);
const layoutLabels = ref<PropOf<typeof ReTable, 'layoutLabels'>>({});
const layoutCard = ref<PropOf<typeof ReTable, 'layoutCard'>>({ columns: 0, minWidth: 0, gap: 0, coverHeight: 0, radius: 0, hoverable: false, background: '示例', fields: [{ prop: '示例', label: '示例', render: (row: any, value: any) => '示例', title: false, desc: false, tag: false, type: 'text', cover: false }], fieldOrder: [] });
const layoutView = ref<PropOf<typeof ReTable, 'layoutView'>>({ fields: [{ prop: '示例', label: '示例', render: (row: any, value: any) => '示例', title: false, desc: false, tag: false, type: 'text', width: 0 }], fieldOrder: [], minHeight: 0, gap: 0, radius: 0, showIndex: false, indexStart: 0, hoverable: false, background: '示例' });
const layoutTable = ref<PropOf<typeof ReTable, 'layoutTable'>>({ showHeader: false, height: '示例', maxHeight: '示例', minHeight: '示例', fill: false });
const treeProps = ref<PropOf<typeof ReTable, 'treeProps'>>({ children: '示例', lazy: false, load: (row: any, treeNode: any, resolve: any) => undefined, hasChildren: '示例', checkStrictly: false });
const batchDeleteHandler = ref<PropOf<typeof ReTable, 'batchDeleteHandler'>>((rows: any) => Promise.resolve());
const summaryMethod = ref<PropOf<typeof ReTable, 'summaryMethod'>>((params: any) => []);
const spanMethod = ref<PropOf<typeof ReTable, 'spanMethod'>>((params: any) => [0, 0]);
const editCache = ref<PropOf<typeof ReTable, 'editCache'>>({ '示例': { '示例': undefined } });
const editSaveHandler = ref<PropOf<typeof ReTable, 'editSaveHandler'>>((rows: any) => Promise.resolve());
const searchModel = ref<PropOf<typeof ReTable, 'searchModel'>>({ '示例': undefined });
const rows = ref<PropOf<typeof ReTable, 'rows'>>([]);
</script>
