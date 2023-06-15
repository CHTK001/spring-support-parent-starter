<template>
  <el-skeleton :loading="loading" animated>
    <div id="operator2" class="panel-header panel-header-noborder"
         style="height:auto; border-left: solid 1px #ddd; border-right: solid 1px #ddd">
      <div>
        <a href="javascript:void(0)" id="newQueryButton" class="easyui-linkbutton l-btn l-btn-small l-btn-plain"
           iconcls="icon-standard-add" plain="true" @click="addData();" title="添加数据" group=""><span
            class="l-btn-left l-btn-icon-left"><span class="l-btn-text">添加数据</span><span
            class="l-btn-icon icon-standard-add">&nbsp;</span></span></a>
        <span class="toolbar-item dialog-tool-separator"></span>

        <a href="javascript:void(0)" class="easyui-linkbutton l-btn l-btn-small l-btn-plain" iconcls="icon-ok"
           plain="true" id="saveRowButton" @click="saveRow()" group=""><span class="l-btn-left l-btn-icon-left"><span
            class="l-btn-text">保存</span><span class="l-btn-icon icon-ok">&nbsp;</span></span></a>


        <a href="javascript:void(0)" class="easyui-linkbutton l-btn l-btn-small l-btn-plain" iconcls="icon-cancel"
           plain="true" id="cancelButton" @click="cancelChange()" group=""><span
            class="l-btn-left l-btn-icon-left"><span class="l-btn-text">取消</span><span class="l-btn-icon icon-cancel">&nbsp;</span></span></a>
      </div>
    </div>

    <el-table v-loading="tableLoad" show-overflow-tooltip :data="tableData" style="width: 100%" border stripe>
      <el-table-column v-for="item in tableColumn" show-overflow-tooltip :prop="item.columnName"
                       :label="item.columnName">
        <template #default="scope">
          <el-input ref="gain"
                    style="width: 100%; height: 30px"
                    v-if="rowStatus[scope.$index + item.columnName]"
                    @keyup.native.enter="sendData(scope)"
                    v-model="currentRow[item.columnName]"></el-input>
          <div style="width: 100%; height: 30px" v-else @click="updateRow(scope)">{{ scope.row[item.columnName] }}</div>
        </template>
      </el-table-column>
    </el-table>

    <div class="demo-pagination-block">
      <el-pagination
          v-model:current-page="form.pageNum"
          v-model:page-size="form.pageSize"
          small="small"
          :total="total"
          ref="pageGroup"
          :page-sizes="[10, 20, 50, 100]"
          layout="->,prev, next, sizes, ->,"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
      />
    </div>
  </el-skeleton>
</template>

<script>
import request from "axios";
import URL from '@/config/url'
import {sformat} from "@/utils/Utils";

export default {
  name: "OpenTable",
  props: {
    config: undefined,
    table: undefined,
    watchData: {
      type: Array,
      default: []
    },

  },
  data() {
    return {
      tableLoad: false,
      rowStatus: {},
      currentRowIndex: -1,
      currentRow: {},
      oldDataRow: {},
      dialogVisible: !1,
      total: 0,
      loading: false,
      tableColumn: [],
      tableData: [],
      form: {
        pageNum: 1,
        pageSize: 10
      }

    }
  },
  mounted() {
    this.doSearch();
  },
  methods: {
    saveRow: function () {
      this.sendData();
    },
    cancelChange: function () {
      for(const item in this.rowStatus) {
        this.rowStatus[item] = !1;
      }
      this.currentRowIndex = -1;
      this.currentRow = {};
      this.oldDataRow = {};
    },
    checkSame(currentRow, oldDataRow) {
      for(const item of Object.keys(oldDataRow)) {
        if(oldDataRow[item] != currentRow[item]) {
          return false;
        }
      }

      return true;
    },
    /**
     * 去空
     * @param data
     */
    checkNull: function (data) {
      const tpl = {};
      for(const item of Object.keys(data)) {
        if (data[item] !== '' && data[item] !== null && data[item] !== undefined) {
          tpl[item] = data[item];
        }
      }

      return tpl;
    },
    sendData: function  () {
      this.currentRow = this.checkNull(this.currentRow);
      this.oldDataRow = this.oldDataRow;
      //之前有行被编辑
      if(Object.keys(this.oldDataRow).length != 0 && this.checkSame(this.currentRow, this.oldDataRow)) {
        this.cancelChange();
      } else {
        this.tableLoad = !0;
        request.put(URL.UPDATE_TABLE, {
          newData: this.currentRow,
          oldData: this.oldDataRow,
          config: this.config,
          table: this.table
        }).then(({data}) => {
              if(data.code == '00000') {
                this.$message.success("修改成功");
                this.cancelChange();
                this.doSearch();
                return !0;
              }
          this.$message.error(data.msg);
            })
            .finally(() => this.tableLoad = !1)
      }
    },
    updateRow: function ({row, column, $index}) {
      if(-1 != this.currentRowIndex && this.currentRowIndex != $index) {
        this.sendData(row, column, $index);
      }
      this.currentRowIndex = $index;
      this.currentRow = row;
      this.rowStatus[$index + column.label] = !0;
      this.oldDataRow[column.label] = row[column.label];
    },
    blurClick: function (scope) {
      debugger
    },
    addData: function () {
      const row = {};
      for (const item of this.tableColumn) {
        row[item.columnName] = '';
      }
      this.tableData.push(row);
    },
    doSearch: function () {
      this.loading = !0;
      this.tableData.length = 0;
      this.tableColumn.length = 0;
      request.get(sformat(URL.OPEN_TABLE, this.form, this.config, this.table), {
        params: this.form
      }).then(({data}) => {
        if (data.code == '00000') {
          let rs = data.data;
          this.total = rs.total;
          this.tableColumn = rs.columns;
          this.watchData.push("打开" + this.table.name + " " + data.msg);
          for (let item of rs.data) {
            this.tableData.push(item);
          }
          return false;
        } else {
          this.watchData.push("打开" + this.table.name + " " + data.msg);
        }
      }).catch(({data}) => {
        this.watchData.push("打开" + this.table.name + " " + data.msg);

      }).finally(() => this.loading = !1)
    },
    handleSizeChange: function (e) {
      this.pageSize = e;
      this.doSearch();
      return !1;
    },
    handleCurrentChange: function (e) {
      this.pageNum = e;
      this.doSearch();
      return !1;
    }
  }
}
</script>

<style scoped>

</style>