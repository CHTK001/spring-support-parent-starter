import{g as m}from"./Utils-25fe7b8b.js";import{_ as u,r as o,o as h,e as p,w as s,a as n,g as e,t as a,h as g,x as f}from"./index-bd432aa0.js";const v={data(){return{}},props:{title:{type:String,default:""}},methods:{getImg(t){return m(t)}}},N={class:"common-header-left"},P={class:"common-header-logo"},k=["alt","src"],A={class:"common-header-title"},I={class:"common-header-right"},w={class:"common-container"},x={class:"common-title"},C={class:"common-main el-card"};function $(t,y,c,B,F,l){const r=o("router-link"),i=o("el-header"),_=o("el-main"),d=o("el-container");return h(),p(d,null,{default:s(()=>[n(i,{style:{height:"50px"}},{default:s(()=>[e("div",N,[e("div",P,[e("img",{alt:t.$CONFIG.APP_NAME,src:l.getImg("logo.png")},null,8,k),e("label",null,a(t.$CONFIG.APP_NAME),1)]),e("div",A,a(c.title),1)]),e("div",I,[n(r,{to:"/login"},{default:s(()=>[g("返回登录")]),_:1})])]),_:1}),n(_,null,{default:s(()=>[e("div",w,[e("h2",x,a(c.title),1),e("div",C,[f(t.$slots,"default")])])]),_:3})]),_:3})}const E=u(v,[["render",$],["__file","F:/workspace/vue-support-parent-starter/vue-support-scheduler-starter/src/views/login/components/commonPage.vue"]]);export{E as default};