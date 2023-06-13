<template>
  <div>
    <div>
      <div class="panel layout-panel layout-panel-north layout-split-north">
        <div data-options="region: 'north',split: true, border: false" style="height: 278px;" title=""
             class="panel-body panel-body-noheader panel-body-noborder layout-body">
          <div id="operator2" class="panel-header panel-header-noborder"
               style="height:auto; border-left: solid 1px #ddd; border-right: solid 1px #ddd">
            <div>
              <a href="javascript:void(0)" id="runButton" class="easyui-linkbutton l-btn l-btn-small l-btn-plain"
                 iconcls="icon-run" plain="true" @click="Home.run()" title="运行选中SQL命令" group=""><span
                  class="l-btn-left l-btn-icon-left"><span class="l-btn-text">运行(F8)</span><span
                  class="l-btn-icon icon-run">&nbsp;</span></span></a>
              <span class="toolbar-item dialog-tool-separator"></span>

              <a href="javascript:void(0)" id="stopRunButton"
                 class="easyui-linkbutton l-btn l-btn-small l-btn-plain l-btn-disabled l-btn-plain-disabled"
                 data-options="plain:true,iconCls:'icon-hamburg-stop',disabled:true" onclick="stopRun()"
                 title="停止运行" group=""><span class="l-btn-left l-btn-icon-left"><span class="l-btn-text">停止</span><span
                  class="l-btn-icon icon-hamburg-stop">&nbsp;</span></span></a>
              <span class="toolbar-item dialog-tool-separator"></span>

              <a href="javascript:void(0)" id="clearSQLButton" class="easyui-linkbutton l-btn l-btn-small l-btn-plain"
                 iconcls="icon-standard-bin-closed" plain="true" @click="Home.clearSQL()" title="清空编辑区SQL命令"
                 group=""><span class="l-btn-left l-btn-icon-left"><span class="l-btn-text">清空(F7)</span><span
                  class="l-btn-icon icon-standard-bin-closed">&nbsp;</span></span></a>
              <span class="toolbar-item dialog-tool-separator"></span>

              <a href="javascript:void(0)" id="formatSQLButton" class="easyui-linkbutton l-btn l-btn-small l-btn-plain"
                 iconcls="icon-hamburg-sitemap" plain="true" @click="Home.formatSQL()" title="SQL格式美化" group=""><span
                  class="l-btn-left l-btn-icon-left"><span class="l-btn-text">美化</span><span
                  class="l-btn-icon icon-hamburg-sitemap">&nbsp;</span></span></a>
              <span class="toolbar-item dialog-tool-separator"></span>

              <a href="javascript:void(0)" id="explainSQLButton" class="easyui-linkbutton l-btn l-btn-small l-btn-plain"
                 iconcls="icon-hamburg-category" plain="true" onclick="explainSQL()" title="执行计划" group=""><span
                  class="l-btn-left l-btn-icon-left"><span class="l-btn-text">计划</span><span
                  class="l-btn-icon icon-hamburg-category">&nbsp;</span></span></a>
              <span class="toolbar-item dialog-tool-separator"></span>

              <a href="javascript:void(0)" id="newQueryButton" class="easyui-linkbutton l-btn l-btn-small l-btn-plain"
                 iconcls="icon-standard-add" plain="true" onclick="newQuery();" title="新建查询" group=""><span
                  class="l-btn-left l-btn-icon-left"><span class="l-btn-text">新建</span><span
                  class="l-btn-icon icon-standard-add">&nbsp;</span></span></a>
              <span class="toolbar-item dialog-tool-separator"></span>

              <a href="javascript:void(0)" id="saveSearchButton" class="easyui-linkbutton l-btn l-btn-small l-btn-plain"
                 iconcls="icon-save" plain="true" onclick="saveSearchDialog()" title="SQL保存,可展开右侧工具栏查看"
                 ><span class="l-btn-left l-btn-icon-left"><span class="l-btn-text">保存</span><span
                  class="l-btn-icon icon-save">&nbsp;</span></span></a>
              <span class="toolbar-item dialog-tool-separator"></span>
              &nbsp;当前数据库：<span id="currentDbTitle">{{ currentDatabase }}</span>
            </div>
          </div>

          <el-skeleton :loading="sqlEditor" animated>
            <div class="shadow"></div>
          </el-skeleton>
          <div class="content" :style="contentStyle">
            <textarea ref="mycode" class="codesql public_text" v-model="code"></textarea>
          </div>
        </div>
      </div>

      <div class="panel layout-panel layout-panel-center layout-split-center"
           style="z-index: 100000; top: -40px;  border-right: solid 1px #ddd">
        <div class="panel-header">
          <div class="panel-title panel-with-icon">运行结果</div>
          <div class="panel-icon icon-standard-application-view-icons"></div>
        </div>
        <div id="searchHistoryPanel"
             title="" class="panel-body panel-body-noborder layout-body panel-noscroll"
        >
         <result-set :config-id="currentTableData"  ref="resultSet">

         </result-set>
        </div>
      </div>
    </div>
  </div>
</template>
<script>
</script>
<script setup>
import {onMounted, onUnmounted, ref, watch, getCurrentInstance, reactive} from "vue"
import CodeMirror from 'codemirror/lib/codemirror'
import 'codemirror/lib/codemirror.css'
import "codemirror/theme/ambiance.css";
import 'codemirror/theme/solarized.css'
import "codemirror/addon/edit/matchbrackets"
import 'codemirror/addon/edit/closebrackets.js'
import 'codemirror/mode/sql/sql.js'
import 'codemirror/addon/hint/show-hint.js'
import 'codemirror/addon/hint/show-hint.css'

// import '@/vendor/codemirror/addon/hint/sql-hint.js'
import 'codemirror/addon/hint/sql-hint.js'
import "codemirror/theme/cobalt.css";
import "codemirror/addon/selection/active-line.js";
import 'codemirror/addon/display/autorefresh';
import "codemirror/mode/javascript/javascript.js"

// theme
import '@/style/easy.css'
import '@/assets/icons/icon-berlin.css'
import '@/assets/icons/icon-hamburg.css'
import '@/assets/icons/icon-standard.css'

import '@/assets/icons/icon.css'
import Home from "@/components/home/index";
import {useRouter, onBeforeRouteUpdate} from "vue-router"
import { Base64 } from 'js-base64';
import ResultSet from "@/components/home/resultset.vue";

const currentAccept = ref('{}');
const route = useRouter();
const code = ref('2332')
const currentDatabase = ref("Empty")
const currentDatabaseData = ref()
const currentTableData = ref()
const props = ref();
const sqlEditor = ref(true);
const cmRef = ref()
const contentStyle = ref({'z-index': -1})
let editor;


Home.setCode(code);
const resultSet = ref(null)
Home.setResultSet(resultSet);
Home.initial(null, null, CodeMirror);

onBeforeRouteUpdate((to) => {
  let parse = JSON.parse(Base64.decode(to.params.data));
  currentDatabaseData.value = parse.db;
  currentDatabase.value = parse.db.label;
  currentTableData.value = parse.table;
  sqlEditor.value = !1;
  contentStyle.value = {'z-index': 1}
  Home.initial(currentDatabaseData, currentTableData)
  if(!!currentTableData.value && !!parse.table.name) {
    editor.setValue("SELECT * FROM " + parse.table.name + "\r\n");
  }
})
const cmOptions = ref({
  lineNumbers: !0,
  matchBrackets: !0,
  hintOptions: {
    completeSingle: !1,
    completeOnSingleClick: !0
  },
  autoRefresh: true,
  indentUnit: 4,
  mode: 'text/x-mysql',
  lineWrapping: !0,
  autofocus: !0,
  extraKeys: {
    ctrl: "autocomplete",
    F7: function () {
      Home.clearSQL()
    }, F8: function () {
      Home.run()
    }
  }
})


onMounted(() => {
  cmRef.value?.refresh()
  if(null == editor) {
    editor = CodeMirror.fromTextArea(getCurrentInstance().ctx.$refs.mycode, cmOptions.value);
    editor.on("inputRead", Home.codemirrorAutocompleteOnInputRead);
    editor.refresh();
    editor.focus();
    Home.setEditor(editor);
  }
})
</script>

<style>
.CodeMirror-scroll {
  min-height: 300px;
}

.el-tabs__header {
  margin: 0 !important;
}
.shadow {
  position: absolute;
  height: 300px;
  z-index: 0;
  display: none;
  width: 100%;
}
.content{
  position: absolute;
  width: 100%;
}
.json-editor {
  height: 100%;
}

.json-editor>>>.CodeMirror {
  font-size: 14px;
  /* overflow-y:auto; */
  font-weight: normal
}

.json-editor>>>.CodeMirror-scroll {}

.json-editor>>>.cm-s-rubyblue span.cm-string {
  color: #F08047;
}
*{font-family:"微软雅黑";}
</style>
