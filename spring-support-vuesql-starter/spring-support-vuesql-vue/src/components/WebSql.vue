<template>
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
          <el-select v-model="datasource" class="m-2" placeholder="Select">
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
              <el-icon>
                <RefreshRight/>
              </el-icon>
            </a>
          </div>
        </div>

        <div class="common-layout-padding">
          <el-table
              :data="treeData"
              style="width: 100%; margin-bottom: 20px"
              row-key="id"
              border
              default-expand-all
          >
            <el-table-column prop="name" label="详情" style="font-size: 21px">
              <template #default="scope">
                <el-icon v-if="scope.row.type =='table'" style="margin-right: 5px">
                  <Tickets/>
                </el-icon>
                <el-text style="cursor: pointer" v-if="scope.row.type=='sub'"
                         @click="handleTabsEdit(scope.row, 'add')">{{ scope.row.name }}
                </el-text>
                <el-text v-else>{{ scope.row.name }}</el-text>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-aside>

      <el-container>
        <el-header height="30px">
          <div class="page-tabs-index">
            <el-tabs v-model="activeRoute" type="card" @tab-remove='closeTab' :closable="closable" @tab-click="tabClick"
                     @edit="handleTabsEdit" height="30">
              <el-tab-pane
                  :key="item.id"
                  v-for="(item, index) in tabs"
                  :label="item.label"
                  :name="item.id"
                  :closable="item.close"
              >
                <router-view></router-view>
              </el-tab-pane>
            </el-tabs>
          </div>
        </el-header>
      </el-container>
    </el-container>
  </div>
</template>

<script>
export default {
  data() {
    return {
      datasource: '',
      closable: false,
      activeRoute: '0',
      tabs: [
        {
          id: '0',
          label: '运行及展示',
          path: "/home",
          content: 'Tab 1 content',
          close: false
        }
      ],
      treeData: [{
        id: 1,
        name: 'water',
        children: [{
          id: 2,
          name: '表',
          type: 'table',
          children: [{
            id: 21,
            name: 'admin',
            type: 'sub'
          }]
        }, {
          id: 3,
          name: '视图',
        },
        ],
      }],
      options: [
        {
          value: 'Option1',
          label: 'Option1',
        },
        {
          value: 'Option2',
          label: 'Option2',
        },
        {
          value: 'Option3',
          label: 'Option3',
        },
        {
          value: 'Option4',
          label: 'Option4',
        },
        {
          value: 'Option5',
          label: 'Option5',
        },
      ]
    }
  },
  mounted() {
    this.$router.push('/home');
  },
  methods: {
    // 增删tabs
    handleTabsEdit(item, action) {
      console.log('tab增删:', item, action);
      if(action == 'remove') {
        return false;
      }
      let tab = this.tabs.find(tab => Number(tab.id) == item.id);
      if (!tab) {
        this.tabs.push({
          id: item.id + "",
          name: item.id,
          label: item.name,
          path: item.path,
          close: true
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
</style>