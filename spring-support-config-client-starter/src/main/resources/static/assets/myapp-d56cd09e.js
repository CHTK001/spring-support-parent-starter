import{d as b}from"./vuedraggable.umd-1403d7e2.js";import{_ as w,D as V,r as d,o as r,c as p,d as s,F as D,f as N,x as _,b as a,w as l,g as u,i as c,t as i,e as M}from"./index-56cdd313.js";const C={components:{draggable:b},data(){return{mods:[],myMods:[],myModsName:[],filterMods:[],modsDrawer:!1}},mounted(){this.getMods()},methods:{addMods(){this.modsDrawer=!0},getMods(){this.myModsName=this.$TOOL.data.get("my-mods")||[];var o=this.$TOOL.data.get(V.MENU);this.filterMenu(o),this.myMods=this.mods.filter(e=>this.myModsName.includes(e.name)),this.filterMods=this.mods.filter(e=>!this.myModsName.includes(e.name))},filterMenu(o){o.forEach(e=>{if(e.meta.hidden||e.meta.type=="button")return!1;e.meta.type=="iframe"&&(e.path=`/i/${e.name}`),e.children&&e.children.length>0?this.filterMenu(e.children):this.mods.push(e)})},saveMods(){const o=this.myMods.map(e=>e.name);this.$TOOL.data.set("my-mods",o),this.$message.success("设置常用成功"),this.modsDrawer=!1}}},O={class:"myMods"},T=["href"],L={href:"javascript:void(0)"},U={class:"setMods"},B={class:"setMods"};function E(o,e,F,I,n,f){const m=d("el-icon"),g=d("router-link"),k=d("el-icon-plus"),h=d("draggable"),y=d("el-button"),v=d("el-drawer");return r(),p("div",null,[s("ul",O,[(r(!0),p(D,null,N(n.myMods,t=>(r(),p("li",{key:t.path,style:_({background:t.meta.color||"#909399"})},[t.meta.type=="link"?(r(),p("a",{key:0,href:t.path,target:"_blank"},[a(m,null,{default:l(()=>[(r(),u(c(t.meta.icon||o.el-o.icon-o.menu)))]),_:2},1024),s("p",null,i(t.meta.title),1)],8,T)):(r(),u(g,{key:1,to:{path:t.path}},{default:l(()=>[a(m,null,{default:l(()=>[(r(),u(c(t.meta.icon||o.el-o.icon-o.menu)))]),_:2},1024),s("p",null,i(t.meta.title),1)]),_:2},1032,["to"]))],4))),128)),s("li",{class:"modItem-add",onClick:e[0]||(e[0]=(...t)=>f.addMods&&f.addMods(...t))},[s("a",L,[a(m,null,{default:l(()=>[a(k)]),_:1})])])]),a(v,{title:"添加应用",modelValue:n.modsDrawer,"onUpdate:modelValue":e[4]||(e[4]=t=>n.modsDrawer=t),size:570,"destroy-on-close":""},{footer:l(()=>[a(y,{onClick:e[3]||(e[3]=t=>n.modsDrawer=!1)},{default:l(()=>[M("取消")]),_:1}),a(y,{type:"primary",onClick:f.saveMods},{default:l(()=>[M("保存")]),_:1},8,["onClick"])]),default:l(()=>[s("div",U,[s("h4",null,"我的常用 ( "+i(n.myMods.length)+" )",1),a(h,{tag:"ul",modelValue:n.myMods,"onUpdate:modelValue":e[1]||(e[1]=t=>n.myMods=t),animation:"200","item-key":"path",group:"people"},{item:l(({element:t})=>[s("li",{style:_({background:t.meta.color||"#909399"})},[a(m,null,{default:l(()=>[(r(),u(c(t.meta.icon||o.el-o.icon-o.menu)))]),_:2},1024),s("p",null,i(t.meta.title),1)],4)]),_:1},8,["modelValue"])]),s("div",B,[s("h4",null,"全部应用 ( "+i(n.filterMods.length)+" )",1),a(h,{tag:"ul",modelValue:n.filterMods,"onUpdate:modelValue":e[2]||(e[2]=t=>n.filterMods=t),animation:"200","item-key":"path",sort:!1,group:"people"},{item:l(({element:t})=>[s("li",{style:_({background:t.meta.color||"#909399"})},[a(m,null,{default:l(()=>[(r(),u(c(t.meta.icon||o.el-o.icon-o.menu)))]),_:2},1024),s("p",null,i(t.meta.title),1)],4)]),_:1},8,["modelValue"])])]),_:1},8,["modelValue"])])}const j=w(C,[["render",E],["__scopeId","data-v-37b35e26"],["__file","Z:/workspace/vue-support-parent-starter/vue-support-config-starter/src/views/home/work/components/myapp.vue"]]);export{j as default};
