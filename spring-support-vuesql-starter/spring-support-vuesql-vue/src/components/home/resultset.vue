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
    </el-tab-pane>
  </el-tabs>
</template>

<script>

export default {
  name: "resultSet",
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
    configId: Object
  },
  mounted() {
  },
  methods: {
    addTab1: function(targetName) {
      const newTabName = `${++this.tabIndex}`
      this.editableTabs.push({
        label: targetName,
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
      this.editableTabs.length = 0;
      this.editableTabs.push( {
        id: '1',
        label: '结果',
        content: 'Tab 1 content',
        close: false
      });
      this.editableTabsValue = '1';
      this.tabIndex = 0;
    },
    run: function (sql) {
      this.reset();
      this.tabIndex = 1;
      this.editableTabsValue = '2';
      let index = 0;
      for(let item of sql.split(";")) {
        this.addTab1("结果" + ++ index);
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