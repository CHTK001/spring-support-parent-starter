import{_ as b,E as A,D as g,r as p,o as k,e as x,w as h,a as n,g as y,h as C,t as _,v as S}from"./index-ca136035.js";import"./index-b232d479.js";import"./about-fa6e508e.js";import"./echarts-0463d91d.js";import"./index-057c3f23.js";import"./progress-13f5ad20.js";import"./space-78b6ae5d.js";import"./time-51f29298.js";import"./ver-298f8898.js";import"./welcome-a523930c.js";import"./Utils-25fe7b8b.js";const u="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";class O{encode(e){if(!e)return"";var r="",t,o,l,s,d,m,c,i=0;for(e=this._utf8_encode(e);i<e.length;)t=e.charCodeAt(i++),o=e.charCodeAt(i++),l=e.charCodeAt(i++),s=t>>2,d=(t&3)<<4|o>>4,m=(o&15)<<2|l>>6,c=l&63,isNaN(o)?m=c=64:isNaN(l)&&(c=64),r=r+u.charAt(s)+u.charAt(d)+u.charAt(m)+u.charAt(c);return r}decode(e){if(!e)return"";var r="",t,o,l,s,d,m,c,i=0;for(e=e.replace(/[^A-Za-z0-9\+\/\=]/g,"");i<e.length;)s=u.indexOf(e.charAt(i++)),d=u.indexOf(e.charAt(i++)),m=u.indexOf(e.charAt(i++)),c=u.indexOf(e.charAt(i++)),t=s<<2|d>>4,o=(d&15)<<4|m>>2,l=(m&3)<<6|c,r=r+String.fromCharCode(t),m!=64&&(r=r+String.fromCharCode(o)),c!=64&&(r=r+String.fromCharCode(l));return r=this._utf8_decode(r),r}_utf8_encode(e){e=e.replace(/\r\n/g,`
`);for(var r="",t=0;t<e.length;t++){var o=e.charCodeAt(t);o<128?r+=String.fromCharCode(o):o>127&&o<2048?(r+=String.fromCharCode(o>>6|192),r+=String.fromCharCode(o&63|128)):(r+=String.fromCharCode(o>>12|224),r+=String.fromCharCode(o>>6&63|128),r+=String.fromCharCode(o&63|128))}return r}_utf8_decode(e){var r="",t=0;let o=0,l=0,s=0;for(;t<e.length;)o=e.charCodeAt(t),o<128?(r+=String.fromCharCode(o),t++):o>191&&o<224?(s=e.charCodeAt(t+1),r+=String.fromCharCode((o&31)<<6|s&63),t+=2):(s=e.charCodeAt(t+1),l=e.charCodeAt(t+2),r+=String.fromCharCode((o&15)<<12|(s&63)<<6|l&63),t+=3);return r}}const V=new O,N=[{path:"/home",component:"home/index",name:"首页",hidden:!1,meta:{title:"首页",icon:"el-icon-eleme-filled",hidden:null,tag:null,affix:!1,type:"menu",color:null,roles:[],keepAlive:null,params:null},children:[{path:"/dashboard",component:"home",name:"控制台",hidden:!1,meta:{title:"控制台",icon:"el-icon-menu",hidden:null,tag:null,affix:!1,type:"menu",color:null,roles:[],keepAlive:null,params:null}}]},{path:"/scheduler",component:"scheduler/index",name:"调度管理",hidden:!1,meta:{title:"调度管理",icon:"sc-icon-scheduler",hidden:null,affix:!1,type:"menu",color:null,roles:[],keepAlive:null,params:null},children:[{path:"/scheduler/jobgroup/summary",component:"scheduler/jobgroup/summary",name:"概要",hidden:!1,meta:{title:"概要",icon:"el-icon-menu",affix:!1,type:"menu"}},{path:"/scheduler/jobgroup",component:"scheduler/jobgroup/index",name:"执行器管理",hidden:!1,meta:{title:"执行器管理",icon:"el-icon-takeaway-box",affix:!1,type:"menu"}},{path:"/scheduler/jobinfo",component:"scheduler/jobinfo/index",name:"调度任务管理",hidden:!1,meta:{title:"调度任务管理",icon:"el-icon-alarm-clock",affix:!1,type:"menu"}},{path:"/scheduler/joblog/cat/:logId",component:"scheduler/joblog/cat",name:"日志详情",hidden:!0,meta:{title:"日志详情",icon:"el-icon-alarm-clock",affix:!1,type:"menu"}},{path:"/scheduler/joblog/:jobGroup/:jobId",component:"scheduler/joblog/index",name:"调度日志",hidden:!1,meta:{title:"调度日志",icon:"el-icon-warning",affix:!1,type:"menu"}}]}];const j={data(){return{userType:"admin",form:{user:"",password:"",autologin:!1,verifyCode:void 0},rules:{user:[{required:!0,message:this.$t("login.userError"),trigger:"blur"}],password:[{required:!0,message:this.$t("login.PWError"),trigger:"blur"}]},captchaBase64Key:void 0,captchaBase64:void 0,islogin:!1}},watch:{userType(a){a=="admin"?(this.form.user="",this.form.password=""):a=="user"&&(this.form.user="",this.form.password="")}},mounted(){this.getCaptcha()},methods:{getCaptcha(){this.$API.auth.captcha.get().then(({data:a})=>{const{verifyCodeBase64:e,verifyCodeKey:r}=a;this.captchaBase64=e,this.captchaBase64Key=r})},async login(){if(V.decode(this.captchaBase64Key)!=this.form.verifyCode)return A({type:"error",message:"校验码不正确"}),this.getCaptcha(),!1;var a=await this.$refs.loginForm.validate().catch(()=>{});if(!a)return!1;this.islogin=!0;var e={username:this.form.user,password:this.$TOOL.crypto.MD5(this.form.password),verifyCode:this.form.verifyCode};try{var r=await this.$API.auth.token.post(e)}catch(t){this.getCaptcha(),console.log(t),this.islogin=!1;return}if(this.getCaptcha(),r.code==="00000")this.$TOOL.cookie.set(g.TOKEN,r.data.accessToken,{expires:this.form.autologin?24*60*60:0}),this.$TOOL.data.set(g.USER_INFO,r.data.userInfo);else return this.islogin=!1,this.$message.warning(r.msg),!1;this.$TOOL.data.set(g.MENU,N),this.$TOOL.data.set(g.PERMISSIONS,[]),this.$router.replace({path:"/"}),this.$message.success("Login Success 登录成功"),this.islogin=!1}}},B={span:6,class:"captcha"},I=["src"],T={class:"login-reg"};function E(a,e,r,t,o,l){const s=p("el-input"),d=p("el-form-item"),m=p("el-checkbox"),c=p("el-col"),i=p("router-link"),v=p("el-button"),w=p("el-form");return k(),x(w,{ref:"loginForm",model:o.form,rules:o.rules,"label-width":"0",size:"large",onKeyup:S(l.login,["enter"])},{default:h(()=>[n(d,{prop:"user"},{default:h(()=>[n(s,{modelValue:o.form.user,"onUpdate:modelValue":e[0]||(e[0]=f=>o.form.user=f),"prefix-icon":"el-icon-user",clearable:"",placeholder:a.$t("login.userPlaceholder")},null,8,["modelValue","placeholder"])]),_:1}),n(d,{prop:"password"},{default:h(()=>[n(s,{modelValue:o.form.password,"onUpdate:modelValue":e[1]||(e[1]=f=>o.form.password=f),"prefix-icon":"el-icon-lock",clearable:"","show-password":"",placeholder:a.$t("login.PWPlaceholder")},null,8,["modelValue","placeholder"])]),_:1}),n(d,{prop:"verifyCode"},{default:h(()=>[n(s,{span:6,modelValue:o.form.verifyCode,"onUpdate:modelValue":e[2]||(e[2]=f=>o.form.verifyCode=f),class:"verifyCode",clearable:"",placeholder:a.$t("login.verifyCode")},null,8,["modelValue","placeholder"]),y("div",B,[y("img",{src:o.captchaBase64,onClick:e[3]||(e[3]=(...f)=>l.getCaptcha&&l.getCaptcha(...f))},null,8,I)])]),_:1}),n(d,{style:{"margin-bottom":"10px"}},{default:h(()=>[n(c,{span:12},{default:h(()=>[n(m,{label:a.$t("login.rememberMe"),modelValue:o.form.autologin,"onUpdate:modelValue":e[4]||(e[4]=f=>o.form.autologin=f)},null,8,["label","modelValue"])]),_:1}),n(c,{span:12,class:"login-forgot"},{default:h(()=>[n(i,{to:"/reset_password"},{default:h(()=>[C(_(a.$t("login.forgetPassword"))+"？",1)]),_:1})]),_:1})]),_:1}),n(d,null,{default:h(()=>[n(v,{type:"primary",style:{width:"100%"},loading:o.islogin,round:"",onClick:l.login},{default:h(()=>[C(_(a.$t("login.signIn")),1)]),_:1},8,["loading","onClick"])]),_:1}),y("div",T,[C(_(a.$t("login.noAccount"))+" ",1),n(i,{to:"/user_register"},{default:h(()=>[C(_(a.$t("login.createAccount")),1)]),_:1})])]),_:1},8,["model","rules","onKeyup"])}const R=b(j,[["render",E],["__scopeId","data-v-b1b4f708"],["__file","Z:/workspace/vue-support-parent-starter/vue-support-scheduler-starter/src/views/login/components/passwordForm.vue"]]);export{R as default};
