<template>
zk
</template>

<script>
import request from "axios";
import URL from "@/config/url";
import {sformat} from "@/utils/Utils";

export default {
  name: "Zookeeper",
  props: {
    config: undefined,
    table: undefined,
    watchData: {
      type: Array,
      default: []
    },
  },
  data() {
    return {
      data: {
        tableData: []
      },
      status: {
        loading: !0,
      },
      form: {},
      cost: 0
    }
  },
  mounted() {
    this.doSearch();
  },
  methods:{
    reset: function () {
      this.data.tableData.length = 0;
    },
    doSearch: function () {
      const startTime = new Date().getTime();
      request.get(sformat(URL.OPEN_TABLE, this.form, this.config, this.table), {
        params: this.form
      }).then(({data}) => {
        if (data.code === '00000') {
          let rs = data.data;
          this.watchData.push("打开" + this.table.name + " " + data.msg);
          for (let item of rs.data) {
            this.data.tableData.push(item);
          }
          return false;
        } else {
          this.watchData.push("打开" + this.table.name + " " + data.msg);
        }
      }).catch(({data}) => {
        this.watchData.push("打开" + this.table.name + " " + data.msg);

      }).finally(() => {
        this.status.loading = !1;
        this.cost = (new Date().getTime() - startTime) + ' ms'
      })
    }
  }
}
</script>

<style scoped>

</style>