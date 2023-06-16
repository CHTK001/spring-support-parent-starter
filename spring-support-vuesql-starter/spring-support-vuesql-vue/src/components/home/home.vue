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

              <a href="javascript:void(0)" id="explainSQLButton" class="easyui-linkbutton l-btn l-btn-small l-btn-plain"
                 iconcls="icon-hamburg-category" plain="true" @click="commitFn()" title="解析Mybatis" group=""><span
                  class="l-btn-left l-btn-icon-left"><span class="l-btn-text">Mybatis</span><span
                  class="l-btn-icon icon-hamburg-docs">&nbsp;</span></span></a>
              <span class="toolbar-item dialog-tool-separator"></span>

              <a href="javascript:void(0)" id="newQueryButton" class="easyui-linkbutton l-btn l-btn-small l-btn-plain"
                 iconcls="icon-standard-add" plain="true" @click="newQuery();" title="新建查询" group=""><span
                  class="l-btn-left l-btn-icon-left"><span class="l-btn-text">新建</span><span
                  class="l-btn-icon icon-standard-add">&nbsp;</span></span></a>
              <span class="toolbar-item dialog-tool-separator"></span>

              <a href="javascript:void(0)" id="newQueryButton" class="easyui-linkbutton l-btn l-btn-small l-btn-plain"
                 iconcls="icon-standard-add" plain="true" @click="formatSQLWeb();" title="格式化" group=""><span
                  class="l-btn-left l-btn-icon-left"><span class="l-btn-text">格式化</span><span
                  class="l-btn-icon icon-standard-application-tile-horizontal">&nbsp;</span></span></a>
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
          <result-set :config="currentDatabaseData" :sql="code" ref="resultSet" :watch-data="watchData">

          </result-set>
        </div>
      </div>
    </div>


    <el-dialog
        v-model="dialogVisible"
        title="解析Mybatis"
        width="90%"
        draggable
    >
      <div>
        <el-input type="textarea" v-model="inMybatisSql" :rows="12"></el-input>
      </div>
      <div style="max-height: 300px; overflow-x: hidden">
        <el-row :gutter="12">
          <el-col>
            <el-card shadow="always" style="margin-top: 12px; " v-for="item in outMybatisSql"> {{ item }}</el-card>
          </el-col>
        </el-row>
      </div>
      <template #footer>
      <span class="dialog-footer">
        <el-button @click="dialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="parse">
          解析
        </el-button>
      </span>
      </template>
    </el-dialog>

    <el-dialog
        v-model="sqlFormatWeb"
        title="格式化SQL"
        width="95%"
        height="90%"
        draggable
    >
      <el-row :rows="12">
        <el-col :span="12">
          <el-input style="font-size: 18px" type="textarea" v-model="inFormatSql" :rows="18"></el-input>
        </el-col>
        <el-col :span="12">
          <el-input style="font-size: 18px" type="textarea" v-model="outFormatSql" :rows="18"></el-input>
        </el-col>
      </el-row>
      <template #footer>
      <span class="dialog-footer">
        <el-button @click="sqlFormatWeb = false">关闭</el-button>
        <el-button type="primary" @click="parseSqlClick">
          解析
        </el-button>
      </span>
      </template>
    </el-dialog>
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
import {guid, sformat} from "@/utils/Utils";
import URL from "@/config/url";
import {format} from "sql-formatter";
import ResultSet from "@/components/home/resultset.vue";

const instance = getCurrentInstance();

const STR_PREPARING = '==>  Preparing:'
const STR_PARAMETERS = '==> Parameters:'
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
    },
    watchData: {
      type: Array,
      default: []
    },
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
      this.currentDatabase = n.configName;
    },
    table: function (n, o) {
      this.code = "SELECT * FROM " + n.name;
    }
  },
  data() {
    return {
      sqlFormatWeb: !1,
      inFormatSql: '',
      outFormatSql: '',
      inMybatisSql: '',
      outMybatisSql: [],
      dialogVisible: false,
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
    formatSQLWeb: function () {
      this.sqlFormatWeb = !this.sqlFormatWeb;
    },
    parseSqlClick: function () {
      this.outFormatSql = format(this.inFormatSql);
    },
    addConfig() {
      this.$emit('event', {
        id: 'WEB-DATABASE',
        label: '数据库',
        type: 'WEB-DATABASE',
        icon: 'DATABASE',
        close: false
      })
    },
    newQuery() {
      this.$emit('event', {
        id: guid(),
        label: '数据库',
        type: 'HOME',
        icon: 'DATABASE',
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
    commitFn: function () {
      this.dialogVisible = !this.dialogVisible;
      this.inMybatisSql = '';
      this.outMybatisSql.length = 0;
    },
    setSql(n) {
      if (n.action == 'OPEN') {
        return !0;
      }
      this.code = "SELECT * FROM " + n.name;
    },
    parse() {
      let mybatisSQLTexts = [], inputText = this.inMybatisSql;
      while (inputText.lastIndexOf('Preparing: ') > -1) {
        // 因为是从尾部截取，所以需要从数组的头部添加
        mybatisSQLTexts.unshift(inputText.substring(inputText.lastIndexOf('Preparing: ')));
        inputText = inputText.substring(0, inputText.lastIndexOf('Preparing: '));
      }
      console.log(mybatisSQLTexts);

      // 将数组中的字符串挨个处理，以数组形式返回
      for (let i = 0; i < mybatisSQLTexts.length; i++) {
        this.outMybatisSql.push(this.parseSql(mybatisSQLTexts[i]));
      }

    },
    parseSql: function (textVa) {
      // 获取带问号的SQL语句
      const statementStartIndex = textVa.indexOf('Preparing: ');
      let statementEndIndex = textVa.length - 1;
      for (let i = statementStartIndex; i < textVa.length; i++) {
        if (textVa[i] === "\n") {
          statementEndIndex = i;
          break;
        }
      }
      let statementStr = textVa.substring(statementStartIndex + "Preparing: ".length, statementEndIndex);
      // console.log(statementStr);
      //获取参数
      const parametersStartIndex = textVa.indexOf('Parameters: ');
      let parametersEndIndex = textVa.length - 1;
      for (let i = parametersStartIndex; i < textVa.length; i++) {
        if (textVa[i] === "\n") {
          parametersEndIndex = i;
          break;
        } else {
          // console.log(textVa[i]);
        }
      }
      let parametersStr = textVa.substring(parametersStartIndex + "Parameters: ".length, parametersEndIndex);
      parametersStr = parametersStr.split(",");
      // console.log(parametersStr);
      for (var i = 0; i < parametersStr.length; i++) {
        // 如果数据中带括号将使用其他逻辑
        const tempStr = parametersStr[i].substring(0, parametersStr[i].indexOf("("));
        // 获取括号中内容
        const typeStr = parametersStr[i].substring(parametersStr[i].indexOf("(") + 1, parametersStr[i].indexOf(")"));
        // 如果为字符类型
        if (typeStr === "String" || typeStr === "Timestamp" || typeStr === "Date") {
          statementStr = statementStr.replace("?", "'" + tempStr.trim() + "'");
        } else {
          // 数值类型
          statementStr = statementStr.replace("?", tempStr.trim());
        }
      }
      return statementStr;
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
