<template>
  <el-text v-if="!search">
    <el-text v-for="item in watchData">
      {{ item }}
    </el-text>
  </el-text>
  <div v-else>
    <!--@sort-change="sortChange"-->
    <el-table show-overflow-tooltip :data="tableData" style="width: 100%" border stripe>
      <el-table-column v-for="item in tableColumn" show-overflow-tooltip :prop="item.name"
                       :label="item.label"/>
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
    type: null,
    watchData: {
      type: Array,
      default: []
    },
    search: {
      type: Boolean,
      default: false
    }
  },
  computed: {
    sql: function () {
      return this.config();
    },
    getUrl: function () {
      if (this.type == 'explain') {
        return sformat(URL.EXPLAIN, this.config);
      }
      return sformat(URL.EXECUTE, this.config);
    }
  },
  watch: {
    sql: function (n, o) {
      this.doSearch();
    }
  },
  data() {
    return {
      total: 0,
      tableColumn: [],
      tableData: [],
      pageNum: 1,
      pageSize: 10,
      orderColumn: undefined,
      orderType: 'asc'
    }
  },
  mounted() {
    if (!this.config || !this.sql) {
      return;
    }
    this.doSearch();
  },
  methods: {
    sortChange(column) {
      this.orderType = column.order.startsWith("asc") ? "asc" : "desc";
      this.orderColumn = column.prop;
      this.doSearch();
    },
    doSearch: function () {
      this.tableData.length = 0;
      this.tableColumn.length = 0;
      request.post(this.getUrl, {
        pageNum: this.pageNum,
        pageSize: this.pageSize,
        sql: this.sql,
        orderColumn: this.orderColumn,
        orderType: this.orderType
      }).then(({data}) => {
        if (data.code == '00000') {
          let rs = data.data;
          this.total = rs.total;
          rs.columns.forEach((item) => {
            this.tableColumn.push({
              name: item,
              label: item
            })
          })
          for (let item of rs.data) {
            this.tableData.push(item);
          }
          return false;
        } else {
          this.watchData.push(data.msg);
        }
      });
    },
    handleSizeChange: function (e) {
      this.pageSize = e;
      this.doSearch();
      return !1;
    },
    handleCurrentChange: function (e) {
      this.pageNum = e;
      this.doSearch();
      return !1;
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