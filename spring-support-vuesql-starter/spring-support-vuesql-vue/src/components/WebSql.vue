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
                  :key="item.configId"
                  :label="item.configName"
                  :value="item.configId"

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
                  @row-contextmenu="rightclick"
              >
                <el-table-column prop="name" label="详情" show-overflow-tooltip style="font-size: 21px; cursor: none">
                  <template #default="scope">
                    <span class="l-btn-icon icon-berlin-calendar" v-if="scope.row.icon =='TABLE'"></span>
                    <span class="l-btn-icon icon-application-view-icons" v-else-if="scope.row.icon =='VIEW'"></span>
                    <span class="l-btn-icon icon-hamburg-database " v-else></span>

                    <el-text v-if="scope.row.type =='TABLE' || scope.row.type =='VIEW'" style="cursor: pointer; margin-left: 18px" @click="handleSql(scope.row)">
                      {{scope.row.name}}
                    </el-text>
                    <el-text style="margin-left: 18px" v-else>{{scope.row.name}}
                    </el-text>
                  </template>
                </el-table-column>
              </el-table>

              <!-- 右键菜单 -->
              <right-menu
                  :class-index="0"
                  :rightclickInfo="rightclickInfo"
                  @openTable="openTable"
              ></right-menu>
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
                  <template #label v-if="item.type == 'HOME'">
                    <span class="custom-tabs-label">
                      <span><span class="margin-5 l-btn-icon panel-icon icon-berlin-home"></span>{{ item.label }}</span>
                    </span>
                  </template>
                  <template #label v-if="item.type == 'DATABASE'">
                    <span class="custom-tabs-label">
                      <span> <span class="margin-5 l-btn-icon panel-icon icon-hamburg-database"></span>{{ item.label }}</span>
                    </span>
                  </template>

                  <template #label v-if=" item.type == 'TABLE'">
                    <span class="custom-tabs-label">
                      <span> <span
                          class="margin-5 l-btn-icon panel-icon icon-berlin-calendar"></span>{{ item.label }}</span>
                    </span>
                  </template>
                  <home v-if="item.type == 'HOME'"
                        ref="home"
                        :current-database-data="currentDatasource"
                        :loading="loading"
                        :current-table-data="currentTable"
                        :watch-data="watchData"
                        @event="onEvent"
                  ></home>
                  <database v-if="item.type == 'WEB-DATABASE'" :watch-data="watchData"></database>
                  <div v-if="item.type == 'TABLE' && item.action == 'OPEN'">
                    <open-table v-if="currentDatasource.configType !== 'ZOOKEEPER'"
                        :watch-data="watchData"
                        :config="currentDatasource"
                        :table="currentTable"></open-table>

                    <zookeeper v-if="currentDatasource.configType !== 'ZOOKEEPER'" :watch-data="watchData"
                               :config="currentDatasource"
                               :table="currentTable">

                    </zookeeper>
                  </div>

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
import '@/style/easy.css'
import Home from "@/components/home/home.vue";
import Database from "@/components/database/database.vue";
import Zookeeper from "@/components/zookeeper/Zookeeper.vue";
import OpenTable from "@/components/table/OpenTable.vue";
import RightMenu from "@/components/menu/RightMenu.vue";
import '@/assets/icons/icon-berlin.css'
import '@/assets/icons/icon-hamburg.css'
import '@/assets/icons/icon-standard.css'

export default {
  components: {Home, Database, OpenTable, RightMenu, Zookeeper},
  data() {
    return {
      loading: true,
      watchData: [],
      tableLoading: false,
      datasource: '',
      currentDatasource: undefined,
      closable: false,
      activeRoute: 'HOME',
      currentTable: undefined,
      rightclickInfo: {},
      tabs: [
        {
          id: 'HOME',
          label: '运行及展示',
          icon: 'HOME',
          type: 'HOME',
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
          if (data.code === '00000') {
            data.data.forEach((item, index) => {
              this.options.push(item)
            })
          }
        }).finally(() => this.loading = !1)
  },
  methods: {
    onEvent: function (item) {
      this.handleTabsEdit(item, "add");
    },
    changeDatabase: function () {
      const item = this.datasource;
      this.tableLoading = true;
      this.treeData.length = 0;
      this.currentDatasource = this.options.filter(it => it.configId === item)[0];
      this.tabs.forEach(item => {
        if(item.id === 'HOME') {
          return !1;
        }
        this.closeTab(item.id);
      })
      request.get(sformat(URL.GET_TABLE_INFO, this.currentDatasource))
          .then(({data}) => {
            if (data.code === '00000') {
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
      if (item.action == 'OPEN') {
        this.handleTabsEdit({
          id: item.id + item.name,
          name: item.id,
          label: item.name,
          realName: item.realName,
          type: item.type,
          path: item.path,
          close: !0,
          action: item.action
        }, 'add')
        return;
      }

      try {
        this.$refs.home[0].setSql(item);
      } catch (e) {
      }
    },
    // 增删tabs
    handleTabsEdit(item, action) {
      console.log('tab增删:', item, action);
      if (action === 'remove') {
        return false;
      }
      let tab = this.tabs.find(tab => tab.id === (item.id + item.label));
      if (!tab) {
        this.tabs.push({
          id: item.id + item.label,
          name: item.id,
          label: item.label,
          type: item.type,
          close: !0,
          action: item.action
        })
      }
      this.activeRoute = item.id + item.label
    },
    closeTab(targetname) {
      console.log(targetname)
      let tabs = this.tabs;
      let activeitem = this.activeRoute
      if (activeitem == targetname) {
        tabs.forEach((tab, index) => {
          if (tab.id == targetname) {
            let nexttab = tabs[index - 1] || tabs[index + 1]
            if (nexttab) {
              console.log(nexttab)
              activeitem = nexttab.id
            }
          }
        })
      }
      this.activeRoute = activeitem;
      this.tabs = tabs.filter(tab => tab.id !== targetname.toString())
    },
    tabClick(tab) {
      console.log(tab)
      const item = this.tabs.filter(tab => tab.id !== tab.index)

    },
    openTable(params) {
      const item = params.row;
      this.currentTable = item;
      if(item) {
        if(!item.children || item.children == 0) {
          this.handleTabsEdit({
            id: item.id + "",
            name: item.id,
            label: item.name,
            type: item.type,
            path: item.path,
            close: !0,
            action: 'OPEN'
          }, 'add')
          return !0;
        }
      }
      ElMessage({
        type: 'error',
        message: '不支持打开'
      })
    },
    rightclick(row, column, event) {
      if((!!row.children && row.children.length > 0) || row.action == 'OPEN') {
        this.rightclickInfo = {};
        return !0;
      }
      this.rightclickInfo = {
        position: {
          x: event.clientX,
          y: event.clientY,
        },
        menulists: [
          {
            fnName: "openTable",
            params: { row, column, event },
            icoName: "menu-icon  icon-table-edit",
            btnName: "打开表",
          },
          {
            fnName: "look",
            params: { row, column, event },
            icoName: "el-icon-view",
            btnName: "查看行数据",
          },
          {
            fnName: "edit",
            params: { row, column, event },
            icoName: "el-icon-edit",
            btnName: "编辑行数据",
          },
          {
            fnName: "delete",
            params: { row, column, event },
            icoName: "el-icon-delete",
            btnName: "删除行数据",
          },
          {
            fnName: "refresh",
            params: { row, column, event },
            icoName: "el-icon-refresh",
            btnName: "刷新页面",
          },
        ],
      };
      event.preventDefault(); // 阻止默认的鼠标右击事件
    },
  }
}
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
  margin: 0!important;

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

.margin-l-5 {
  margin-left: 20px;
}
</style>