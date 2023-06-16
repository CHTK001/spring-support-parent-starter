<template>
  <el-skeleton :loading="loading" animated>
    <div v-if="!!table.updateEnable || !!table.insertEnable || !!table.deleteEnable" id="operator2"
         class="panel-header panel-header-noborder"
         style="height:auto; border-left: solid 1px #ddd; border-right: solid 1px #ddd">
      <div>
        <a v-if="!!table.insertEnable" href="javascript:void(0)" id="newQueryButton"
           class="easyui-linkbutton l-btn l-btn-small l-btn-plain"
           iconcls="icon-standard-add" plain="true" @click="addData();" title="添加数据" group=""><span
            class="l-btn-left l-btn-icon-left"><span class="l-btn-text">添加数据</span><span
            class="l-btn-icon icon-standard-add">&nbsp;</span></span></a>
        <span class="toolbar-item dialog-tool-separator"></span>

        <a v-if="(!!table.updateEnable || !!table.insertEnable) && openCancel" href="javascript:void(0)"
           class="easyui-linkbutton l-btn l-btn-small l-btn-plain" iconcls="icon-ok"
           plain="true" id="saveRowButton" @click="saveRow()" group=""><span class="l-btn-left l-btn-icon-left"><span
            class="l-btn-text">保存</span><span class="l-btn-icon icon-ok">&nbsp;</span></span></a>


        <a v-if="(!!table.deleteEnable) && openDelete" href="javascript:void(0)"
           class="easyui-linkbutton l-btn l-btn-small l-btn-plain" iconcls="icon-cancel"
           plain="true"  @click="deleteRow()" group=""><span
            class="l-btn-left l-btn-icon-left"><span class="l-btn-text">刪除</span><span class="l-btn-icon icon-cancel">&nbsp;</span></span></a>

        <a v-if="(!!table.updateEnable || !!table.insertEnable) && openCancel" href="javascript:void(0)"
           class="easyui-linkbutton l-btn l-btn-small l-btn-plain" iconcls="icon-cancel"
           plain="true" id="cancelButton" @click="cancelChange()" group=""><span
            class="l-btn-left l-btn-icon-left"><span class="l-btn-text">取消</span><span class="l-btn-icon icon-cancel">&nbsp;</span></span></a>
      </div>
    </div>

    <el-table v-loading="tableLoad" show-overflow-tooltip :data="tableData" style="width: 100%" border stripe
              @selection-change="handleSelectionChange">
      <el-table-column prop="index" width="45">
        <template #default="scope">
          <span>{{scope.$index+1}}</span>
        </template>
      </el-table-column>
      <el-table-column type="selection" width="55"/>
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
import {sformat, copy} from "@/utils/Utils";

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
      openCancel: false,
      openDelete: false,
      tableLoad: false,
      rowStatus: {},
      currentRowIndex: -1,
      currentRow: {},
      oldRowData: {},//历史行数据
      dialogVisible: !1,
      total: 0,
      loading: false,
      tableColumn: [],
      tableData: [],
      addNewDataStatus: !1,
      form: {
        pageNum: 1,
        pageSize: 10
      },
      multipleSelection: undefined

    }
  },
  mounted() {
    this.doSearch();
  },
  methods: {
    handleSelectionChange: function (val) {
      this.multipleSelection = val;
      if(val.length === 0) {
        this.openDelete = !1;
        return !1;
      }
      this.openDelete = !0;
    },
    deleteRow: function(row){
      this.tableLoad = !0;
      request.put(URL.UPDATE_TABLE, {
        oldData: this.multipleSelection,
        config: this.config,
        table: this.table,
        mode: 'delete'
      }).then(({data}) => {
        if (data.code == '00000') {
          this.$message.success("修改成功");
          this.cancelChange();
          this.doSearch();
          this.openCancel = !1;
          return !0;
        }
        this.$message.error(data.msg);
      }).finally(() => this.tableLoad = !1)
    },
    saveRow: function () {
      this.sendData();
    },
    cancelChange: function () {
      for (const item in this.rowStatus) {
        this.rowStatus[item] = !1;
      }
      this.currentRowIndex = -1;
      this.currentRow = {};
      this.oldRowData = {};
    },
    checkWithHistorySame() {
      for (const item of Object.keys(this.currentRow)) {
        //数据被修改
        if (this.oldRowData[item] != this.currentRow[item]) {
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
      for (const item of Object.keys(data)) {
        if (data[item] !== '' && data[item] !== null && data[item] !== undefined) {
          tpl[item] = data[item];
        }
      }

      return tpl;
    },
    sendData: function () {
      //之前有行被编辑
      if (this.checkWithHistorySame()) {
        this.cancelChange();
      } else {
        this.tableLoad = !0;
        request.put(URL.UPDATE_TABLE, {
          newData: this.currentRow,
          oldData: this.oldRowData,
          config: this.config,
          table: this.table
        }).then(({data}) => {
          if (data.code == '00000') {
            this.$message.success("修改成功");
            this.cancelChange();
            this.doSearch();
            this.openCancel = !1;
            return !0;
          }
          this.$message.error(data.msg);
        }).finally(() => this.tableLoad = !1)
      }
    },
    updateRow: function ({row, column, $index}) {
      this.openCancel = !0;
      if (-1 != this.currentRowIndex && this.currentRowIndex != $index) {
        this.sendData(row, column, $index);
      }
      this.currentRowIndex = $index;
      this.currentRow = row;
      this.oldRowData = copy(row);
      this.rowStatus[$index + column.label] = !0;
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
        if (data.code === '00000') {
          let rs = data.data;
          this.total = rs.total;
          if (rs.data.length < this.pageSize) {
            this.total = (this.pageNum - 1) * this.pageSize + rs.data.length;
          } else if (rs.data.length === 0) {
            this.total = (this.pageNum) * this.pageSize;
          }
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