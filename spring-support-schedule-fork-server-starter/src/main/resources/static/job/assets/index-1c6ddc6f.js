import{_ as M,r,o,c as d,a as s,b as m,F as $,i as V,e as c,w as i,g as a,t as T,B as F,j as E,y as R,z as S,T as X,h as I,x as j,p as Q,k as J,S as ee,n as A,l as te,Q as se,D as L,R as G,U as z,V as D}from"./index-452c6e27.js";import{g as B}from"./Utils-25fe7b8b.js";import{a as oe}from"./index-1f4f24f2.js";import"./about-189e1e89.js";import"./echarts-cb266609.js";import"./index-8baa2384.js";import"./progress-2e401ac1.js";import"./space-f5a30a92.js";import"./time-984e5dcc.js";import"./ver-c7dec924.js";import"./welcome-f8f4fcce.js";const ne={name:"NavMenu",props:["navMenus"],data(){return{}},methods:{hasChildren(e){return e.children=e.children?e.children.filter(t=>!t.hidden):e.children,e.children&&!e.children.every(t=>t.meta.hidden)}}},le={key:0,style:{padding:"20px"}},ie=["href"],ae={key:0,class:"menu-tag"},re={key:1,class:"menu-tag"};function ue(e,t,u,v,l,n){const _=r("el-alert"),g=r("el-icon"),b=r("el-menu-item"),p=r("NavMenu",!0),f=r("el-sub-menu");return o(),d($,null,[u.navMenus.length<=0?(o(),d("div",le,[s(_,{title:"无子集菜单",center:"",type:"info",closable:!1})])):m("v-if",!0),(o(!0),d($,null,V(u.navMenus,h=>(o(),d($,{key:h},[n.hasChildren(h)?(o(),c(f,{key:1,index:h.path},{title:i(()=>[h.meta&&h.meta.icon?(o(),c(g,{key:0},{default:i(()=>[(o(),c(E(h.meta.icon||"el-icon-menu")))]),_:2},1024)):m("v-if",!0),a("span",null,T(h.meta.title),1),h.meta.tag?(o(),d("span",re,T(h.meta.tag),1)):m("v-if",!0)]),default:i(()=>[s(p,{navMenus:h.children},null,8,["navMenus"])]),_:2},1032,["index"])):(o(),c(b,{key:0,index:h.path},{title:i(()=>[a("span",null,T(h.meta.title),1),h.meta.tag?(o(),d("span",ae,T(h.meta.tag),1)):m("v-if",!0)]),default:i(()=>[h.meta&&h.meta.type=="link"?(o(),d("a",{key:0,href:h.path,target:"_blank",onClick:t[0]||(t[0]=F(()=>{},["stop"]))},null,8,ie)):m("v-if",!0),h.meta&&h.meta.icon?(o(),c(g,{key:1},{default:i(()=>[(o(),c(E(h.meta.icon||"el-icon-menu")))]),_:2},1024)):(o(),c(g,{key:2},{default:i(()=>[(o(),c(E("el-icon-menu")))]),_:1}))]),_:2},1032,["index"]))],64))),128))],64)}const q=M(ne,[["render",ue],["__file","Z:/workspace/vue-support-parent-starter/vue-support-scheduler-starter/src/layout/components/NavMenu.vue"]]);const ce={components:{NavMenu:q},data(){return{nav:!1,menu:[]}},computed:{},created(){var e=this.$router.sc_getMenu();this.menu=this.filterUrl(e)},watch:{},methods:{getImg(e){return B(e)},showMobileNav(e){var t=e.currentTarget.getAttribute("drag-flag");if(t=="true")return!1;this.nav=!0},select(){this.$refs.mobileNavBox.handleClose()},filterUrl(e){var t=[];return e&&e.forEach(u=>{if(u.meta=u.meta?u.meta:{},u.meta.hidden||u.meta.type=="button")return!1;u.meta.type=="iframe"&&(u.path=`/i/${u.name}`),u.children&&u.children.length>0&&(u.children=this.filterUrl(u.children)),t.push(u)}),t}},directives:{drag(e){let t=e,u="",v="";t.onmousedown=function(l){let n=l.clientX-t.offsetLeft,_=l.clientY-t.offsetTop;return document.onmousemove=function(g){t.setAttribute("drag-flag",!0),u=new Date().getTime();let b=g.clientX-n,p=g.clientY-_;p>0&&p<document.body.clientHeight-50&&(t.style.top=p+"px"),b>0&&b<document.body.clientWidth-50&&(t.style.left=b+"px")},document.onmouseup=function(){v=new Date().getTime(),v-u>200&&t.setAttribute("drag-flag",!1),document.onmousemove=null,document.onmouseup=null},!1}}}},de={class:"logo-bar"},me=["src"];function _e(e,t,u,v,l,n){const _=r("el-icon-menu"),g=r("el-icon"),b=r("el-header"),p=r("NavMenu"),f=r("el-menu"),h=r("el-scrollbar"),C=r("el-main"),k=r("el-container"),w=r("el-drawer"),N=R("drag");return o(),d($,null,[S((o(),d("div",{ref:"",class:"mobile-nav-button",onClick:t[0]||(t[0]=x=>n.showMobileNav(x)),draggable:"false"},[s(g,null,{default:i(()=>[s(_)]),_:1})])),[[N]]),s(w,{ref:"mobileNavBox",title:"移动端菜单",size:240,modelValue:l.nav,"onUpdate:modelValue":t[1]||(t[1]=x=>l.nav=x),direction:"ltr","with-header":!1,"destroy-on-close":""},{default:i(()=>[s(k,{class:"mobile-nav"},{default:i(()=>[s(b,null,{default:i(()=>[a("div",de,[a("img",{class:"logo",src:n.getImg("logo.png")},null,8,me),a("span",null,T(e.$CONFIG.APP_NAME),1)])]),_:1}),s(C,null,{default:i(()=>[s(h,null,{default:i(()=>[s(f,{"default-active":e.$route.meta.active||e.$route.fullPath,onSelect:n.select,router:"","background-color":"#212d3d","text-color":"#fff","active-text-color":"#409EFF"},{default:i(()=>[s(p,{navMenus:l.menu},null,8,["navMenus"])]),_:1},8,["default-active","onSelect"])]),_:1})]),_:1})]),_:1})]),_:1},8,["modelValue"])],64)}const he=M(ce,[["render",_e],["__scopeId","data-v-a1b9a25e"],["__file","Z:/workspace/vue-support-parent-starter/vue-support-scheduler-starter/src/layout/components/sideM.vue"]]);const pe={data(){return{breadList:[]}},created(){this.getBreadcrumb()},watch:{$route(){this.getBreadcrumb()}},methods:{getBreadcrumb(){let e=this.$route.meta.breadcrumb;this.breadList=e}}},fe=e=>(Q("data-v-b6acff93"),e=e(),J(),e),ve={class:"adminui-topbar"},ge={class:"left-panel"},ye=fe(()=>a("div",{class:"center-panel"},null,-1)),be={class:"right-panel"};function ke(e,t,u,v,l,n){const _=r("el-icon"),g=r("el-breadcrumb-item"),b=r("el-breadcrumb");return o(),d("div",ve,[a("div",ge,[s(b,{"separator-icon":"el-icon-arrow-right",class:"hidden-sm-and-down"},{default:i(()=>[s(X,{name:"breadcrumb"},{default:i(()=>[(o(!0),d($,null,V(l.breadList,p=>(o(),d($,{key:p.title},[p.path!="/"&&!p.meta.hiddenBreadcrumb?(o(),c(g,{key:p.meta.title},{default:i(()=>[p.meta.icon?(o(),c(_,{key:0,class:"icon"},{default:i(()=>[(o(),c(E(p.meta.icon)))]),_:2},1024)):m("v-if",!0),I(T(p.meta.title),1)]),_:2},1024)):m("v-if",!0)],64))),128))]),_:1})]),_:1})]),ye,a("div",be,[j(e.$slots,"default",{},void 0,!0)])])}const we=M(pe,[["render",ke],["__scopeId","data-v-b6acff93"],["__file","Z:/workspace/vue-support-parent-starter/vue-support-scheduler-starter/src/layout/components/topbar.vue"]]);const Te={name:"tags",data(){return{contextMenuVisible:!1,contextMenuItem:null,left:0,top:0,tagList:this.$store.state.viewTags.viewTags,tipDisplayed:!1}},props:{},watch:{$route(e){this.addViewTags(e),this.$nextTick(()=>{const t=this.$refs.tags;t&&t.scrollWidth>t.clientWidth&&(t.querySelector(".active").scrollIntoView(),this.tipDisplayed||(this.$msgbox({type:"warning",center:!0,title:"提示",message:"当前标签数量过多，可通过鼠标滚轴滚动标签栏。关闭标签数量可减少系统性能消耗。",confirmButtonText:"知道了"}),this.tipDisplayed=!0))})},contextMenuVisible(e){const t=u=>{const v=document.getElementById("contextmenu");v&&!v.contains(u.target)&&this.closeMenu()};e?document.body.addEventListener("click",u=>t(u)):document.body.removeEventListener("click",u=>t(u))}},created(){var e=this.$router.sc_getMenu(),t=this.treeFind(e,u=>u.path==this.$CONFIG.DASHBOARD_URL);t&&(t.fullPath=t.path,this.addViewTags(t),this.addViewTags(this.$route))},mounted(){this.tagDrop(),this.scrollInit()},methods:{treeFind(e,t){for(const u of e){if(t(u))return u;if(u.children){const v=this.treeFind(u.children,t);if(v)return v}}return null},tagDrop(){const e=this.$refs.tags;ee.create(e,{draggable:"li",animation:300})},addViewTags(e){e.name&&!e.meta.fullpage&&(this.$store.commit("pushViewTags",e),this.$store.commit("pushKeepLive",e.name))},isActive(e){return e.fullPath===this.$route.fullPath},closeSelectedTag(e,t=!0){const u=this.tagList.findIndex(v=>v.fullPath==e.fullPath);if(this.$store.commit("removeViewTags",e),this.$store.commit("removeIframeList",e),this.$store.commit("removeKeepLive",e.name),t&&this.isActive(e)){const v=this.tagList[u-1];v?this.$router.push(v):this.$router.push("/")}},openContextMenu(e,t){this.contextMenuItem=t,this.contextMenuVisible=!0,this.left=e.clientX+1,this.top=e.clientY+1,this.$nextTick(()=>{let u=document.getElementById("contextmenu");document.body.offsetWidth-e.clientX<u.offsetWidth&&(this.left=document.body.offsetWidth-u.offsetWidth+1,this.top=e.clientY+1)})},closeMenu(){this.contextMenuItem=null,this.contextMenuVisible=!1},refreshTab(){this.contextMenuVisible=!1;const e=this.contextMenuItem;this.$route.fullPath!==e.fullPath&&this.$router.push({path:e.fullPath,query:e.query}),this.$store.commit("refreshIframe",e),setTimeout(()=>{this.$store.commit("removeKeepLive",e.name),this.$store.commit("setRouteShow",!1),this.$nextTick(()=>{this.$store.commit("pushKeepLive",e.name),this.$store.commit("setRouteShow",!0)})},0)},closeTabs(){var e=this.contextMenuItem;e.meta.affix||(this.closeSelectedTag(e),this.contextMenuVisible=!1)},closeOtherTabs(){var e=this.contextMenuItem;this.$route.fullPath!=e.fullPath&&this.$router.push({path:e.fullPath,query:e.query});var t=[...this.tagList];t.forEach(u=>{if(u.meta&&u.meta.affix||e.fullPath==u.fullPath)return!0;this.closeSelectedTag(u,!1)}),this.contextMenuVisible=!1},maximize(){var e=this.contextMenuItem;this.contextMenuVisible=!1,this.$route.fullPath!=e.fullPath&&this.$router.push({path:e.fullPath,query:e.query}),document.getElementById("app").classList.add("main-maximize")},openWindow(){var e=this.contextMenuItem,t=e.href||"/";e.meta.affix||this.closeSelectedTag(e),window.open(t),this.contextMenuVisible=!1},scrollInit(){const e=this.$refs.tags;e.addEventListener("mousewheel",t,!1)||e.addEventListener("DOMMouseScroll",t,!1);function t(u){const v=u.wheelDelta||u.detail,l=1,n=-1;let _=0;v==3||v<0&&v!=-3?_=l*50:_=n*50,e.scrollLeft+=_}}}},$e={class:"adminui-tags"},Ce={ref:"tags"},xe=["onContextmenu"],Ie=a("hr",null,null,-1),Oe=a("hr",null,null,-1);function Le(e,t,u,v,l,n){const _=r("el-icon-close"),g=r("el-icon"),b=r("router-link"),p=r("el-icon-refresh"),f=r("el-icon-folder-delete"),h=r("el-icon-full-screen"),C=r("el-icon-copy-document");return o(),d($,null,[a("div",$e,[a("ul",Ce,[(o(!0),d($,null,V(l.tagList,k=>(o(),d("li",{key:k,class:A([n.isActive(k)?"active":"",k.meta.affix?"affix":""]),onContextmenu:F(w=>n.openContextMenu(w,k),["prevent"])},[s(b,{to:k},{default:i(()=>[a("span",null,T(k.meta.title),1),k.meta.affix?m("v-if",!0):(o(),c(g,{key:0,onClick:F(w=>n.closeSelectedTag(k),["prevent","stop"])},{default:i(()=>[s(_)]),_:2},1032,["onClick"]))]),_:2},1032,["to"])],42,xe))),128))],512)]),s(se,{name:"el-zoom-in-top"},{default:i(()=>[l.contextMenuVisible?(o(),d("ul",{key:0,style:te({left:l.left+"px",top:l.top+"px"}),class:"contextmenu",id:"contextmenu"},[a("li",{onClick:t[0]||(t[0]=k=>n.refreshTab())},[s(g,null,{default:i(()=>[s(p)]),_:1}),I("刷新")]),Ie,a("li",{onClick:t[1]||(t[1]=k=>n.closeTabs()),class:A(l.contextMenuItem.meta.affix?"disabled":"")},[s(g,null,{default:i(()=>[s(_)]),_:1}),I("关闭标签")],2),a("li",{onClick:t[2]||(t[2]=k=>n.closeOtherTabs())},[s(g,null,{default:i(()=>[s(f)]),_:1}),I("关闭其他标签")]),Oe,a("li",{onClick:t[3]||(t[3]=k=>n.maximize())},[s(g,null,{default:i(()=>[s(h)]),_:1}),I("最大化")]),a("li",{onClick:t[4]||(t[4]=k=>n.openWindow())},[s(g,null,{default:i(()=>[s(C)]),_:1}),I("在新的窗口中打开")])],4)):m("v-if",!0)]),_:1})],64)}const Ee=M(Te,[["render",Le],["__file","Z:/workspace/vue-support-parent-starter/vue-support-scheduler-starter/src/layout/components/tags.vue"]]);const Ne={data(){return{input:"",menu:[],result:[],history:[]}},mounted(){var e=this.$TOOL.data.get("SEARCH_HISTORY")||[];this.history=e;var t=this.$TOOL.data.get(L.MENU);this.filterMenu(t),this.$refs.input.focus()},methods:{inputChange(e){e?this.result=this.menuFilter(e):this.result=[]},filterMenu(e){e.forEach(t=>{if(t.meta.hidden||t.meta.type=="button")return!1;t.meta.type=="iframe"&&(t.path=`/i/${t.name}`),t.children&&t.children.length>0&&!t.component?this.filterMenu(t.children):this.menu.push(t)})},menuFilter(e){var t=[],u=this.menu.filter(n=>{if(n.meta.title.toLowerCase().indexOf(e.toLowerCase())>=0||n.name.toLowerCase().indexOf(e.toLowerCase())>=0)return!0}),v=this.$router.getRoutes(),l=u.map(n=>n.meta.type=="link"?v.find(_=>_.path=="/"+n.path):v.find(_=>_.path==n.path));return l.forEach(n=>{t.push({name:n.name,type:n.meta.type,path:n.meta.type=="link"?n.path.slice(1):n.path,icon:n.meta.icon,title:n.meta.title,breadcrumb:n.meta.breadcrumb.map(_=>_.meta.title).join(" - ")})}),t},to(e){this.history.includes(this.input)||(this.history.push(this.input),this.$TOOL.data.set("SEARCH_HISTORY",this.history)),e.type=="link"?setTimeout(()=>{let t=document.createElement("a");t.style="display: none",t.target="_blank",t.href=e.path,document.body.appendChild(t),t.click(),document.body.removeChild(t)},10):this.$router.push({path:e.path}),this.$emit("success",!0)},historyClick(e){this.input=e,this.inputChange(e)},historyClose(e){this.history.splice(e,1),this.history.length<=0?this.$TOOL.data.remove("SEARCH_HISTORY"):this.$TOOL.data.set("SEARCH_HISTORY",this.history)}}},Me={class:"sc-search"},Ve={key:0,class:"sc-search-history"},Ae={class:"sc-search-result"},Pe={key:0,class:"sc-search-no-result"},Se={key:1},De=["onClick"],Fe={class:"title"};function Re(e,t,u,v,l,n){const _=r("el-input"),g=r("el-tag"),b=r("el-icon"),p=r("el-scrollbar");return o(),d("div",Me,[s(_,{ref:"input",modelValue:l.input,"onUpdate:modelValue":t[0]||(t[0]=f=>l.input=f),placeholder:"搜索",size:"large",clearable:"","prefix-icon":"el-icon-search","trigger-on-focus":!1,onInput:n.inputChange},null,8,["modelValue","onInput"]),l.history.length>0?(o(),d("div",Ve,[(o(!0),d($,null,V(l.history,(f,h)=>(o(),c(g,{closable:"",effect:"dark",type:"info",key:f,onClick:C=>n.historyClick(f),onClose:C=>n.historyClose(h)},{default:i(()=>[I(T(f),1)]),_:2},1032,["onClick","onClose"]))),128))])):m("v-if",!0),a("div",Ae,[l.result.length<=0?(o(),d("div",Pe,T(e.$t("data.nodata")),1)):(o(),d("ul",Se,[s(p,{"max-height":"366px"},{default:i(()=>[(o(!0),d($,null,V(l.result,f=>(o(),d("li",{key:f.path,onClick:h=>n.to(f)},[s(b,null,{default:i(()=>[(o(),c(E(f.icon||"el-icon-menu")))]),_:2},1024),a("span",Fe,T(f.breadcrumb),1)],8,De))),128))]),_:1})]))])])}const Ue=M(Ne,[["render",Re],["__scopeId","data-v-5ee65916"],["__file","Z:/workspace/vue-support-parent-starter/vue-support-scheduler-starter/src/layout/components/search.vue"]]);const Ge={data(){return{loading:!1,tasks:[]}},mounted(){this.getData()},methods:{async getData(){this.loading=!0;var e=await this.$API.system.tasks.page.get().finally(()=>{this.loading=!1});this.tasks=e.data.data,this.loading=!1},refresh(){this.getData()},download(e){let t=document.createElement("a");t.style="display: none",t.target="_blank",t.href=this.$API.common.ossPrefix.url+e.taskFinishFile+"?mode=DOWNLOAD",document.body.appendChild(t),t.click(),document.body.removeChild(t)}}},ze={style:{"font-size":"14px",color:"#999","line-height":"1.5",margin:"0 40px"}},Be={class:"user-bar-tasks-item-body"},qe={class:"taskIcon"},He={class:"taskMain"},We={class:"title"},Ze={class:"bottom"},Ye={class:"state"},Ke={class:"handler"};function Xe(e,t,u,v,l,n){const _=r("el-empty"),g=r("el-icon-paperclip"),b=r("el-icon"),p=r("el-icon-dataAnalysis"),f=r("el-tag"),h=r("el-button"),C=r("el-card"),k=r("el-main"),w=r("el-footer"),N=r("el-container"),x=R("time"),P=R("loading");return S((o(),c(N,null,{default:i(()=>[s(k,null,{default:i(()=>[l.tasks.length==0?(o(),c(_,{key:0,"image-size":120},{description:i(()=>[a("h2",null,T(e.$t("page.notask")),1)]),default:i(()=>[a("p",ze,T(e.$t("data.nodata")),1)]),_:1})):m("v-if",!0),(o(!0),d($,null,V(l.tasks,O=>(o(),c(C,{key:O.taskId,shadow:"hover",class:"user-bar-tasks-item"},{default:i(()=>[a("div",Be,[a("div",qe,[O.taskNoticeType=="export"?(o(),c(b,{key:0,size:20},{default:i(()=>[s(g)]),_:1})):m("v-if",!0),O.taskNoticeType=="report"?(o(),c(b,{key:1,size:20},{default:i(()=>[s(p)]),_:1})):m("v-if",!0)]),a("div",He,[a("div",We,[a("h2",null,T(O.taskName),1),a("p",null,[S(a("span",null,null,512),[[x,O.createTime,void 0,{tip:!0}]]),I(" 创建")])]),a("div",Ze,[a("div",Ye,[O.taskStatus==3?(o(),c(f,{key:0,type:"info"},{default:i(()=>[I("正在运行")]),_:1})):m("v-if",!0),O.taskStatus==2?(o(),c(f,{key:1,type:"info"},{default:i(()=>[I("已暂停")]),_:1})):m("v-if",!0),O.taskStatus==0?(o(),c(f,{key:2,type:"info"},{default:i(()=>[I("未开始")]),_:1})):m("v-if",!0),O.taskStatus=="1"?(o(),c(f,{key:3},{default:i(()=>[I("已完成")]),_:1})):m("v-if",!0)]),a("div",Ke,[O.taskStatus=="1"&&O.taskFinishFile?(o(),c(h,{key:0,type:"primary",circle:"",icon:"el-icon-download",onClick:U=>n.download(O)},null,8,["onClick"])):m("v-if",!0)])])])])]),_:2},1024))),128))]),_:1}),s(w,{style:{padding:"10px","text-align":"right"}},{default:i(()=>[s(h,{circle:"",icon:"el-icon-refresh",onClick:n.refresh},null,8,["onClick"])]),_:1})]),_:1})),[[P,l.loading]])}const je=M(Ge,[["render",Xe],["__scopeId","data-v-2c676fbe"],["__file","Z:/workspace/vue-support-parent-starter/vue-support-scheduler-starter/src/layout/components/tasks.vue"]]);const Qe={components:{search:Ue,tasks:je},data(){return{userName:"",userNameF:"",searchVisible:!1,tasksVisible:!1,msg:!1,msgList:[]}},created(){var e=this.$TOOL.data.get(L.USER_INFO);this.userName=e.userRealName||e.userName||e.nickname,this.userNameF=this.userName.substring(0,1)},methods:{handleUser(e){e=="uc"&&this.$router.push({path:"/usercenter"}),e=="cmd"&&this.$router.push({path:"/cmd"}),e=="clearCache"&&this.$confirm("清除缓存会将系统初始化，包括登录状态、主题、语言设置等，是否继续？","提示",{type:"info"}).then(()=>{const t=this.$loading();this.$TOOL.data.remove(L.MENU);const u=this.$TOOL.data.get(L.USER_INFO);var v=null;v=this.$API.system.menu.myMenus.get(),v.then(l=>{if(l.code=="00000"){if(l.data.menu.length==0)return this.islogin=!1,this.$alert("当前用户无任何菜单权限，请联系系统管理员","无权限访问",{type:"error",center:!0}),!1;this.$TOOL.data.set(L.MENU,l.data.menu),this.$TOOL.data.set(L.PERMISSIONS,l.data.permissions),(!l.data.dashboardGrid||!l.data.dashboardGrid.length)&&u.roles.indexOf(L.ADMIN)>-1&&(l.data.dashboardGrid=Object.keys(oe)),this.$TOOL.data.set(L.DASHBOARD_GRID,l.data.dashboardGrid),l.data.grid.copmsList?this.$TOOL.data.set(L.GRID,l.data.grid):this.$TOOL.data.remove(L.GRID)}else return this.islogin=!1,this.$message.warning(l.msg),!1}),setTimeout(()=>{t.close()},1e3)}).catch(()=>{}),e=="outLogin"&&this.$confirm("确认是否退出当前用户？","提示",{type:"warning",confirmButtonText:"退出",confirmButtonClass:"el-button--danger"}).then(()=>{this.$router.replace({path:"/login"})}).catch(()=>{})},screen(){var e=document.documentElement;this.$TOOL.screen(e)},showMsg(){this.msg=!0},markRead(){this.msgList=[]},search(){this.searchVisible=!0},tasks(){this.tasksVisible=!0}}},Je={class:"user-bar"},et={class:"user-avatar"};function tt(e,t,u,v,l,n){const _=r("el-avatar"),g=r("el-icon-arrow-down"),b=r("el-icon"),p=r("el-dropdown-item"),f=r("el-dropdown-menu"),h=r("el-dropdown"),C=r("search"),k=r("el-dialog"),w=r("tasks"),N=r("el-drawer");return o(),d($,null,[a("div",Je,[s(h,{class:"user panel-item",trigger:"click",onCommand:n.handleUser},{dropdown:i(()=>[s(f,null,{default:i(()=>[s(p,{command:"outLogin"},{default:i(()=>[I(T(e.$t("page.tcdl")),1)]),_:1})]),_:1})]),default:i(()=>[a("div",et,[s(_,{size:30},{default:i(()=>[I(T(l.userNameF),1)]),_:1}),a("label",null,T(l.userName),1),s(b,{class:"el-icon--right"},{default:i(()=>[s(g)]),_:1})])]),_:1},8,["onCommand"])]),s(k,{modelValue:l.searchVisible,"onUpdate:modelValue":t[1]||(t[1]=x=>l.searchVisible=x),width:700,title:"搜索",center:"","destroy-on-close":""},{default:i(()=>[s(C,{onSuccess:t[0]||(t[0]=x=>l.searchVisible=!1)})]),_:1},8,["modelValue"]),s(N,{modelValue:l.tasksVisible,"onUpdate:modelValue":t[2]||(t[2]=x=>l.tasksVisible=x),size:450,title:"任务中心","destroy-on-close":""},{default:i(()=>[s(w)]),_:1},8,["modelValue"])],64)}const st=M(Qe,[["render",tt],["__scopeId","data-v-3b3ca027"],["__file","Z:/workspace/vue-support-parent-starter/vue-support-scheduler-starter/src/layout/components/userbar.vue"]]),ot={data(){return{layout:this.$store.state.global.layout,menuIsCollapse:this.$store.state.global.menuIsCollapse,layoutTags:this.$store.state.global.layoutTags,lang:this.$TOOL.data.get("APP_LANG")||this.$CONFIG.LANG,dark:this.$TOOL.data.get("APP_DARK")||!1,colorList:["#409EFF","#009688","#536dfe","#ff5c93","#c62f2f","#fd726d"],colorPrimary:this.$TOOL.data.get("APP_COLOR")||this.$CONFIG.COLOR||"#409EFF"}},watch:{layout(e){this.$store.commit("SET_layout",e)},menuIsCollapse(){this.$store.commit("TOGGLE_menuIsCollapse")},layoutTags(){this.$store.commit("TOGGLE_layoutTags")},dark(e){e?(document.documentElement.classList.add("dark"),this.$TOOL.data.set("APP_DARK",e)):(document.documentElement.classList.remove("dark"),this.$TOOL.data.remove("APP_DARK"))},lang(e){this.$i18n.locale=e,this.$TOOL.data.set("APP_LANG",e)},colorPrimary(e){e||(e="#409EFF",this.colorPrimary="#409EFF"),document.documentElement.style.setProperty("--el-color-primary",e);for(let t=1;t<=9;t++)document.documentElement.style.setProperty(`--el-color-primary-light-${t}`,G.lighten(e,t/10));for(let t=1;t<=9;t++)document.documentElement.style.setProperty(`--el-color-primary-dark-${t}`,G.darken(e,t/10));this.$TOOL.data.set("APP_COLOR",e)}}};function nt(e,t,u,v,l,n){const _=r("el-alert"),g=r("el-divider"),b=r("el-switch"),p=r("el-form-item"),f=r("el-option"),h=r("el-select"),C=r("el-color-picker"),k=r("el-form");return o(),c(k,{ref:"form","label-width":"120px","label-position":"left",style:{padding:"0 20px"}},{default:i(()=>[s(_,{title:"以下配置可实时预览，开发者可在 config/index.js 中配置默认值，非常不建议在生产环境下开放布局设置",type:"error",closable:!1}),s(g),s(p,{label:e.$t("user.nightmode")},{default:i(()=>[s(b,{modelValue:l.dark,"onUpdate:modelValue":t[0]||(t[0]=w=>l.dark=w)},null,8,["modelValue"])]),_:1},8,["label"]),s(p,{label:e.$t("user.language")},{default:i(()=>[s(h,{modelValue:l.lang,"onUpdate:modelValue":t[1]||(t[1]=w=>l.lang=w)},{default:i(()=>[s(f,{label:"简体中文",value:"zh-cn"}),s(f,{label:"English",value:"en"})]),_:1},8,["modelValue"])]),_:1},8,["label"]),s(g),s(p,{label:"主题颜色"},{default:i(()=>[s(C,{modelValue:l.colorPrimary,"onUpdate:modelValue":t[2]||(t[2]=w=>l.colorPrimary=w),predefine:l.colorList},{default:i(()=>[I(">")]),_:1},8,["modelValue","predefine"])]),_:1}),s(g),s(p,{label:"框架布局"},{default:i(()=>[s(h,{modelValue:l.layout,"onUpdate:modelValue":t[3]||(t[3]=w=>l.layout=w),placeholder:"请选择"},{default:i(()=>[s(f,{label:"默认",value:"default"}),s(f,{label:"通栏",value:"header"}),s(f,{label:"经典",value:"menu"}),s(f,{label:"功能坞",value:"dock"})]),_:1},8,["modelValue"])]),_:1}),s(p,{label:"折叠菜单"},{default:i(()=>[s(b,{modelValue:l.menuIsCollapse,"onUpdate:modelValue":t[4]||(t[4]=w=>l.menuIsCollapse=w)},null,8,["modelValue"])]),_:1}),s(p,{label:"标签栏"},{default:i(()=>[s(b,{modelValue:l.layoutTags,"onUpdate:modelValue":t[5]||(t[5]=w=>l.layoutTags=w)},null,8,["modelValue"])]),_:1}),s(g)]),_:1},512)}const lt=M(ot,[["render",nt],["__file","Z:/workspace/vue-support-parent-starter/vue-support-scheduler-starter/src/layout/components/setting.vue"]]);const it={data(){return{}},watch:{$route(e){this.push(e)}},created(){this.push(this.$route)},computed:{iframeList(){return this.$store.state.iframe.iframeList},ismobile(){return this.$store.state.global.ismobile},layoutTags(){return this.$store.state.global.layoutTags}},mounted(){},methods:{push(e){e.meta.type=="iframe"?this.ismobile||!this.layoutTags?this.$store.commit("setIframeList",e):this.$store.commit("pushIframeList",e):(this.ismobile||!this.layoutTags)&&this.$store.commit("clearIframeList")}}},at={class:"iframe-pages"},rt=["src"];function ut(e,t,u,v,l,n){return S((o(),d("div",at,[(o(!0),d($,null,V(n.iframeList,_=>S((o(),d("iframe",{key:_.meta.url,src:_.meta.url,frameborder:"0"},null,8,rt)),[[z,e.$route.meta.url==_.meta.url]])),128))],512)),[[z,e.$route.meta.type=="iframe"]])}const ct=M(it,[["render",ut],["__scopeId","data-v-781114dc"],["__file","Z:/workspace/vue-support-parent-starter/vue-support-scheduler-starter/src/layout/components/iframeView.vue"]]),dt={render(){},data(){return{logoutCount:this.$TOOL.data.get("AUTO_EXIT")}},mounted(){this.logoutCount&&(this.setNewAutoExitTime(),document.onclick=()=>{this.setNewAutoExitTime()},document.onmousemove=()=>{this.setNewAutoExitTime()},document.onkeydown=()=>{this.setNewAutoExitTime()},document.onscroll=()=>{this.setNewAutoExitTime()},window.autoExitTimer=window.setInterval(this.autoExitfun,1e3))},unmounted(){this.logoutCount&&(clearInterval(window.autoExitTimer),window.autoExitTimer=null)},methods:{setNewAutoExitTime(){window.autoExitTime=new Date().getTime()},autoExitfun(){new Date().getTime()-window.autoExitTime>this.logoutCount*60*1e3&&(clearInterval(window.autoExitTimer),window.autoExitTimer=null,this.$router.replace({path:"/login"}),this.$alert("用户长时间无操作，为保证账户安全，系统已自动登出。","提示",{type:"warning",center:!0,roundButton:!0}))}}};const mt={name:"index",components:{SideM:he,Topbar:we,Tags:Ee,NavMenu:q,userbar:st,setting:lt,iframeView:ct,autoExit:dt},data(){return{openSettingSet:L.OPEN_SETTING,settingDialog:!1,menu:[],nextMenu:[],pmenu:{},active:""}},computed:{ismobile(){return this.$store.state.global.ismobile},layout(){return this.$store.state.global.layout},layoutTags(){return this.$store.state.global.layoutTags},menuIsCollapse(){return this.$store.state.global.menuIsCollapse}},created(){this.onLayoutResize(),window.addEventListener("resize",this.onLayoutResize);var e=this.$router.sc_getMenu();this.menu=this.filterUrl(e),this.showThis()},watch:{$route(){this.showThis()},layout:{handler(e){document.body.setAttribute("data-layout",e)},immediate:!0}},methods:{getImg(e){return B(e)},openSetting(){this.settingDialog=!0},onLayoutResize(){this.$store.commit("SET_ismobile",document.body.clientWidth<992)},showThis(){this.pmenu=this.$route.meta.breadcrumb?this.$route.meta.breadcrumb[0]:{},this.nextMenu=this.filterUrl(this.pmenu.children),this.$nextTick(()=>{this.active=this.$route.meta.active||this.$route.fullPath})},showMenu(e){this.pmenu=e,this.nextMenu=this.filterUrl(e.children),(!e.children||e.children.length==0)&&e.component&&this.$router.push({path:e.path})},filterUrl(e){var t=[];return e&&e.forEach(u=>{if(u.meta=u.meta?u.meta:{},u.meta.hidden||u.meta.type=="button")return!1;u.meta.type=="iframe"&&(u.path=`/i/${u.name}`),u.children&&u.children.length>0&&(u.children=this.filterUrl(u.children)),t.push(u)}),t},exitMaximize(){document.getElementById("app").classList.remove("main-maximize")}}},_t={class:"adminui-header"},ht={class:"adminui-header-left"},pt={class:"logo-bar"},ft=["src"],vt={key:0,class:"nav"},gt=["onClick"],yt={class:"adminui-header-right"},bt={class:"aminui-wrapper"},kt={key:0,class:"adminui-side-top"},wt={class:"adminui-side-scroll"},Tt={class:"aminui-body el-container"},$t={class:"adminui-main",id:"adminui-main"},Ct={class:"adminui-header"},xt={class:"adminui-header-left"},It={class:"logo-bar"},Ot=["src"],Lt={class:"adminui-header-right"},Et={class:"aminui-wrapper"},Nt={class:"adminui-side-scroll"},Mt={class:"aminui-body el-container"},Vt={class:"adminui-main",id:"adminui-main"},At={class:"adminui-header"},Pt={class:"adminui-header-left"},St={class:"logo-bar"},Dt=["src"],Ft={class:"adminui-header-right"},Rt={key:0,class:"adminui-header-menu"},Ut={class:"aminui-wrapper"},Gt={class:"aminui-body el-container"},zt={class:"adminui-main",id:"adminui-main"},Bt={class:"aminui-wrapper"},qt={key:0,class:"aminui-side-split"},Ht={class:"aminui-side-split-top"},Wt=["title","src"],Zt={class:"adminui-side-split-scroll"},Yt=["onClick"],Kt={key:0,class:"adminui-side-top"},Xt={class:"adminui-side-scroll"},jt={class:"aminui-body el-container"},Qt={class:"adminui-main",id:"adminui-main"};function Jt(e,t,u,v,l,n){const _=r("el-icon"),g=r("userbar"),b=r("NavMenu"),p=r("el-menu"),f=r("el-scrollbar"),h=r("el-icon-expand"),C=r("el-icon-fold"),k=r("Side-m"),w=r("Topbar"),N=r("Tags"),x=r("router-view"),P=r("iframe-view"),O=r("router-link"),U=r("el-icon-close"),H=r("el-icon-brush-filled"),W=r("setting"),Z=r("el-drawer"),Y=r("auto-exit");return o(),d($,null,[m(" 通栏布局 "),n.layout=="header"?(o(),d($,{key:0},[a("header",_t,[a("div",ht,[a("div",pt,[a("img",{class:"logo",src:n.getImg("logo.png")},null,8,ft),a("span",null,T(e.$CONFIG.APP_NAME),1)]),n.ismobile?m("v-if",!0):(o(),d("ul",vt,[(o(!0),d($,null,V(l.menu,y=>(o(),d("li",{key:y,class:A(l.pmenu.path==y.path?"active":""),onClick:K=>n.showMenu(y)},[s(_,null,{default:i(()=>[(o(),c(E(y.meta.icon||"el-icon-menu")))]),_:2},1024),a("span",null,T(y.meta.title),1)],10,gt))),128))]))]),a("div",yt,[s(g)])]),a("section",bt,[!n.ismobile&&l.nextMenu.length>0||!l.pmenu.component?(o(),d("div",{key:0,class:A(n.menuIsCollapse?"aminui-side isCollapse":"aminui-side")},[n.menuIsCollapse?m("v-if",!0):(o(),d("div",kt,[a("h2",null,T(l.pmenu.meta.title),1)])),a("div",wt,[s(f,null,{default:i(()=>[s(p,{"default-active":l.active,router:"",collapse:n.menuIsCollapse,"unique-opened":e.$CONFIG.MENU_UNIQUE_OPENED},{default:i(()=>[s(b,{navMenus:l.nextMenu},null,8,["navMenus"])]),_:1},8,["default-active","collapse","unique-opened"])]),_:1})]),a("div",{class:"adminui-side-bottom",onClick:t[0]||(t[0]=y=>e.$store.commit("TOGGLE_menuIsCollapse"))},[s(_,null,{default:i(()=>[n.menuIsCollapse?(o(),c(h,{key:0})):(o(),c(C,{key:1}))]),_:1})])],2)):m("v-if",!0),n.ismobile?(o(),c(k,{key:1})):m("v-if",!0),a("div",Tt,[n.ismobile?m("v-if",!0):(o(),c(w,{key:0})),!n.ismobile&&n.layoutTags?(o(),c(N,{key:1})):m("v-if",!0),a("div",$t,[s(x,null,{default:i(({Component:y})=>[(o(),c(D,{include:this.$store.state.keepAlive.keepLiveRoute},[e.$store.state.keepAlive.routeShow?(o(),c(E(y),{key:e.$route.fullPath})):m("v-if",!0)],1032,["include"]))]),_:1}),s(P)])])])],64)):n.layout=="menu"?(o(),d($,{key:1},[m(" 经典布局 "),a("header",Ct,[a("div",xt,[a("div",It,[a("img",{class:"logo",src:n.getImg("logo.png")},null,8,Ot),a("span",null,T(e.$CONFIG.APP_NAME),1)])]),a("div",Lt,[s(g)])]),a("section",Et,[n.ismobile?m("v-if",!0):(o(),d("div",{key:0,class:A(n.menuIsCollapse?"aminui-side isCollapse":"aminui-side")},[a("div",Nt,[s(f,null,{default:i(()=>[s(p,{"default-active":l.active,router:"",collapse:n.menuIsCollapse,"unique-opened":e.$CONFIG.MENU_UNIQUE_OPENED},{default:i(()=>[s(b,{navMenus:l.menu},null,8,["navMenus"])]),_:1},8,["default-active","collapse","unique-opened"])]),_:1})]),a("div",{class:"adminui-side-bottom",onClick:t[1]||(t[1]=y=>e.$store.commit("TOGGLE_menuIsCollapse"))},[s(_,null,{default:i(()=>[n.menuIsCollapse?(o(),c(h,{key:0})):(o(),c(C,{key:1}))]),_:1})])],2)),n.ismobile?(o(),c(k,{key:1})):m("v-if",!0),a("div",Mt,[n.ismobile?m("v-if",!0):(o(),c(w,{key:0})),!n.ismobile&&n.layoutTags?(o(),c(N,{key:1})):m("v-if",!0),a("div",Vt,[s(x,null,{default:i(({Component:y})=>[(o(),c(D,{include:this.$store.state.keepAlive.keepLiveRoute},[e.$store.state.keepAlive.routeShow?(o(),c(E(y),{key:e.$route.fullPath})):m("v-if",!0)],1032,["include"]))]),_:1}),s(P)])])])],64)):n.layout=="dock"?(o(),d($,{key:2},[m(" 功能坞布局 "),a("header",At,[a("div",Pt,[a("div",St,[a("img",{class:"logo",src:n.getImg("logo.png")},null,8,Dt),a("span",null,T(e.$CONFIG.APP_NAME),1)])]),a("div",Ft,[n.ismobile?m("v-if",!0):(o(),d("div",Rt,[s(p,{mode:"horizontal","default-active":l.active,router:"","background-color":"#222b45","text-color":"#fff","active-text-color":"var(--el-color-primary)"},{default:i(()=>[s(b,{navMenus:l.menu},null,8,["navMenus"])]),_:1},8,["default-active"])])),n.ismobile?(o(),c(k,{key:1})):m("v-if",!0),s(g)])]),a("section",Ut,[a("div",Gt,[!n.ismobile&&n.layoutTags?(o(),c(N,{key:0})):m("v-if",!0),a("div",zt,[s(x,null,{default:i(({Component:y})=>[(o(),c(D,{include:this.$store.state.keepAlive.keepLiveRoute},[e.$store.state.keepAlive.routeShow?(o(),c(E(y),{key:e.$route.fullPath})):m("v-if",!0)],1032,["include"]))]),_:1}),s(P)])])])],64)):(o(),d($,{key:3},[m(" 默认布局 "),a("section",Bt,[n.ismobile?m("v-if",!0):(o(),d("div",qt,[a("div",Ht,[s(O,{to:e.$CONFIG.DASHBOARD_URL},{default:i(()=>[a("img",{class:"logo",title:e.$CONFIG.APP_NAME,src:n.getImg("logo-r.png")},null,8,Wt)]),_:1},8,["to"])]),a("div",Zt,[s(f,null,{default:i(()=>[a("ul",null,[(o(!0),d($,null,V(l.menu,y=>(o(),d("li",{key:y,class:A(l.pmenu.path==y.path?"active":""),onClick:K=>n.showMenu(y)},[s(_,null,{default:i(()=>[(o(),c(E(y.meta.icon||e.el-e.icon-l.menu)))]),_:2},1024),a("p",null,T(y.meta.title),1)],10,Yt))),128))])]),_:1})])])),!n.ismobile&&l.nextMenu.length>0||!l.pmenu.component?(o(),d("div",{key:1,class:A(n.menuIsCollapse?"aminui-side isCollapse":"aminui-side")},[n.menuIsCollapse?m("v-if",!0):(o(),d("div",Kt,[a("h2",null,T(l.pmenu.meta.title),1)])),a("div",Xt,[s(f,null,{default:i(()=>[s(p,{"default-active":l.active,router:"",collapse:n.menuIsCollapse,"unique-opened":e.$CONFIG.MENU_UNIQUE_OPENED},{default:i(()=>[s(b,{navMenus:l.nextMenu},null,8,["navMenus"])]),_:1},8,["default-active","collapse","unique-opened"])]),_:1})]),a("div",{class:"adminui-side-bottom",onClick:t[2]||(t[2]=y=>e.$store.commit("TOGGLE_menuIsCollapse"))},[s(_,null,{default:i(()=>[n.menuIsCollapse?(o(),c(h,{key:0})):(o(),c(C,{key:1}))]),_:1})])],2)):m("v-if",!0),n.ismobile?(o(),c(k,{key:2})):m("v-if",!0),a("div",jt,[s(w,null,{default:i(()=>[s(g)]),_:1}),!n.ismobile&&n.layoutTags?(o(),c(N,{key:0})):m("v-if",!0),a("div",Qt,[s(x,null,{default:i(({Component:y})=>[(o(),c(D,{include:this.$store.state.keepAlive.keepLiveRoute},[e.$store.state.keepAlive.routeShow?(o(),c(E(y),{key:e.$route.fullPath})):m("v-if",!0)],1032,["include"]))]),_:1}),s(P)])])])],64)),a("div",{class:"main-maximize-exit",onClick:t[3]||(t[3]=(...y)=>n.exitMaximize&&n.exitMaximize(...y))},[s(_,null,{default:i(()=>[s(U)]),_:1})]),l.openSettingSet?(o(),d("div",{key:4,class:"layout-setting",onClick:t[4]||(t[4]=(...y)=>n.openSetting&&n.openSetting(...y))},[s(_,null,{default:i(()=>[s(H)]),_:1})])):m("v-if",!0),s(Z,{title:"布局实时演示",modelValue:l.settingDialog,"onUpdate:modelValue":t[5]||(t[5]=y=>l.settingDialog=y),size:400,"append-to-body":"","destroy-on-close":""},{default:i(()=>[s(W)]),_:1},8,["modelValue"]),s(Y)],64)}const ds=M(mt,[["render",Jt],["__file","Z:/workspace/vue-support-parent-starter/vue-support-scheduler-starter/src/layout/index.vue"]]);export{ds as default};
