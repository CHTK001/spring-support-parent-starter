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
      <result v-if="item.id == '1'" :sql="item.sql" :search="false" ></result>
      <result v-else :sql="item.sql" :search="true" :config="config"></result>
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
      editableTabs:[ {
        id: '1',
        label: '结果',
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
    addTab1: function(sql, config) {
      const newTabName = `${++this.tabIndex}`
      this.editableTabs.push({
        label: "结果" + newTabName,
        sql: sql,
        id: this.editableTabs.length + 1 + '',
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
      let collectionOf = document.getElementsByClassName('is-icon-close');
      if(!!collectionOf && !!collectionOf.length) {
        for (let el of collectionOf) {
          el.click();
        }
      }
      this.tabIndex = 1;
    },
    run: function () {
      this.reset();
      let index = 1;
      for(let sql of this.sql.split(";")) {
        if(!sql) {
          continue
        }
        this.addTab1(sql, this.config);
      }
    }
  }
}
</script>
<style>
.demo-tabs > .el-tabs__content {
  padding: 32px;
  color: #6b778c;
  font-size: 32px;
  font-weight: 600;
}

.el-tabs {
  --el-tabs-header-height: 28px;
  --el-font-size-base: 12px;
}
</style>