import{_ as v,r as n,o as c,c as l,g as r,h,t,e as g,w as s,a as i,b as u,x as k,i as O,F}from"./index-bd432aa0.js";const b={props:{title:{type:String,required:!0,default:""},value:{type:String,required:!0,default:""},prefix:{type:String,default:""},suffix:{type:String,default:""},description:{type:String,default:""},tips:{type:String,default:""},groupSeparator:{type:Boolean,default:!1}},data(){return{}},computed:{cmtValue(){return this.groupSeparator?this.$TOOL.groupSeparator(this.value):this.value}}},C={class:"sc-statistic"},T={class:"sc-statistic-title"},V={style:{width:"200px","line-height":"2"}},$={class:"sc-statistic-content"},B={key:0,class:"sc-statistic-content-prefix"},L={class:"sc-statistic-content-value"},q={key:1,class:"sc-statistic-content-suffix"},z={key:0,class:"sc-statistic-description"};function N(o,x,e,y,d,m){const p=n("el-icon-question-filled"),f=n("el-icon"),_=n("el-tooltip");return c(),l("div",C,[r("div",T,[h(t(e.title)+" ",1),e.tips?(c(),g(_,{key:0,effect:"light"},{content:s(()=>[r("div",V,t(e.tips),1)]),default:s(()=>[i(f,{class:"sc-statistic-tips"},{default:s(()=>[i(p)]),_:1})]),_:1})):u("v-if",!0)]),r("div",$,[e.prefix?(c(),l("span",B,t(e.prefix),1)):u("v-if",!0),r("span",L,t(m.cmtValue),1),e.suffix?(c(),l("span",q,t(e.suffix),1)):u("v-if",!0)]),e.description||o.$slots.default?(c(),l("div",z,[k(o.$slots,"default",{},()=>[h(t(e.description),1)],!0)])):u("v-if",!0)])}const A=v(b,[["render",N],["__scopeId","data-v-5a75fe71"],["__file","F:/workspace/vue-support-parent-starter/vue-support-scheduler-starter/src/components/scStatistic/index.vue"]]),j={title:"磁盘空间",icon:"el-icon-odometer",description:"磁盘空间",components:{scStatistic:A},data(){return{files:[],value:39.58,colors:[{color:"#67C23A",percentage:80},{color:"#E6A23C",percentage:60},{color:"#F56C6C",percentage:40}]}},mounted(){this.$API.system.oshi.space.get().then(o=>{if(o.code==="00000")return this.files=o.data,!1})},methods:{format(o){return o+"G"}}},E={style:{color:"#6e6767"}};function I(o,x,e,y,d,m){const p=n("el-button"),f=n("el-progress"),_=n("el-card"),S=n("el-col"),w=n("el-row");return c(),g(_,{shadow:"never",header:"当前已用量"},{default:s(()=>[i(w,{gutter:15,style:{"margin-top":"20px"}},{default:s(()=>[(c(!0),l(F,null,O(d.files,a=>(c(),g(S,{lg:12},{default:s(()=>[i(_,{shadow:"never"},{default:s(()=>[r("span",null,[r("h2",null,[r("b",null,t(a.name),1)])]),i(f,{"stroke-width":20,color:d.colors,percentage:(a.free/a.total*100).toFixed(2)},{default:s(()=>[i(p,{text:""},{default:s(()=>[h(t(this.$TOOL.sizeFormat(a.total)),1)]),_:2},1024)]),_:2},1032,["color","percentage"]),r("span",E,t(this.$TOOL.sizeFormat(a.free))+" 可用, 共 "+t(this.$TOOL.sizeFormat(a.total)),1)]),_:2},1024)]),_:2},1024))),256))]),_:1})]),_:1})}const P=v(j,[["render",I],["__file","F:/workspace/vue-support-parent-starter/vue-support-scheduler-starter/src/views/home/widgets/components/space.vue"]]),G=Object.freeze(Object.defineProperty({__proto__:null,default:P},Symbol.toStringTag,{value:"Module"}));export{G as _};
