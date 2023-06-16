<template>
  <el-skeleton :loading="status.loading" animated>
    <div  id="operator2"
         class="panel-header panel-header-noborder"
         style="height:auto; border-left: solid 1px #ddd; border-right: solid 1px #ddd">
      <div>
        <a v-if="!!table.insertEnable" href="javascript:void(0)" id="newQueryButton"
           class="easyui-linkbutton l-btn l-btn-small l-btn-plain"
           iconcls="icon-standard-add" @click="addData();" title="添加数据" ><span
            class="l-btn-left l-btn-icon-left"><span class="l-btn-text">添加数据</span><span
            class="l-btn-icon icon-standard-add">&nbsp;</span></span></a>
        <span class="toolbar-item dialog-tool-separator"></span>

        <a v-if="(!!table.updateEnable || !!table.insertEnable) && status.openCancel" href="javascript:void(0)"
           class="easyui-linkbutton l-btn l-btn-small l-btn-plain" iconcls="icon-ok"
           id="saveRowButton" @click="saveRow()" ><span class="l-btn-left l-btn-icon-left"><span
            class="l-btn-text">保存</span><span class="l-btn-icon icon-ok">&nbsp;</span></span></a>


        <a v-if="(!!table.deleteEnable) && status.openDelete" href="javascript:void(0)"
           class="easyui-linkbutton l-btn l-btn-small l-btn-plain" iconcls="icon-cancel"
           @click="deleteRow()"><span
            class="l-btn-left l-btn-icon-left"><span class="l-btn-text">刪除</span><span
            class="l-btn-icon icon-table-row-delete">&nbsp;</span></span></a>

        <a v-if="(!!table.updateEnable || !!table.insertEnable) && status.openCancel" href="javascript:void(0)"
           class="easyui-linkbutton l-btn l-btn-small l-btn-plain" iconcls="icon-cancel"
           id="cancelButton" @click="cancelChange()" ><span
            class="l-btn-left l-btn-icon-left"><span class="l-btn-text">取消</span><span class="l-btn-icon icon-cancel">&nbsp;</span></span></a>

        <a href="javascript:void(0)" class="easyui-linkbutton l-btn l-btn-small l-btn-plain"
           iconcls="icon-standard-arrow-refresh" id="refreshButton" @click="doSearch()" ><span
            class="l-btn-left l-btn-icon-left"><span class="l-btn-text">刷新</span><span
            class="l-btn-icon icon-standard-arrow-refresh">&nbsp;</span></span></a>

        <a v-if="(!!table.updateEnable || !!table.insertEnable)" href="javascript:void(0)" class="easyui-linkbutton l-btn l-btn-small l-btn-plain"
           iconcls="icon-table-multiple" id="copyRowButton" @click="copyRow()" ><span
            class="l-btn-left l-btn-icon-left"><span class="l-btn-text">复制</span><span
            class="l-btn-icon icon-table-multiple">&nbsp;</span></span></a>


        <a  class="easyui-linkbutton l-btn l-btn-small l-btn-plain"><span><span class="l-btn-text">耗时: {{cost}}</span></span></a>


      </div>
    </div>

    <el-table v-loading="status.tableLoad" show-overflow-tooltip :data="data.tableData" style="width: 100%" border
              stripe
              @selection-change="handleSelectionChange">
      <el-table-column prop="index" width="45">
        <template #default="scope">
          <span>{{ scope.$index + 1 }}</span>
        </template>
      </el-table-column>
      <el-table-column type="selection" width="55"/>
      <el-table-column v-for="item in data.tableColumn" show-overflow-tooltip :prop="item.columnName"
                       :label="item.columnName">
        <template #default="scope">
          <el-input ref="gain"
                    style="width: 100%; height: 30px"
                    v-if="status.rowStatus[scope.$index + item.columnName]  && item.columnType !== 'DATETIME'"
                    @keyup.native.enter="sendData(scope)"
                    v-model="updateRecordData[scope.$index]['newData'][item.columnName]"></el-input>
          <el-date-picker
              clearable
              @keyup.native.enter="sendData(scope)"
              v-model="updateRecordData[scope.$index]['newData'][item.columnName]"
              size="small"
              style="width: 130px;margin-left: -10px;"
              v-else-if="status.rowStatus[scope.$index + item.columnName] && item.columnType === 'DATETIME'"
              type="datetime"
              format="YYYY-MM-DD HH:mm:ss"
              value-format="YYYY-MM-DD HH:mm:ss"
          />

          <el-date-picker
              clearable
              @keyup.native.enter="sendData(scope)"
              v-model="updateRecordData[scope.$index]['newData'][item.columnName]"
              size="small"
              v-else-if="status.rowStatus[scope.$index + item.columnName] && item.columnType === 'YEAR'"
              type="year"
              format="YYYY"
              value-format="YYYY"
          />

          <div style="width: 100%; height: 30px" v-else @click="updateRow(scope)">{{ scope.row[item.columnName] }}</div>
        </template>
      </el-table-column>
    </el-table>

    <div class="demo-pagination-block">
      <el-pagination
          v-model:current-page="form.pageNum"
          v-model:page-size="form.pageSize"
          small="small"
          :total="data.total"
          ref="pageGroup"
          :page-sizes="[10, 20, 50, 100]"
          layout="->,prev, next, sizes, ->,"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
      />
    </div>
  </el-skeleton>

  <el-dialog draggable status-icon v-model="status.dialogVisible" title="数据库配置" width="30%">
    <el-form ref="formRef" :model="data.redis" :rules="rules.redis" label-width="120px">
      <el-form-item label="键" prop="key">
        <el-input v-model="data.redis.key" clearable placeholder="请输入配置名称"/>
      </el-form-item>

      <el-tooltip
          class="box-item"
          effect="dark"
          content=" (为空或 -1秒永不过期 )"
          placement="right"
      >
        <el-form-item label="过期时间" prop="ttl">
          <el-input v-model="data.redis.ttl" type="number" clearable placeholder="请输入配置名称"/>
        </el-form-item>
      </el-tooltip>
      <el-form-item label="数据类型" prop="type">
        <el-select v-model="data.redis.type">
          <el-option value="string" label="string"></el-option>
          <el-option value="set" label="set"></el-option>
          <el-option value="list" label="list"></el-option>
          <el-option value="zset" label="zset"></el-option>
          <el-option value="hash" label="hash"></el-option>
        </el-select>
      </el-form-item>

      <el-form-item label="值" prop="value">
        <el-input v-model="data.redis.value" type="textarea" clearable placeholder="请输入配置名称"/>
      </el-form-item>
      <el-form-item>
        <el-button @click="status.dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm()" :loading="loading">提交</el-button>
      </el-form-item>
    </el-form>
  </el-dialog>
</template>

<script>
import request from "axios";
import URL from '@/config/url'
import {copy, isNewSame, sformat} from "@/utils/Utils";
import '@/plugins/layx/layx.min.css'

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
      data: {
        total: 0,
        tableColumn: [],
        tableData: [],
        multipleSelection: undefined,
        redis: {}
      },
      status: {
        openCancel: false,
        openDelete: false,
        tableLoad: false,
        dialogVisible: !1,
        rowAction: 'add',
        loading: false,
        //每个单元个状态
        rowStatus: {}
      },
      rules: {
        redis: {
          key: [{required: true, message: "键不能为空", trigger: 'blur'}]
        },
      },
      form: {
        pageNum: 1,
        pageSize: 10
      },
      cost: 0,
      recordData: [],
      updateRecordData: []
    }
  },
  mounted() {
    this.doSearch();
  },
  methods: {
    submitForm: function () {
      this.recordData.push({
        newData: this.data.redis,
        action: 'add'
      })
      this.sendData();
      this.status.dialogVisible = !this.status.dialogVisible;
    },
    copyRow: function () {
      if (!this.data.multipleSelection || this.data.multipleSelection.length === 0) {
        layx.notice({
          title: '消息提示',
          message: '请选择一行进行复制'
        });
        return !1;
      }
      for (const it of this.data.multipleSelection) {
        let copy1 = copy(it);
        this.data.tableData.push(copy1);
        this.recordData.push({
          newData: copy1,
          olData: {},
          action: 'add',
          index: this.data.tableData.length - 1
        })
      }
      this.status.openCancel = !0;
    },
    handleSelectionChange: function (val) {
      this.data.multipleSelection = val;
      if (val.length === 0) {
        this.status.openDelete = !1;
        return !1;
      }
      this.status.openDelete = !0;
    },
    deleteRow: function (row) {
      this.status.tableLoad = !0;
      for (const el of this.data.multipleSelection) {
        this.recordData.push({
          newData: {},
          oldData: el,
          action: 'delete'
        })
      }
      this.saveRow();
    },
    cancelChange: function () {
      this.doSearch();
    },

    updateRow: function ({row, column, $index}) {
      this.status.openCancel = !0;
      (this.updateRecordData[$index] || (this.updateRecordData[$index] = {}))['newData'] = row;
      (this.updateRecordData[$index] || (this.updateRecordData[$index] = {}))['oldData'] = copy(row);
      this.status.rowStatus[$index + column.label] = {};

    },
    addData: function () {
      if (this.config.configType === 'REDIS') {
        this.status.dialogVisible = !this.status.dialogVisible;
      } else {
        const row = {};
        for (const item of this.data.tableColumn) {
          row[item.columnName] = null;
        }
        this.status.openCancel = !0;
        this.data.tableData.push(row);
        this.recordData.push({
          newData: row,
          oldData: {},
          action: 'add',
          index: this.data.tableData.length - 1
        })
      }
    },

    saveRow: function () {
      for(const item of this.updateRecordData) {
        if (Object.keys(item).length == 0) {
          continue
        }
        if (this.inAdd(item)) {
          continue
        }
        item['action'] = 'update';
        this.recordData.push(item)
      }
      this.sendData();
    },
    inAdd: function (item) {
      for (const itemElement of this.recordData) {
        if (itemElement.action === 'add') {
          if (isNewSame(item.newData, itemElement.newData)) {
            return true;
          }
        }
      }
      return false;
    },
    sendData: function () {
      //之前有行被编辑
      this.status.tableLoad = !0;
      const startTime = new Date().getTime();
      request.put(URL.UPDATE_TABLE, {
        record: this.recordData,
        config: this.config,
        table: this.table,
      }).then(({data}) => {
        if (data.code === '00000') {
          layx.notice({
            title: '消息提示',
            message: "修改成功",
          });
          this.cancelChange();
          this.status.openCancel = !1;
          return !0;
        }
        layx.notice({
          title: '消息提示',
          type: 'error',
          message: data.msg,
        });
        this.$messageBox.alert(data.msg, '消息', {
          confirmButtonText: 'OK'
        });
      }).finally(() => {
        this.status.tableLoad = !1;
        this.cost = (new Date().getTime() - startTime) + ' ms'
      })
    },
    reset: function () {
      this.data.redis = {};
      this.status.loading = !0;
      this.data.tableData.length = 0;
      this.data.tableColumn.length = 0;
      this.recordData.length = 0;
      this.updateRecordData.length = 0;
      this.status.rowStatus = {};
      this.status.openCancel = !1;
      this.status.openDelete = !1;
    },
    doSearch: function () {
      this.reset();
      const startTime = new Date().getTime();
      request.get(sformat(URL.OPEN_TABLE, this.form, this.config, this.table), {
        params: this.form
      }).then(({data}) => {
        if (data.code === '00000') {
          let rs = data.data;
          this.data.total = rs.total;
          if (rs.data.length < this.form.pageSize) {
            this.data.total = (this.form.pageNum - 1) * this.form.pageSize + rs.data.length;
          } else if (rs.data.length === 0) {
            this.data.total = (this.form.pageNum) * this.form.pageSize;
          }
          this.data.tableColumn = rs.columns;
          this.watchData.push("打开" + this.table.name + " " + data.msg);
          let index = 0;
          for (let item of rs.data) {
            this.data.tableData.push(item);
            this.updateRecordData[index] = {};
            index += 1;
          }
          return false;
        } else {
          this.watchData.push("打开" + this.table.name + " " + data.msg);
        }
      }).catch(({data}) => {
        this.watchData.push("打开" + this.table.name + " " + data.msg);

      }).finally(() => {
        this.status.loading = !1;
        this.cost = (new Date().getTime() - startTime) + ' ms'
      })
    },
    handleSizeChange: function (e) {
      this.form.pageSize = e;
      this.doSearch();
      return !1;
    },
    handleCurrentChange: function (e) {
      this.form.pageNum = e;
      this.doSearch();
      return !1;
    }
  }
}
</script>

<style scoped>

</style>