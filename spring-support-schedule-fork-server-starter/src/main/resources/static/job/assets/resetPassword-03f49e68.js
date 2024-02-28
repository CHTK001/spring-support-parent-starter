import y from"./commonPage-722e54e7.js";import{_ as k,r,o as a,e as c,w as s,a as t,g as w,h as f,c as C,t as z,b as _}from"./index-bd432aa0.js";import"./Utils-25fe7b8b.js";const x={components:{commonPage:y},data(){return{stepActive:0,form:{user:"",phone:"",yzm:"",newpw:"",newpw2:""},rules:{user:[{required:!0,message:"请输入登录账号"}],phone:[{required:!0,message:"请输入手机号"}],yzm:[{required:!0,message:"请输入短信验证码"}],newpw:[{required:!0,message:"请输入新的密码"}],newpw2:[{required:!0,message:"请再次输入新的密码"},{validator:(i,o,p)=>{o!==this.form.newpw?p(new Error("两次输入密码不一致")):p()}}]},disabled:!1,time:0}},mounted(){},methods:{async getYzm(){var i=await this.$refs.form.validateField("phone").catch(()=>{});if(!i)return!1;this.$message.success("已发送短信至手机号码"),this.disabled=!0,this.time=60;var o=setInterval(()=>{this.time-=1,this.time<1&&(clearInterval(o),this.disabled=!1,this.time=0)},1e3)},async save(){var i=await this.$refs.form.validate().catch(()=>{});if(!i)return!1;this.stepActive=1},backLogin(){this.$router.push({path:"/login"})}}},q=w("div",{class:"el-form-item-msg"},"请输入注册时填写的登录账号",-1),A={class:"yzm"},U={key:0},B=w("div",{class:"el-form-item-msg"},"请输入包含英文、数字的8位以上密码",-1);function N(i,o,p,P,e,u){const h=r("el-step"),v=r("el-steps"),m=r("el-input"),n=r("el-form-item"),d=r("el-button"),g=r("el-form"),V=r("el-result"),b=r("common-page");return a(),c(b,{title:"重置密码"},{default:s(()=>[t(v,{active:e.stepActive,simple:"","finish-status":"success"},{default:s(()=>[t(h,{title:"填写新密码"}),t(h,{title:"完成重置"})]),_:1},8,["active"]),e.stepActive==0?(a(),c(g,{key:0,ref:"form",model:e.form,rules:e.rules,"label-width":120},{default:s(()=>[t(n,{label:"登录账号",prop:"user"},{default:s(()=>[t(m,{modelValue:e.form.user,"onUpdate:modelValue":o[0]||(o[0]=l=>e.form.user=l),placeholder:"请输入登录账号"},null,8,["modelValue"]),q]),_:1}),t(n,{label:"手机号码",prop:"phone"},{default:s(()=>[t(m,{modelValue:e.form.phone,"onUpdate:modelValue":o[1]||(o[1]=l=>e.form.phone=l),placeholder:"请输入手机号码"},null,8,["modelValue"])]),_:1}),t(n,{label:"短信验证码",prop:"yzm"},{default:s(()=>[w("div",A,[t(m,{modelValue:e.form.yzm,"onUpdate:modelValue":o[2]||(o[2]=l=>e.form.yzm=l),placeholder:"请输入6位短信验证码"},null,8,["modelValue"]),t(d,{onClick:u.getYzm,disabled:e.disabled},{default:s(()=>[f("获取验证码"),e.disabled?(a(),C("span",U," ("+z(e.time)+")",1)):_("v-if",!0)]),_:1},8,["onClick","disabled"])])]),_:1}),t(n,{label:"新密码",prop:"newpw"},{default:s(()=>[t(m,{modelValue:e.form.newpw,"onUpdate:modelValue":o[3]||(o[3]=l=>e.form.newpw=l),"show-password":"",placeholder:"请输入新密码"},null,8,["modelValue"]),B]),_:1}),t(n,{label:"确认新密码",prop:"newpw2"},{default:s(()=>[t(m,{modelValue:e.form.newpw2,"onUpdate:modelValue":o[4]||(o[4]=l=>e.form.newpw2=l),"show-password":"",placeholder:"请再一次输入新密码"},null,8,["modelValue"])]),_:1}),t(n,null,{default:s(()=>[t(d,{type:"primary",onClick:u.save},{default:s(()=>[f("提交")]),_:1},8,["onClick"])]),_:1})]),_:1},8,["model","rules"])):_("v-if",!0),e.stepActive==1?(a(),c(V,{key:1,icon:"success",title:"密码重置成功","sub-title":"请牢记自己的新密码,返回登录后使用新密码登录"},{extra:s(()=>[t(d,{type:"primary",onClick:u.backLogin},{default:s(()=>[f("返回登录")]),_:1},8,["onClick"])]),_:1})):_("v-if",!0)]),_:1})}const L=k(x,[["render",N],["__file","F:/workspace/vue-support-parent-starter/vue-support-scheduler-starter/src/views/login/resetPassword.vue"]]);export{L as default};
