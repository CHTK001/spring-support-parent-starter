<template>
  <div style="min-height: 300px">
    <div v-if="!search" style="position: relative; ">
      <ul class="infinite-list" style="overflow: auto; font-size: 12px">
        <li v-for="(item, i) in watchData" :key="i" class="infinite-list-item">{{ item }}</li>
      </ul>
      <el-button style="position: absolute; top: 5px; right: 18px;" @click="clearWatchData">
        <el-icon>
          <DeleteFilled/>
        </el-icon>
      </el-button>
    </div>
    <div v-else>
      <div id="operator2"
           class="panel-header panel-header-noborder"
           style="height:24px; border-left: solid 1px #ddd; border-right: solid 1px #ddd">
        <div>
          <a class="easyui-linkbutton l-btn l-btn-small l-btn-plain"><span><span
              class="l-btn-text">耗时: {{ cost }}</span></span></a>
        </div>

        <!--@sort-change="sortChange"-->
        <el-table show-overflow-tooltip :data="tableData" style="width: 100%" border stripe>
          <el-table-column v-for="item in tableColumn" show-overflow-tooltip :prop="item.columnName"
                           :label="item.columnName"/>
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
      orderType: 'asc',
      cost: 0
    }
  },
  mounted() {
    if (!this.config || !this.sql) {
      return;
    }
    this.doSearch();
  },
  methods: {
    clearWatchData: function () {
      this.watchData.length = 0;
    },
    sortChange(column) {
      this.orderType = column.order.startsWith("asc") ? "asc" : "desc";
      this.orderColumn = column.prop;
      this.doSearch();
    },
    doSearch: function () {
      this.tableData.length = 0;
      this.tableColumn.length = 0;
      const startTime = new Date().getTime();
      request.post(this.getUrl, {
        pageNum: this.pageNum,
        pageSize: this.pageSize,
        sql: this.sql,
        orderColumn: this.orderColumn,
        orderType: this.orderType
      }).then(({data}) => {
        if (data.code === '00000') {
          let rs = data.data;
          this.total = rs.total;
          if (rs.data.length < this.pageSize) {
            this.total = (this.pageNum - 1) * this.pageSize + rs.data.length;
          } else if (rs.data.length === 0) {
            this.total = (this.pageNum) * this.pageSize;
          }
          this.tableColumn = rs.columns;
          this.watchData.push(this.sql + " " + data.msg);
          for (let item of rs.data) {
            this.tableData.push(item);
          }
          return !0;
        } else {
          this.watchData.push(data.msg);
        }
      }).catch(({data}) => {
        this.watchData.push(data.msg);
      }).finally(() => {
        this.cost = (new Date().getTime() - startTime) + " ms"
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