<template>
  <el-card shadow="never">
    <template #header>
      <div class="flex justify-between">
        <div>
          <el-button type="success" @click="addDatabase">
            <el-icon>
              <Plus/>
            </el-icon>
          </el-button>
        </div>
      </div>
    </template>
    <el-table :data="tableData" border style="width: 100%">
      <el-table-column prop="configName" label="配置名称"/>
      <el-table-column prop="configIp" label="配置地址"/>
      <el-table-column prop="configPort" label="配置端口"/>
      <el-table-column prop="configUsername" label="配置账号"/>
      <el-table-column prop="configType" label="数据源类型"/>
      <el-table-column prop="configDatabase" label="数据源数据库"/>
      <el-table-column label="操作">
        <template #default="scope">
          <el-button @click="updateDatabase(scope.row)">
            <el-icon>
              <Setting/>
            </el-icon>
          </el-button>
          <el-button @click="deleteDatabase(scope.row.configId)">
            <el-icon>
              <DeleteFilled/>
            </el-icon>
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>


  <el-dialog draggable status-icon v-model="dialogVisible" title="数据库配置" width="30%">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <el-form-item label="配置id" prop="configId" v-if="false">
        <el-input v-model="form.configId" disabled readonly/>
      </el-form-item>
      <el-form-item label="配置名称" prop="configName">
        <el-input v-model="form.configName" clearable placeholder="请输入配置名称"/>
      </el-form-item>
      <el-form-item label="配置地址" prop="configIp">
        <el-input v-model="form.configIp" clearable placeholder="请输入配置地址"/>
      </el-form-item>
      <el-form-item label="配置端口" prop="configPort">
        <el-input v-model="form.configPort" type="number" clearable placeholder="请输入配置端口"/>
      </el-form-item>
      <el-form-item label="账号" prop="configUsername">
        <el-input v-model="form.configUsername" clearable placeholder="请输入账号"/>
      </el-form-item>
      <el-form-item label="密码" prop="configPassword" v-if="show">
        <el-input v-model="form.configPassword" type="password" clearable placeholder="请输入密码"/>
      </el-form-item>
      <el-form-item label="数据库" prop="configDatabase">
        <el-input v-model="form.configDatabase"  clearable placeholder="请输入数据库"/>
      </el-form-item>
      <el-form-item label="数据源类型" prop="configType">
        <el-select v-model="form.configType" clearable placeholder="请选择数据源类型">
          <el-option label="MYSQL5" value="MYSQL5"></el-option>
          <el-option label="MYSQL8" value="MYSQL8"></el-option>
          <el-option label="REDIS" value="REDIS"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm()" :loading="loading">提交</el-button>
      </el-form-item>
    </el-form>
  </el-dialog>
</template>


<script>
import request from "axios";
import URL from "@/config/url"
import {DeleteFilled, Setting} from "@element-plus/icons-vue";
import {sformat} from "@/utils/Utils";
import {ElMessage} from 'element-plus'

export default {
  name: "database",
  components: {Setting, DeleteFilled},
  data() {
    return {
      loading: false,
      dialogVisible: false,
      tableData: [],
      show: true,
      form: {},
      rules: {
        configName: [{required: true, message: "配置名不能为空", trigger: 'blur'}],
        configIp: [{required: true, message: "配置地址不能为空", trigger: 'blur'}],
        configPort: [{required: true, message: "配置端口不能为空", trigger: 'blur'}],
        configType: [{required: true, message: "数据源类型不能为空", trigger: 'blur'}]
      }
    }
  },
  mounted() {
    this.doSearch();
  },
  methods: {
    updateDatabase: function (config) {
      this.dialogVisible = !this.dialogVisible;
      this.form = config;
    },
    deleteDatabase: function (configId){
      request.delete(sformat(URL.DELETE_DATABASE, {"configId": configId}))
          .then(({data}) => {
            this.doSearch()
          })
    },
    doSearch: function (){
      request.get(URL.LIST_DATASOURCE)
          .then(({data}) => {
            this.tableData = data.data;
          })
    },
    submitForm: function () {
      this.$refs.formRef.validate((v) => {
        if (v) {
          this.loading = true;
          request.post(URL.UPDATE_DATABASE, this.form)
              .then(({data}) => {
                ElMessage({
                  type: data.code == '00000' ? "success" : "error",
                  message: data.msg
                });
                if(data.code == '00000') {
                  this.doSearch();
                }
              }).catch(() => {
            ElMessage({
              type: "error",
              message: '操作失败'
            })
          }).finally(() => {
            this.loading = false;
            this.dialogVisible = !this.dialogVisible;
          });
        }
      })

    },
    addDatabase: function () {
      this.show = true;
      this.dialogVisible = !this.dialogVisible;
      this.form = {};
    }
  }
}
</script>

<style scoped>

</style>