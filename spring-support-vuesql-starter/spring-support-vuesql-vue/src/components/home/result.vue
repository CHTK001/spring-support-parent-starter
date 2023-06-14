<template>
  <el-text v-if="!search">
    <el-text v-for="item in info">
      {{item}}
    </el-text>
  </el-text>
  <div v-else>
    <el-table :data="tableData" style="width: 100%" border stripe >
      <el-table-column v-for="item in tableColumn" width="100" show-overflow-tooltip :prop="item.name" :label="item.label" />
    </el-table>

    <div class="demo-pagination-block">
      <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          small="small"
          layout="->, prev, pager, next, jumper, ->, total"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
      />
    </div>
  </div>
</template>

<script>
import request from "axios";
import URL from "@/config/url";
import {sformat} from "@/utils/Utils";
export default {
  name: "result",
  props: {
    config: Object,
    sql: String,
    search: {
      type: Boolean,
      default: false
    }
  },
  data() {
      return {
        info: [],
        total: 0,
        tableColumn: [],
        tableData: [],
        pageNum: 1,
        pageSize: 10
      }
  },
  mounted() {
    if(!this.config || !this.sql) {
      return;
    }

    this.doSearch();
  },
  methods: {
    doSearch: function () {
      this.total = 0;
      request.post(sformat(URL.EXECUTE, this.config), {
        pageNum: this.pageNum,
        pageSize: this.pageSize,
        sql: this.sql
      }).then(({data}) => {
        if(data.code == '00000') {
          let rs = data.data;
          this.total = rs.total;
          rs.columns.forEach((item) => {
            this.tableColumn.push({
              name: item,
              label: item
            })
          })
          for(let item of rs.data) {
            this.tableData.push(item);
          }
          return false;
        } else {
          this.info.push(data.msg);
        }
      });
    },
    handleSizeChange: function (e) {
      this.pageSize = e;
      this.doSearch();
    },
    handleCurrentChange: function (e) {
      this.pageNum = e;
      this.doSearch();
    }
  }
}
</script>
<style scoped>
#searchHistoryPanel el-tabs__content {
  padding: 0 !important;
}
.demo-pagination-block {
  margin-top: 10px;
}
</style>