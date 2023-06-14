<template>
  <div @keyup.119.native="run">
    <div>
      <div class="panel layout-panel layout-panel-north layout-split-north">
        <div data-options="region: 'north',split: true, border: false" style="height: 278px;" title=""
             class="panel-body panel-body-noheader panel-body-noborder layout-body">
          <div id="operator2" class="panel-header panel-header-noborder"
               style="height:auto; border-left: solid 1px #ddd; border-right: solid 1px #ddd">
            <div>
              <a href="javascript:void(0)" id="runButton" class="easyui-linkbutton l-btn l-btn-small l-btn-plain"
                 iconcls="icon-run" plain="true" @click="run()" title="运行选中SQL命令" group=""><span
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
                 iconcls="icon-standard-bin-closed" plain="true" @click="clearSQL()" title="清空编辑区SQL命令"
                 group=""><span class="l-btn-left l-btn-icon-left"><span class="l-btn-text">清空(F7)</span><span
                  class="l-btn-icon icon-standard-bin-closed">&nbsp;</span></span></a>
              <span class="toolbar-item dialog-tool-separator"></span>

              <a href="javascript:void(0)" id="formatSQLButton" class="easyui-linkbutton l-btn l-btn-small l-btn-plain"
                 iconcls="icon-hamburg-sitemap" plain="true" @click="formatSQL()" title="SQL格式美化" group=""><span
                  class="l-btn-left l-btn-icon-left"><span class="l-btn-text">美化</span><span
                  class="l-btn-icon icon-hamburg-sitemap">&nbsp;</span></span></a>
              <span class="toolbar-item dialog-tool-separator"></span>

              <a href="javascript:void(0)" id="explainSQLButton" class="easyui-linkbutton l-btn l-btn-small l-btn-plain"
                 iconcls="icon-hamburg-category" plain="true" @click="explainSQL()" title="解释" group=""><span
                  class="l-btn-left l-btn-icon-left"><span class="l-btn-text">解释</span><span
                  class="l-btn-icon icon-hamburg-category">&nbsp;</span></span></a>
              <span class="toolbar-item dialog-tool-separator"></span>

              <a href="javascript:void(0)" id="newQueryButton" class="easyui-linkbutton l-btn l-btn-small l-btn-plain"
                 iconcls="icon-standard-add" plain="true" onclick="newQuery();" title="新建查询" group=""><span
                  class="l-btn-left l-btn-icon-left"><span class="l-btn-text">新建</span><span
                  class="l-btn-icon icon-standard-add">&nbsp;</span></span></a>
              <span class="toolbar-item dialog-tool-separator"></span>

              <a href="javascript:void(0)" id="addConfig" class="easyui-linkbutton l-btn l-btn-small l-btn-plain"
                 iconcls="icon-plus" plain="true" @click="addConfig()" title="新增数据库"
              >
                  <span class="l-btn-left l-btn-icon-left">
                    <span class="l-btn-text">数据库</span>
                    <span class="l-btn-icon panel-icon icon-hamburg-database">&nbsp;</span></span>
              </a>

              <a href="javascript:void(0)" id="saveSearchButton" class="easyui-linkbutton l-btn l-btn-small l-btn-plain"
                 iconcls="icon-save" plain="true" onclick="saveSearchDialog()" title="SQL保存,可展开右侧工具栏查看"
              ><span class="l-btn-left l-btn-icon-left"><span class="l-btn-text">保存</span><span
                  class="l-btn-icon icon-save">&nbsp;</span></span></a>
              <span class="toolbar-item dialog-tool-separator"></span>
              <span href="javascript:void(0)" class="easyui-linkbutton l-btn l-btn-small l-btn-plain"
                    plain="true" title="新增数据库"
              ><span>当前数据库：</span></span>
              <span id="currentDbTitle"><el-tag>{{ currentDatabase }}</el-tag></span>

            </div>
          </div>

          <div class="content">
            <Codemirror
                v-model:value="code"
                :options="cmOptions"
                border
                height="230"
                ref="cmRef"
                @F8="run"
                @inputRead="codemirrorAutocompleteOnInputRead"
            ></Codemirror>
          </div>
        </div>
      </div>

      <div class="panel layout-panel layout-panel-center layout-split-center"
           style="border-right: solid 1px #ddd">
        <div class="panel-header">
          <div class="panel-title panel-with-icon">运行结果</div>
          <div class="panel-icon icon-standard-application-view-icons"></div>
        </div>
        <div id="searchHistoryPanel"
             title="" class="panel-body panel-body-noborder layout-body panel-noscroll"
        >
          <result-set :config="currentDatabaseData" :sql="code" ref="resultSet">

          </result-set>
        </div>
      </div>
    </div>
  </div>
</template>
<script>
import "codemirror/mode/javascript/javascript.js"
import Codemirror from "codemirror-editor-vue3"
import "codemirror/theme/ambiance.css";
import "codemirror/theme/eclipse.css";
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
// theme
import '@/style/easy.css'
import '@/assets/icons/icon-berlin.css'
import '@/assets/icons/icon-hamburg.css'
import '@/assets/icons/icon-standard.css'

import {getCurrentInstance} from "vue";
import '@/assets/icons/icon.css'
import request from "axios";
import {sformat} from "@/utils/Utils";
import URL from "@/config/url";
import {format} from "sql-formatter";
import ResultSet from "@/components/home/resultset.vue";

const  instance = getCurrentInstance();

export default {
  name: "home",
  components: {ResultSet, Codemirror},
  props: {
    currentDatabaseData: undefined,
    currentTableData: undefined,
    loading: {
      type: Boolean,
      default: true
    },
    event: {
      type: Function
    }
  },
  computed: {
    database: function () {
      return this.currentDatabaseData;
    },
    table: function () {
      return this.currentTableData;
    },
    codemirror() {
      return this.$refs.myCm.codemirror
    }
  },
  watch: {
    database: function (n, o) {
      this.currentDatabase = n.label;
    },
    table: function (n, o) {
      this.code = "SELECT * FROM " + n.name;
    }
  },
  data() {
    return {
      code: '', //代码
      currentDatabase: '',//当前数据库
      cmRef: undefined,
      editor: undefined,
      cmOptions: {
        lineNumbers: !0,
        matchBrackets: !0,
        hintOptions: {
          completeSingle: !1,
          completeOnSingleClick: !0
        },
        indentUnit: 4,
        mode: "text/x-sql",
        lineWrapping: !0,
        theme: "eclipse",
        autofocus: !0,
        extraKeys: {
          ctrl: "autocomplete",
          F7: function () {
            arguments[0].setValue('');
          },
        }
      }
    }
  },
  mounted() {
    this.cmRef?.refresh()
    this.editor = this.$refs.cmRef.cminstance;
  },
  methods: {
    addConfig() {
      this.$emit('event',  {
        id: '1',
        label: '数据库',
        path: "/database",
        type: 'database',
        content: 'Tab 1 content',
        close: false
      })
    },
    onUpdate: (args) => {
      // this.editor.setValue("SELECT * FROM " + args.name + "\r\n");
    },
    explainSQL() {
      let value = this.editor.getSelection() || this.editor.getValue();
      this.$refs.resultSet.explainSQL(value);
    },
    clearSQL() {
      this.code = '';
    },
    setSql(n) {
      this.code = "SELECT * FROM " + n.name;
    },
    run() {
      let value = this.editor.getSelection() || this.editor.getValue();
      this.$refs.resultSet.run(value);
    },
    formatSQL() {
      let selection = this.editor.getSelection();
      if (!!selection) {
        this.editor.replaceSelection(format(selection));
        return false;
      }
      this.editor.setValue(format(this.code));
    },
    codemirrorAutocompleteOnInputRead: function (a) {
      if(this.currentDatabaseData) {
        if (!a.options.hintOptions.tables) {
          request.get(sformat(URL.KEYWORD, this.currentDatabaseData))
              .then(({data}) => {
                a.options.hintOptions.tables = data.data;
              })
        }
      }
      if (!a.state.completionActive) {
        var b = a.getCursor();
        b = a.getTokenAt(b);
        var c = "";
        b.string.match(/^[.`\w@]\w*$/) && (c = b.string);
        0 < c.length && CodeMirror.commands.autocomplete(a)
      }
    },
  }
}
</script>
<style scoped>
.content {
  overflow-x: hidden;
}

*{font-family:"微软雅黑";}
</style>
