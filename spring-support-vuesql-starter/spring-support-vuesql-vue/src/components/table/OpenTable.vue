<template>
  <el-skeleton :loading="loading" animated>
    <el-table show-overflow-tooltip :data="tableData" style="width: 100%" border stripe>
      <el-table-column v-for="item in tableColumn" show-overflow-tooltip :prop="item.name"
                       :label="item.label"/>
    </el-table>

    <div class="demo-pagination-block">
      <el-pagination
          v-model:current-page="form.pageNum"
          v-model:page-size="form.pageSize"
          small="small"
          :total="total"
          ref="pageGroup"
          layout="->,prev, next, ->,"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
      />
    </div>
  </el-skeleton>
</template>

<script>
import request from "axios";
import URL from '@/config/url'
import {sformat} from "@/utils/Utils";
export default {
  name: "OpenTable",
  props: {
    config: undefined,
    table: undefined,
    watchData: {
      type: Array,
      default: []
    },

  },
  data(){
    return {
      total: 0,
      loading: false,
      tableColumn: [],
      tableData: [],
      form: {
        pageNum: 1,
        pageSize: 10
      }

    }
  },
  mounted() {
    this.loading = !0;
    request.get(sformat(URL.OPEN_TABLE, this.form, this.config, this.table), {
      params: this.form
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
        this.watchData.push(this.sql + " " + data.msg);
        for (let item of rs.data) {
          this.tableData.push(item);
        }
        return false;
      } else {
        this.watchData.push(data.msg);
      }
    }).catch(({data}) => {
      this.watchData.push(data.msg);

    }).finally(() => this.loading = !1)
  },
  methods: {
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

</style>