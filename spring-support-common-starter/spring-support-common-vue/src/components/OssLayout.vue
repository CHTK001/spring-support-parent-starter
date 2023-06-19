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
  </div>
  <div style="width: 100%; height: 100%">
    <div>
      <el-table @cell-click="tableClick" :loading="status.loading" ref="tableRef" :data="data.tableData" style="width: 100%" border stripe>
        <el-table-column prop="ossBucket" label="Bucket"/>
        <el-table-column prop="ossType" label="类型"/>
        <el-table-column prop="ossPath" label="oss路径"/>
        <el-table-column prop="ossNameStrategy" label="命名策略" width="200"/>
        <el-table-column prop="ossRejectStrategy" label="拒绝策略" width="200"/>
        <el-table-column prop="ossCovering" label="重名覆盖">
          <template #default="scope">
            <el-tag v-if="scope.row.ossCovering">覆盖</el-tag>
            <el-tag v-else>不覆盖</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ossBuffer" label="传输字节"/>
        <el-table-column prop="ossAppKey" label="appKey"/>
        <el-table-column prop="ossAppSecret" label="appSecret" width="120"/>
        <el-table-column prop="ossProperties" label="额外参数"/>
        <el-table-column prop="ossPlugins" label="插件" show-overflow-tooltip/>
        <el-table-column label="操作" width="200" style="z-index: 10000">
          <template #default="scope">
            <el-button type="info" :icon="Edit" @click.stop="onUpdate(scope.row)" size="small"/>
            <el-button type="danger" :icon="Delete" @click.stop="onDelete(scope.row)" size="small"/>
            <el-button type="danger" :icon="Upload" @click.stop="onUpload(scope.row)" size="small"/>
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

  <el-dialog draggable status-icon v-model="status.dialogVisible" title="OSS配置" width="30%">
    <el-form ref="formRef" :model="query.form" :rules="rules.oss" label-width="120px">
      <el-form-item label="ossId" prop="ossId" v-if="false">
        <el-input v-model="query.form.ossId" disabled readonly/>
      </el-form-item>
      <el-form-item label="bucket" prop="ossBucket">
        <el-input v-model="query.form.ossBucket" clearable placeholder="bucket"/>
      </el-form-item>
      <el-form-item label="oss路径" prop="ossPath">
        <el-input v-model="query.form.ossPath" clearable placeholder="oss路径"/>
      </el-form-item>
      <el-form-item label="oss类型" prop="ossType">
        <el-select v-model="query.form.ossType" clearable placeholder="oss类型">
          <el-option :value="item.value" :label="item.label" v-for="item in data.impl">
            <span style="float: left">{{ item.value }}</span>
            <span style=" float: right; color: var(--el-text-color-secondary); font-size: 13px;">{{ item.label }}</span>
          </el-option>
        </el-select>
      </el-form-item>

      <el-form-item label="appKey" prop="appKey">
        <el-input v-model="query.form.ossAppKey" clearable placeholder="appKey"/>
      </el-form-item>

      <el-form-item label="appSecret" prop="appSecret">
        <el-input v-model="query.form.ossAppSecret" clearable placeholder="appSecret"/>
      </el-form-item>

      <el-form-item label="重名覆盖" prop="ossCovering">
        <el-select v-model="query.form.ossCovering" clearable placeholder="重名覆盖">
          <el-option value="true" label="覆盖"/>
          <el-option value="false" label="不覆盖"/>
        </el-select>
      </el-form-item>

      <el-form-item label="传输字节" prop="ossBuffer">
        <el-input type="number" v-model="query.form.ossBuffer" clearable placeholder="传输字节"/>
      </el-form-item>

      <el-form-item label="命名策略" prop="ossNameStrategy">
        <el-select v-model="query.form.ossNameStrategy" clearable placeholder="命名策略">
          <el-option :value="item.value" :label="item.label" v-for="item in data.names"/>
        </el-select>
      </el-form-item>

      <el-form-item label="拒绝策略" prop="ossRejectStrategy">
        <el-select v-model="query.form.ossRejectStrategy" clearable placeholder="拒绝策略">
          <el-option :value="item.value" :label="item.label" v-for="item in data.reject"/>
        </el-select>
      </el-form-item>

      <el-form-item label="额外参数" prop="ossProperties">
        <el-input type="textarea" v-model="query.form.ossProperties" clearable placeholder="额外参数"/>
      </el-form-item>

      <el-form-item label="插件(,分隔)" prop="ossPlugins">
        <el-select multiple v-model="query.form.ossPlugins" clearable placeholder="插件">
          <el-option :key="item.value" :value="item.value" :label="item.label" v-for="item in data.filter">
            <span style="float: left">{{ item.value }}</span>
            <span style=" float: right; color: var(--el-text-color-secondary); font-size: 13px;">{{ item.label }}</span>
          </el-option>
        </el-select>
      </el-form-item>

      <el-form-item>
        <el-button @click="status.dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm()" :loading="status.btnloading">提交</el-button>
      </el-form-item>
    </el-form>
  </el-dialog>
  <el-dialog draggable status-icon v-model="status.uploadDialogVisible" title="OSS配置" width="30%">
    <el-form ref="formRef" :model="query.form" :rules="rules.oss" label-width="120px">
      <el-form-item label="父目录" prop="ossProperties"  v-if="false">
        <el-input v-model="query.form.parentPath" clearable placeholder="父目录"/>
      </el-form-item>
      <el-form-item label="运行效果：" :rules="[ { required: true, message: '请上传运行效果', trigger: 'blur', }, ]">
        <el-upload
            ref="uploadRef"
            :class="{ 'disabled': fileList.length > 2 }"
            :file-list="fileList"
            :show-file-list="true"
            list-type="picture-card"
            :auto-upload="false"
            :limit="3"
            :on-remove="handleRemove"
            :on-change="handleChange"
            :on-preview="handlePictureCardPreview"
        >
          <el-icon>
            <Plus/>
          </el-icon>
        </el-upload>
      </el-form-item>
      <el-form-item>
        <el-button @click="status.uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitUpload()" :loading="status.btnloading">提交</el-button>
      </el-form-item>
    </el-form>
  </el-dialog>

  <el-dialog class="preview" v-model="dialogVisible" width="500" draggable>
    <img width="500" :src="dialogImageUrl" alt="Preview Image"/>
  </el-dialog>

  <el-drawer
      v-model="status.drawer"
      :title="data.drawerTitle"
      direction="rtl"
      :before-close="handleDrawerClose"
  >
    <el-table
        :data="data.treeTableData"
        style="width: 100%; "
        row-key="id"
        @cell-click="treeTableClick"
        border
        lazy
        :load="loadTree"
        @cell-mouse-enter="status.show = true"
        @cell-mouse-leave="status.show = false"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
    >
      <el-table-column prop="name" label="节点" show-overflow-tooltip width="500" >
        <template #default="scope">
          <span v-if="!!scope.row.hasChildren || !scope.row.file" class="l-btn-icon1 icon-standard-folder-explore"></span>
          <span v-else class="l-btn-icon1 icon-standard-application"></span>

          <span style="cursor:pointer" class="l-btn-text" >{{ scope.row.name || scope.row.ossPath }}</span>

          <span @click.prevent="deleteTreeNode(scope.row)" v-if="status.show"
                  style="left: inherit !important; right: 0; cursor: pointer"
                  class="l-btn-icon icon-standard-delete"></span>
            <span @click.prevent="addTreeNode(scope.row)" v-if="status.show && !scope.row.file"
                  style="left: inherit !important; right: 16px; cursor: pointer"
                  class="l-btn-icon icon-standard-add"></span>
            <span style="clear: both"></span>
        </template>
      </el-table-column>
    </el-table>
  </el-drawer>
</template>

<script>
import request from "axios";
import '@/style/easy.css'
import {Delete, Edit, Upload} from "@element-plus/icons-vue";
import {ElMessageBox} from "element-plus";
import '@/plugins/layx/layx.min.css'

const host = '/api';
export default {
  name: 'OssLayout',
  computed: {
    Upload() {
      return Upload
    },
    Edit() {
      return Edit
    },
    Delete() {
      return Delete
    }
  },
  data() {
    return {
      status: {
        show: !1,
        drawer: !1,
        uploadDialogVisible: !1,
        parentPathStatus: !1,
        btnloading: !1,
        dialogVisible: !1,
        loading: !1,
        impl: [],
        names: [],
        reject: [],
        filter: []
      },
      data: {
        tableData: [],
        total: 0,
        drawerTitle: '',
        treeTableData: [],
      },
      rules: {
        oss: {
          ossBucket: [{required: true, message: "bucket不能为空"}],
          ossPath: [{required: true, message: "ossPath不能为空"}]
        }
      },
      query: {
        form: {
          pageNum: 1,
          pageSize: 10
        }
      },
      fileList: [],
      fileObjList: [],
      currentRow: undefined,
      dialogVisible: false,
      dialogImageUrl: '',
      maps: new Map(),

    }
  },
  mounted() {
    this.doSearch();
    this.initial();
  },
  methods: {
    treeTableClick: function (row) {
      if(row.file) {
        this.$copyText("/release/preview/" + row.ossBucket + "/" + row.id);
        layx.notice({
          title: '消息提示',
          message: "复制成功",
        });
        return !0;
      }
    },
    tableClick: function (row) {
      if(row.ossType === 'LOCAL') {
        this.data.drawerTitle = row.ossBucket;
        this.status.drawer = !this.status.drawer
        this.data.treeTableData.length = 0;
        this.data.treeTableData.push(Object.assign({
          id: 1,
          name: "",
          file: false,
          parent: "",
          ossId: row.ossId,
          parentPath: '',
          hasChildren: !0
        }, row));
      }
    },
    handleDrawerClose: function () {
      this.status.drawer = !this.status.drawer
      this.data.treeTableData.length = 0;
    },
    loadTree: function (row, treeNode, resolve){
      this.maps.set(row.id, {row, treeNode, resolve});
      request.get(host + "/release/listObjects", {
        params: {
          id: row.id,
          name : row.parent,
          ossId: row.ossId,
          ossBucket: row.ossBucket,
        }
      }).then(({data}) => {
        if (data.code === '00000') {
          const rs = [];
          for(const item of data.data) {
            rs.push(Object.assign({}, Object.assign({}, row), item));
          }
          resolve(rs);
        } else {
          resolve([]);
        }
      })
    },
    deleteTreeNode: function (row) {
      const data1 = row;
      request.get(host + "/release/deleteObject", {
        params: {
          id: row.id,
          name : row.parent,
          ossId: row.ossId,
          ossBucket: row.ossBucket,
        }
      }).then(({data}) => {
        if (data.code === '00000') {
          layx.notice({
            title: '消息提示',
            message: "修改成功",
          });
          let split = data1.parent.split('/');
          split.splice(split.length - 1, 1);

          const {row, treeNode, resolve} = this.maps.get(split.join('/'));
          this.loadTree(row, treeNode, resolve);
          return !0;
        }
        layx.notice({
          title: '消息提示',
          type: 'error',
          message: data.msg,
        });
      });
    },
    addTreeNode: function (row){
      this.onUpload(row);
    },
    submitUpload: function () {
      const formData = new FormData();
      for (const item of Object.keys(this.currentRow)) {
        formData.append(item, this.currentRow[item]);
      }

      for (const fileObjListElement of this.fileObjList) {
        formData.append("files", fileObjListElement.raw);
      }
      if(!formData.get("parentPath")) {
        formData.set("parentPath", this.currentRow.parent);
      }
      request.post(host + "/release/upload", formData, {
        headers: {
          "Content-Type": "multipart/form-data"
        },
      }).then(({data}) => {
        let type = 'success';
        if (data.code !== '00000') {
          type = 'error';
        } else {
          this.status.uploadDialogVisible = !this.status.uploadDialogVisible
        }
        layx.notice({
          title: '消息提示',
          type: type,
          message: data.msg
        });
      }).catch((data) => {
        let type = 'error';
        layx.notice({
          title: '消息提示',
          type: type,
          message: data.message
        });
      })
    },
    handleChange: function (uploadFile) {
      this.fileObjList.push(uploadFile)
    },
    handleRemove: function (uploadFile) {
      for (const i in this.fileObjList) {
        if (this.fileObjList[i] === uploadFile) {
          this.fileObjList.splice(i, 1);
        }
      }
    },
    handlePictureCardPreview: function (uploadFile) {
      this.dialogVisible = true;
      this.dialogImageUrl = uploadFile.url
    },
    onUpdate: function (row) {
      this.status.dialogVisible = !this.status.dialogVisible;
      this.query.form = row;
      if (!!this.query.form.ossPlugins) {
        this.query.form.ossPlugins = this.query.form.ossPlugins.split(',')
      } else {
        this.query.form.ossPlugins = [];
      }
    },
    onUpload: function (row) {
      this.status.uploadDialogVisible = !this.status.uploadDialogVisible
      this.fileList.length = 0;
      this.fileObjList.length = 0;
      this.currentRow = row;
      try {
        this.$refs.uploadRef.clearFiles()
      } catch (e) {
      }
      this.query.form.parentPath = null;
      if(row.parent !== undefined) {
        this.query.form.parentPath = row.parent;
      }
    },
    onDelete: function (row) {
      ElMessageBox.confirm(
          '确定要删除当前bucket ' + row.ossBucket + ' ?',
          'Warning', {
            confirmButtonText: '确认',
            cancelButtonText: '取消',
            type: 'warning',
          }
      ).then(() => {
        request.get(host + "/release/delete", {
          params: {ossId: row.ossId}
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
    initial: function () {
      request.get(host + "/release/options", {
        params: {type: 0}
      }).then(({data}) => {
        if (data.code === '00000') {
          this.data.impl = data.data;
        }
      });
      request.get(host + "/release/options", {
        params: {type: 1}
      }).then(({data}) => {
        if (data.code === '00000') {
          this.data.names = data.data;
        }
      })
      request.get(host + "/release/options", {
        params: {type: 2}
      }).then(({data}) => {
        if (data.code === '00000') {
          this.data.reject = data.data;
        }
      })
      request.get(host + "/release/options", {
        params: {type: 3}
      }).then(({data}) => {
        if (data.code === '00000') {
          this.data.filter = data.data;
        }
      })
    },
    submitForm: function () {
      this.$refs.formRef.validate(v => {
        if (v) {
          request.post(host + "/release/save", this.query.form).then(({data}) => {
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
    addData: function () {
      this.status.dialogVisible = !this.status.dialogVisible;
      this.query.form = {};
    },
    doSearch: function () {
      this.status.loading = true;
      this.data.tableData.length = 0;
      this.data.total = 0;
      request.get(host + "/release/page", this.query.form)
          .then(({data}) => {
            if (data.code === '00000') {
              this.data.tableData = data.data.records;
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
    }
  }

}

</script>
<style>
.preview .el-dialog__body {
  padding-left: 0;
  margin: 0;
}
.l-btn-icon1 {
  display: inline-block;
  width: 16px;
  height: 16px;
  line-height: 16px;
  margin-top: 6px;
  margin-right: -4px;
  font-size: 1px;
}
</style>
