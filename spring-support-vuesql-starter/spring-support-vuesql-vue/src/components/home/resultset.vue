<template>
  <el-tabs
      v-model="editableTabsValue"
      type="card"
      class="demo-tabs"
      @tab-remove="removeTab1"
  >
    <el-tab-pane
        v-for="item in editableTabs"
        :key="item.id"
        :label="item.label"
        :name="item.id"
        :closable="item.close"
    >
      <result v-if="item.type == null" :type="item.type" :watch-data="watchData" :sql="item.sql"
              :search="false"></result>
      <result v-else-if="item.type == 'sql'" :type="item.type" :sql="item.sql" :watch-data="watchData" :search="true"
              :config="config"></result>
      <result v-else-if="item.type == 'explain'" :type="item.type" :sql="item.sql" :watch-data="watchData"
              :search="true" :config="config"></result>
    </el-tab-pane>
  </el-tabs>
</template>

<script>
import Result from "@/components/home/result.vue";

export default {
  name: "resultSet",
  components: {Result},
  data() {
    return {
      tabIndex: 1,
      editableTabsValue: '1',
      watchData: [],
      editableTabs: [{
        id: '1',
        label: '消息',
        content: 'Tab 1 content',
        close: false
      }]
    }
  },
  props: {
    config: Object,
    sql: String
  },
  mounted() {
  },
  methods: {
    addTab1: function (sql, type) {
      const newTabName = `${++this.tabIndex}`
      this.editableTabs.push({
        label: "结果" + newTabName,
        sql: sql,
        type: type,
        id: newTabName,
        close: true
      })
      this.editableTabsValue = newTabName
    },
    removeTab1: function(targetName) {
      const tabs = this.editableTabs
      let activeName = this.editableTabsValue
      if (activeName === targetName) {
        tabs.forEach((tab, index) => {
          if (tab.id === targetName) {
            const nextTab = tabs[index + 1] || tabs[index - 1]
            if (nextTab) {
              activeName = nextTab.id
            }
          }
        })
      }

      this.editableTabsValue = activeName
      this.editableTabs = tabs.filter((tab) => tab.id !== targetName)
    },
    reset: function() {
      this.editableTabs.length = 0;
      this.editableTabs = [{
        id: '1',
        label: '消息',
        content: 'Tab 1 content',
        close: false
      }]
      this.tabIndex = 1;
    },
    run: function () {
      this.reset();
      let index = 1;
      for (let sql of this.sql.split(";")) {
        if (!sql) {
          continue
        }
        this.addTab1(sql, "sql");
      }
    },
    explainSQL: function (sql) {
      this.reset();
      this.addTab1(sql, 'explain');
    }
  }
}
</script>
<style>
.demo-tabs > .el-tabs__content {
  color: #6b778c;
  font-size: 32px;
  font-weight: 600;
}

.el-tabs {
  --el-tabs-header-height: 28px;
  --el-font-size-base: 12px;
}
</style>