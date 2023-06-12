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
                 iconcls="icon-run" plain="true" onclick="run()" title="运行选中SQL命令" group=""><span
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
                 iconcls="icon-standard-bin-closed" plain="true" onclick="clearSQL()" title="清空编辑区SQL命令"
                 group=""><span class="l-btn-left l-btn-icon-left"><span class="l-btn-text">清空(F7)</span><span
                  class="l-btn-icon icon-standard-bin-closed">&nbsp;</span></span></a>
              <span class="toolbar-item dialog-tool-separator"></span>

              <a href="javascript:void(0)" id="formatSQLButton" class="easyui-linkbutton l-btn l-btn-small l-btn-plain"
                 iconcls="icon-hamburg-sitemap" plain="true" onclick="formatSQL()" title="SQL格式美化" group=""><span
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
                 group=""><span class="l-btn-left l-btn-icon-left"><span class="l-btn-text">保存</span><span
                  class="l-btn-icon icon-save">&nbsp;</span></span></a>
              <span class="toolbar-item dialog-tool-separator"></span>
              &nbsp;当前数据库：<span id="currentDbTitle">{{ curdatabase }}</span>
            </div>
          </div>
          <div class="content">
            <Codemirror style="margin:0px; width:99%;" id="sqltextarea"
                        v-model:value="code"
                        :options="cmOptions"
                        border
                        ref="cmRef"
                        height="200"
                        @change="onChange"
                        @input="onInput"
                        @ready="onReady"
            >
            </Codemirror>
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

          <div id="searchTabs" class="easyui-tabs tabs-container">

            <div class="tabs-header tabs-header-noborder">
              <div class="tabs-wrap">
                <ul class="tabs" style="height: 26px;">
                  <li class="tabs-selected"><a href="javascript:void(0)" class="tabs-inner"
                                               style="height: 25px; line-height: 25px;"><span
                      class="tabs-title tabs-with-icon" style="padding-right: 9px;">消息</span><span
                      class="tabs-icon icon-hamburg-issue"></span></a><span class="tabs-p-tool" style="right: 5px;"><a
                      href="javascript:void(0)" class="icon-mini-refresh"></a></span></li>
                </ul>
              </div>
            </div>
            <div class="tabs-panels" style="padding-top: 2px; height: 269px">
              <div class="panel">
                <div id="searcHomePanel" title=""
                     class="panel-body  panel-body-noborder"
                     style="border-top-width: 1px; ">
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

  </div>
</template>
<script>
import {onMounted, onUnmounted, ref} from "vue"
import "codemirror/mode/javascript/javascript.js"
import Codemirror from "codemirror-editor-vue3"

export default {
  components: {Codemirror},
  setup() {
    const code = ref('1232323')

    const cmRef = ref()
    const cmOptions = {
      lineNumbers: !0,
      matchBrackets: !0,
      hintOptions: {
        completeSingle: !1,
        completeOnSingleClick: !0
      },
      indentUnit: 4,
      mode: "text/x-mysql",
      lineWrapping: !0,
      theme: "eclipse",
      autofocus: !0,
      extraKeys: {
        ctrl: "autocomplete",
        F7: function () {
          clearSQL()
        }, F8: function () {
          run()
        }
      }
    }
    const onChange = (val, cm) => {
      console.log(val)
      console.log(cm.getValue())
    }

    const onInput = (val) => {
      console.log(val)
    }

    const onReady = (cm) => {
      console.log(cm.focus())
    }

    onMounted(() => {
      cmRef.value?.refresh()

    })

    onUnmounted(() => {
      cmRef.value?.destroy()
    })

    return {
      code,
      cmRef,
      cmOptions,
      onChange,
      onInput,
      onReady
    }
  }
}
</script>
<script setup>
import {ref, onMounted, onUnmounted} from "vue"
import '@/style/easy.css'
import '@/assets/icons/icon-all.min.css'
import '@/assets/icons/icon.css'

import "codemirror/mode/sql/sql.js"
import 'codemirror/addon/hint/show-hint.css'
import 'codemirror/addon/hint/show-hint.js'
import 'codemirror/addon/hint/sql-hint.js'
import Home from './index'

const code = ref('')
const curdatabase = ref("Empty")

const cmRef = ref()

onMounted(() => {
  Home.initial();
})
</script>

<style>
.CodeMirror-scroll {
  min-height: 300px;
}

.el-tabs__header {
  margin: 0 !important;
}
</style>
