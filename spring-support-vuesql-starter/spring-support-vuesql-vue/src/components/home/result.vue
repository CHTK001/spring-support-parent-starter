<template>
  <el-text v-if="!search">
    <el-text v-for="item in info">
      {{item}}
    </el-text>
  </el-text>
  <div v-else>
    <el-table :data="tableData" style="width: 100%" border>
      <el-table-column v-for="item in tableColumn" show-overflow-tooltip :prop="item.name" :label="item.label" />
    </el-table>
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
    request.post(sformat(URL.EXECUTE, this.config), {
        pageNum: this.pageNum,
        pageSize: this.pageSize,
        sql: this.sql
    }).then(({data}) => {
      if(data.code == '00000') {
        let rs = data.data;
        rs.columns.forEach((item) => {
          this.tableColumn.push({
            name: item,
            label: item
          })
        })
          for(let item of rs.data) {
              this.tableData.push(item);
          }
        return
      } else {
        this.info.push(data.msg);
      }
    });
  },
  methods: {

  }
}
</script>
<style>
</style>