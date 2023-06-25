<template>
  <el-skeleton :loading="status.loading" animated>
    <div id="operator2"
         class="panel-header panel-header-noborder"
         style="height:auto; border-left: solid 1px #ddd; border-right: solid 1px #ddd">
      <div>
        <a href="javascript:void(0)" class="easyui-linkbutton l-btn l-btn-small l-btn-plain"
           iconcls="icon-standard-arrow-refresh" id="refreshButton" @click="doSearch()"><span
            class="l-btn-left l-btn-icon-left"><span class="l-btn-text">刷新</span><span
            class="l-btn-icon icon-standard-arrow-refresh">&nbsp;</span></span></a>


        <a class="easyui-linkbutton l-btn l-btn-small l-btn-plain"><span><span
            class="l-btn-text">耗时: {{ cost }}</span></span></a>


      </div>
    </div>

    <div class="common-layout-padding">
      <el-table
          :data="data.tableData"
          style="width: 100%"
          row-key="id"
          border
          lazy
          :load="load"
          @cell-mouse-enter="status.show = true"
          @cell-mouse-leave="status.show = false"
          :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
      >
        <el-table-column prop="name" label="节点">
          <template #default="scope">
            <div class="l-btn-left l-btn-icon-left" style="width: 120px;">
              <span class="l-btn-text">{{ scope.row.name }}</span>
              <span v-if="!!scope.row.hasChildren" class="l-btn-icon icon-standard-application-double"></span>
              <span v-else class="l-btn-icon icon-standard-application"></span>
              <span @click.prevent="deleteNode(scope.row)" v-if="status.show"
                    style="left: inherit !important; right: 0; cursor: pointer"
                    class="l-btn-icon icon-standard-delete"></span>
              <span @click.prevent="addNode(scope.row)" v-if="status.show"
                    style="left: inherit !important; right: 16px; cursor: pointer"
                    class="l-btn-icon icon-standard-add"></span>
              <span style="clear: both"></span>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </el-skeleton>

  <el-dialog
      v-model="status.dialogVisible"
      title="信息"
      width="30%"
      draggable
  >
    <el-form :model="form" :rules="rules.zk" ref="formRef" label-width="60px">
      <el-form-item label="节点" prop="name">
        <el-input v-model="form.name" clearable/>
      </el-form-item>
      <el-form-item label="类型" prop="type">
        <el-select v-model="form.type" placeholder="请选择类型..." clearable>
          <el-option label="永久" value="YJ"/>
          <el-option label="临时" value="LS"/>
        </el-select>
      </el-form-item>
      <el-form-item label="数据">
        <el-input v-model="form.value" clearable/>
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="status.dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="status.btnLoading">提交</el-button>
      </span>
    </template>
  </el-dialog>
</template>
<script>
import request from "axios";
import URL from "@/config/url";
import {sformat} from "@/utils/Utils";
import {encode} from 'js-base64';

export default {
  name: "Zookeeper",
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
        tableData: []
      },
      rules: {
        zk: {
          name: [{required: true, message: "节点不能为空"}],
          type: [{required: true, message: "类型不能为空"}],
        }
      },
      status: {
        loading: !0,
        show: !1,
        dialogVisible: !1,
        btnLoading: !1
      },
      form: {},
      query: {
        realName: undefined,
        table: {
          realName: undefined,
        }
      },
      recordData: [],
      cost: 0,
      maps: new Map(),
    }
  },
  mounted() {
    this.query.realName = encode(this.table.realName);
    this.doSearch();
  },
  methods: {
    reset: function () {
      this.data.tableData.length = 0;
    },
    submitForm: function () {
      this.$refs.formRef.validate((valid) => {
        if (valid) {
          this.recordData.length = 0;
          this.recordData.push({
            newData: this.form,
            action: 'add'
          })
          const startTime = new Date().getTime();
          this.status.btnLoading = !0;
          request.put(URL.UPDATE_TABLE, {
            record: this.recordData,
            config: this.config,
            table: this.query.table,
          }).then(({data}) => {
            if (data.code === '00000') {
              layx.notice({
                title: '消息提示',
                message: "修改成功",
              });
              // this.doSearch();
              const {row, treeNode, resolve} = this.maps.get(this.query.table.realName);
              this.load(row, treeNode, resolve);
              this.status.btnLoading = !1;
              this.status.dialogVisible = false

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
            this.cost = (new Date().getTime() - startTime) + ' ms';
            this.status.btnLoading = !1;
          })
        } else {
          this.status.btnLoading = !1;
        }
      });
    },
    deleteNode: function (row1) {
      const data1 = row1;
      this.query.table.realName = row1.realName;
      this.recordData.length = 0;
      this.recordData.push({
        newData: {name: " "},
        action: 'delete'
      })
      const startTime = new Date().getTime();
      request.put(URL.UPDATE_TABLE, {
        record: this.recordData,
        config: this.config,
        table: this.query.table,
      }).then(({data}) => {
        if (data.code === '00000') {
          layx.notice({
            title: '消息提示',
            message: "修改成功",
          });
          let split = data1.realName.split('/');
          split.splice(split.length - 1, 1);

          const {row, treeNode, resolve} = this.maps.get(split.join('/'));
          this.load(row, treeNode, resolve);
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
        this.cost = (new Date().getTime() - startTime) + ' ms';
      })
    },
    addNode: function (row) {
      this.form = {};
      this.query.table.realName = row.realName;
      this.status.btnLoading = !1;
      this.status.dialogVisible = !this.status.dialogVisible
    },
    load: function (row, treeNode, resolve) {
      this.maps.set(row.realName, {row, treeNode, resolve});
      request.get(sformat(URL.OPEN_TABLE, this.form, this.config, {
        realName: encode(row.realName)
      }), {
        params: this.form
      }).then(({data}) => {
        if (data.code === '00000') {
          let rs = data.data;
          const rsData = [];
          this.watchData.push("打开" + this.table.name + " " + data.msg);
          for (let item of rs.data[0]['rs']) {
            rsData.push(item);
          }
          resolve(rsData);
          return false;
        } else {
          this.watchData.push("打开" + this.table.name + " " + data.msg);
          resolve([]);
        }
      }).catch((data) => {
        resolve([]);
        this.watchData.push("打开" + this.table.name + " " + data.msg);
      })
    },
    doSearch: function () {
      this.reset();
      const startTime = new Date().getTime();
      request.get(sformat(URL.OPEN_TABLE, this.form, this.config, this.query), {
        params: this.form
      }).then(({data}) => {
        if (data.code === '00000') {
          let rs = data.data;
          this.watchData.push("打开" + this.table.name + " " + data.msg);
          for (let item of rs.data[0]['rs']) {
            this.data.tableData.push(item);
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
    }
  }
}
</script>

<style scoped>

</style>