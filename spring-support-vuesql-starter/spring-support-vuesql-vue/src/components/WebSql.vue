<template>
  <el-skeleton :loading="loading" animated :rows="10">
    <div class="common-layout">
      <el-container>
        <el-aside width="218px" class="aside">
          <div class="panel-header">
            <div class="panel-title panel-with-icon">数据库选择</div>
            <div class="panel-icon ">
              <el-icon>
                <Link/>
              </el-icon>
            </div>
            <div class="panel-tool">
              <a>
                <el-icon>
                  <DArrowLeft/>
                </el-icon>
              </a>
            </div>
          </div>

          <div class="common-layout-padding">
            <el-select v-model="datasource" class="m-2" placeholder="请选择..."  @change="changeDatabase">
              <el-option
                  v-for="item in options"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"

              />
            </el-select>
          </div>

          <div class="panel-header">
            <div class="panel-title panel-with-icon">数据库</div>
            <div class="panel-icon ">
              <el-icon>
                <Histogram/>
              </el-icon>
            </div>
            <div class="panel-tool">
              <a>
                <el-icon @click="changeDatabase">
                  <RefreshRight/>
                </el-icon>
              </a>
            </div>
          </div>

          <el-skeleton :loading="tableLoading" animated>
            <div class="common-layout-padding">
              <el-table
                  :data="treeData"
                  style="width: 100%; margin-bottom: 20px; height: 700px; max-height: 700px;"
                  row-key="id"
                  border
                  default-expand-all
              >
                <el-table-column prop="name" label="详情" show-overflow-tooltip style="font-size: 21px">
                  <template #default="scope">
                    <el-icon v-if="scope.row.type =='TABLE'" style="margin-right: 5px; color: orange">
                      <Tickets/>
                    </el-icon>
                    <el-icon v-else-if="scope.row.type =='VIEW'" style="margin-right: 5px; color: green">
                      <DocumentCopy/>
                    </el-icon>
                    <el-icon v-else style="; color: orange">
                      <FolderOpened/>
                    </el-icon>
                    <el-text style="cursor: pointer" v-if="scope.row.type=='TABLE' || scope.row.type=='VIEW'"
                             @click="handleSql(scope.row)">{{ scope.row.name }}
                    </el-text>
                    <el-text v-else>
                      {{ scope.row.name }}
                    </el-text>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-skeleton>
        </el-aside>

        <el-container>
          <el-header height="30px">
            <div class="page-tabs-index">
              <el-tabs v-model="activeRoute" type="card" @tab-remove='closeTab' :closable="closable"
                       @tab-click="tabClick"
                       @edit="handleTabsEdit" height="30">
                <el-tab-pane
                    :key="item.id"
                    v-for="(item, index) in tabs"
                    :label="item.label"
                    :name="item.id"
                    :closable="item.close"
                >
                  <template #label  v-if="item.type == 'home'" >
                    <span class="custom-tabs-label">
                      <span><span class="margin-5 l-btn-icon panel-icon icon-berlin-home"></span>{{item.label}}</span>
                    </span>
                  </template>
                  <template #label  v-if="item.type == 'database'" >
                    <span class="custom-tabs-label">
                      <span> <span class="margin-5 l-btn-icon panel-icon icon-hamburg-database"></span>{{item.label}}</span>
                    </span>
                  </template>
                    <home v-if="item.type == 'home'"
                          ref="home"
                          :current-database-data="currentDatasource"
                          :loading="loading"
                          :current-table-data="currentTable"
                          @event="onEvent"
                    ></home>
                    <database v-if="item.type == 'database'"></database>
                </el-tab-pane>
              </el-tabs>
            </div>
          </el-header>
        </el-container>
      </el-container>
    </div>
  </el-skeleton>
</template>

<script>
import request from 'axios'
import URL from "@/config/url"
import {sformat} from '@/utils/Utils'
import {ElMessage} from "element-plus";

export default {
  data() {
    return {
      loading: true,
      tableLoading: false,
      datasource: '',
      currentDatasource: undefined,
      closable: false,
      activeRoute: '0',
      currentTable: undefined,
      tabs: [
        {
          id: '0',
          label: '运行及展示',
          path: "/home",
          type: 'home',
          content: 'Tab 1 content',
          close: false
        }
      ],
      treeData: [],
      options: [
      ]
    }
  },
  mounted() {
    this.loading = !0;
    request.get(URL.LIST_DATASOURCE)
        .then(({data}) => {
          this.options.length = 0;
          if (data.code == '00000') {
            data.data.forEach((item, index) => {
              this.options.push({
                value: item.configId,
                configId: item.configId,
                label: item.configName
              })
            })
          }
        }).finally(() => this.loading = false)
  },
  methods: {
    onEvent: function (item) {
      this.handleTabsEdit(item, "add");
    },
    changeDatabase: function () {
      const item = this.datasource;
      this.tableLoading = true;
      this.currentDatasource = this.options.filter(it => it.value == item)[0];
      request.get(sformat(URL.GET_TABLE_INFO, this.currentDatasource))
          .then(({data}) => {
            if (data.code == '00000') {
              this.treeData.push(data.data);
            } else {
              ElMessage({
                type: 'error',
                message: data.msg
              });
              this.treeData.length = 0;
            }
          }).catch(xhr => {
            ElMessage({
              type: 'error',
              message: '请求失败'
            });
            this.treeData.length = 0;
      }).finally(() => this.tableLoading = false)
    },
    handleSql(item, action) {
      this.currentTable = item;
      try {
        this.$refs.home[0].setSql(item);
      } catch (e) {
      }
    },
    // 增删tabs
    handleTabsEdit(item, action) {
      console.log('tab增删:', item, action);
      if (action == 'remove') {
        return false;
      }
      let tab = this.tabs.find(tab => Number(tab.id) == item.id);
      if (!tab) {
        this.tabs.push({
          id: item.id + "",
          name: item.id,
          label: item.label,
          type: item.type,
          path: item.path,
          close: !0
        })
      }
      this.activeRoute = item.id + ""
    },
    closeTab(targetname) {
      console.log(targetname)
      let tabs = this.tabs;
      let activeitem = this.activeRoute
      if (Number(activeitem) == targetname) {
        tabs.forEach((tab, index) => {
          if (Number(tab.id) == targetname) {
            let nexttab = tabs[index - 1] || tabs[index + 1]
            if (nexttab) {
              console.log(nexttab)
              activeitem = nexttab.id
            }
          }
        })
      }
      this.activeRoute = activeitem + "";
      this.tabs = tabs.filter(tab => tab.id !== targetname.toString())
    },
    tabClick(tab) {
      console.log(tab)
      const item = this.tabs.filter(tab => tab.id !== tab.index)
      this.$router.push({path: item[0].path})

    }
  }
}
</script>

<script setup>
import {
  Document,
  Menu as IconMenu,
  Location,
  Setting,
} from '@element-plus/icons-vue'

import {ref} from "vue";
import Home from "@/components/home/home.vue";
import Database from "@/components/database/database.vue";


</script>

<style scoped>
.aside {
  border-right: 1px solid #ddd;
  border-bottom: 1px solid #ddd;
}

.common-layout-padding {
  padding: 10px;
}

.common-layout {
  width: 100%;
  height: 100%;
  min-height: 700px;
}

el-container {
  height: 100%;
}

.panel-with-icon {
  padding-left: 18px;
}

.panel-icon {
  left: 5px;
  width: 16px;
}

.panel-tool el-icon {
  height: 16px;
  width: 16px;
  opacity: .6;
  color: blue;
}

.panel-icon, .panel-tool {
  position: absolute;
  top: 50%;
  margin-top: -8px;
  height: 16px;
  overflow: hidden;
  right: 8px;
}


.panel-tool a {
  display: inline-block;
  width: 16px;
  height: 16px;
  opacity: 0.6;
  filter: alpha(opacity=60);
  margin: 0 0 0 2px;
  vertical-align: top;
}

.panel-header {
  background-color: #ffffff;
}

.tabStyle{
  display: block;
  height: 65px;
}
.el-icon-basic-home{
  display: inline-block;
  width: 16px;
  height: 16px;
  position: relative;
  top: 4px;
  background: url('@/assets/icons/icon-standard/16x16/application-home.png') no-repeat top;
}

.panel-header, .panel-body {
  border-color: #ddd;
}

.panel-header {
  padding: 5px;
  position: relative;
}

.panel-header, .panel-body {
  border-width: 1px;
  border-style: solid;
}

.panel-title {
  font-size: 12px;
  font-weight: bold;
  color: #777;
  height: 16px;
  line-height: 16px;
}

.el-tabs__header {
  height: 28px !important;
  font-size: 10px;
  line-height: 28px;
}
.custom-tabs-label > span{
  margin-left: 6px;
}

.el-tabs {
  --el-tabs-header-height: 28px;
  --el-font-size-base: 12px;
}
.margin-5 {
  margin-right: 5px;
}
</style>