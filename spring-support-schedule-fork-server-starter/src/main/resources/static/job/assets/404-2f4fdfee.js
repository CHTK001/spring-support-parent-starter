import{g as _}from"./Utils-25fe7b8b.js";import{_ as l,r as p,o as d,c as i,g as o,a as s,w as c,h as a,p as u,k as g}from"./index-452c6e27.js";const h={data(){return{}},methods:{getImg(e){return _(e)},gohome(){location.href="#/"},goback(){this.$router.go(-1)},gologin(){this.$router.push("/login")}}},n=e=>(u("data-v-0885cf8e"),e=e(),g(),e),m={class:"router-err"},f={class:"router-err__icon"},k=["src"],v={class:"router-err__content"},y=n(()=>o("h2",null,"无权限或找不到页面",-1)),C=n(()=>o("p",null,"当前页面无权限访问或者打开了一个不存在的链接，请检查当前账户权限和链接的可访问性。",-1));function I(e,x,b,w,B,t){const r=p("el-button");return d(),i("div",m,[o("div",f,[o("img",{src:t.getImg("logo.png")},null,8,k)]),o("div",v,[y,C,s(r,{type:"primary",plain:"",round:"",onClick:t.gohome},{default:c(()=>[a("返回首页")]),_:1},8,["onClick"]),s(r,{type:"primary",plain:"",round:"",onClick:t.gologin},{default:c(()=>[a("重新登录")]),_:1},8,["onClick"]),s(r,{type:"primary",round:"",onClick:t.goback},{default:c(()=>[a("返回上一页")]),_:1},8,["onClick"])])])}const V=l(h,[["render",I],["__scopeId","data-v-0885cf8e"],["__file","Z:/workspace/vue-support-parent-starter/vue-support-scheduler-starter/src/layout/other/404.vue"]]);export{V as default};
