import{_ as a,r,o as c,e as i,w as n,g as e,t as s}from"./index-bd432aa0.js";const _={title:"时钟",icon:"el-icon-clock",description:"演示部件效果",data(){return{time:"",day:""}},mounted(){this.showTime(),setInterval(()=>{this.showTime()},1e3)},methods:{showTime(){this.time=this.$TOOL.dateFormat(new Date,"hh:mm:ss"),this.day=this.$TOOL.dateFormat(new Date,"yyyy年MM月dd日")}}},d={class:"time"};function l(p,h,u,v,t,y){const o=r("el-card");return c(),i(o,{shadow:"hover",header:"时钟",class:"item-background"},{default:n(()=>[e("div",d,[e("h2",null,s(t.time),1),e("p",null,s(t.day),1)])]),_:1})}const m=a(_,[["render",l],["__scopeId","data-v-d0ca500a"],["__file","F:/workspace/vue-support-parent-starter/vue-support-scheduler-starter/src/views/home/widgets/components/time.vue"]]),f=Object.freeze(Object.defineProperty({__proto__:null,default:m},Symbol.toStringTag,{value:"Module"}));export{f as _};
