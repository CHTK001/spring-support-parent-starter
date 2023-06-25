<template>
  <div class="page-tabs-index">
  <div class="panel layout-panel layout-panel-north layout-split-north">
    <div data-options="region: 'north',split: true, border: false"
         class="panel-body panel-body-noheader panel-body-noborder layout-body">
      <div id="operator2" class="panel-header panel-header-noborder"
           style="height:auto; border-left: solid 1px #ddd; border-right: solid 1px #ddd">
        <div>
          <a href="javascript:void(0)" class="easyui-linkbutton l-btn l-btn-small l-btn-plain"
             iconcls="icon-standard-arrow-refresh" id="refreshButton" @click="doSearch()"><span
              class="l-btn-left l-btn-icon-left"><span class="l-btn-text">刷新</span><span
              class="l-btn-icon icon-standard-arrow-refresh">&nbsp;</span></span></a>
          <span class="toolbar-item dialog-tool-separator"></span>

          <a href="javascript:void(0)" id="newQueryButton"
             class="easyui-linkbutton l-btn l-btn-small l-btn-plain"
             iconcls="icon-standard-add" @click="addData();" title="添加数据"><span
              class="l-btn-left l-btn-icon-left"><span class="l-btn-text">添加数据</span><span
              class="l-btn-icon icon-standard-add">&nbsp;</span></span></a>
          <span class="toolbar-item dialog-tool-separator"></span>
        </div>
      </div>
    </div>
  </div>

    <div style="width: 100%; height: 100%">
      <div>
        <el-table  :loading="status.loading" ref="tableRef" :data="data.tableData" style="width: 100%" border stripe>
          <el-table-column prop="arrangeName" label="任务名称"/>
          <el-table-column label="操作" width="200" style="z-index: 10000">
            <template #default="scope">
              <el-button type="info" :icon="Edit" @click.stop="onUpdate(scope.row)" size="small"/>
              <el-button type="info" :icon="Coin" @click.stop="onDag(scope.row)" size="small">
                配置
              </el-button>
              <el-button type="danger" :icon="Delete" @click.stop="onDelete(scope.row)" size="small"/>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div class="demo-pagination-block">
        <el-pagination
            small="small"
            layout="->, total, prev, pager, next"
            :total="data.total"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
        />
      </div>
    </div>

  </div>

  <el-dialog draggable status-icon v-model="status.dialogVisible" title="编排配置" width="30%">
    <el-form ref="formRef" :model="query.form" :rules="rules.arrange" label-width="120px">
      <el-form-item label="arrangeId" prop="arrangeId" v-if="false">
        <el-input v-model="query.form.arrangeId" disabled readonly/>
      </el-form-item>
      <el-form-item label="编排名称" prop="arrangeName">
        <el-input v-model="query.form.arrangeName" clearable placeholder="处理任务"/>
      </el-form-item>
      <el-form-item>
        <el-button @click="status.dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm()" :loading="status.btnloading">提交</el-button>
      </el-form-item>
    </el-form>
  </el-dialog>

  <el-dialog fullscreen draggable status-icon v-model="status.dagVisible" >
    <iframe :src="data.frame" frameborder="0" width="100%" height="100%"></iframe>
  </el-dialog>
</template>

<script>

import request from "axios";
import '@/style/easy.css'
import {Coin, Delete, Edit, Upload} from "@element-plus/icons-vue";
import {ElMessageBox} from "element-plus";
import '@/plugins/layx/layx.min.css'
import Butterfly from "@/components/butterfly.vue";
import config from "@/config/common"

const host = config.host;
export default {
  name: "arrange",
  components: {Butterfly},
  computed: {
    Coin() {
      return Coin
    },
    Delete() {
      return Delete
    },
    Edit() {
      return Edit
    }
  },
  data() {
    return {
      config: {
        dagConfig:  {
          disLinkable: true, // 可删除连线
          linkable: true,    // 可连线
          draggable: true,   // 可拖动
          zoomable: true,    // 可放大
          moveable: true,    // 可平移
          theme: {
            edge: {
              shapeType: 'Flow'
            }
          }
        }
      },
      status: {
        loading: false,
        dialogVisible: false,
        dagVisible: !1,
      },
      data: {
        frame: '',
        tableData: [],
        options: [],
        total: 0,
        dagData: {}
      },
      query: {
        form: {
          pageSize: 10,
          pageNum: 1
        }
      },
      rules: {
        arrange: {
          arrangeName: [{required: true, message: "名字不能为空", trigger: 'blur'}]
        }
      },
      canvansRef: {},
      butterflyVue: {},
    }
  },
  methods: {
    addData: function () {
      this.status.dialogVisible = !this.status.dialogVisible;
      this.query.form = {};
    },
    onDag: function (row) {
      layx.open({
        id: row.arrangeName,
        content: {
          type: 'local-url',
          value: '/arrange-dag?id=' + row.arrangeId
        },
        position: 'center',
        width: 800,
        height: 800
      })
    },
    onUpdate: function (row) {
      this.status.dialogVisible = !this.status.dialogVisible;
      this.query.form = row;
    },
    onDelete: function (row) {
      ElMessageBox.confirm(
          '确定要删除当前任务 ' + row.arrangeName + ' ?',
          'Warning', {
            confirmButtonText: '确认',
            cancelButtonText: '取消',
            type: 'warning',
          }
      ).then(() => {
        request.get(host + "/arrange/delete", {
          params: {id: row.arrangeId}
        }).then(({data}) => {
          if (data.code === '00000') {
            this.doSearch();
          }
        });
      }).catch(() => {
        layx.notice({
          type: 'warn',
          messsage: '删除失败'
        })
      })
    },
    submitForm: function () {
      this.$refs.formRef.validate(v => {
        if (v) {
          request.post(host + "/arrange/saveOrUpdate", this.query.form).then(({data}) => {
            if (data.code === '00000') {
              this.addData();
              this.doSearch();
            }
            let type = 'success';
            if (data.code !== '00000') {
              type = 'error';
            }
            layx.notice({
              title: '消息提示',
              type: type,
              message: data.msg
            });
          })
        }
      })

    },

    doSearch: function (){
      this.status.loading = true;
      this.data.tableData.length = 0;
      this.data.total = 0;
      request.get(host + "/arrange/page", this.query.form)
          .then(({data}) => {
            if (data.code === '00000') {
              this.data.tableData = data.data.data;
              this.data.total = data.data.total;
            }
          }).finally(() => this.status.loading = false);
    },
    handleSizeChange: function (e) {
      this.query.form.pageSize = e;
      this.doSearch();
      return !1;
    },
    handleCurrentChange: function (e) {
      this.query.form.pageNum = e;
      this.doSearch();
      return !1;
    },

  },
  mounted() {
    this.doSearch();
  }
}

</script>

<style scoped>

</style>