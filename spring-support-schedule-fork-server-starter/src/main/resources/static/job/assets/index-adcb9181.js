import{_ as P,r,y as R,o as p,c as x,a as e,w as l,b as C,g as b,e as c,h as d,t as A,z,F as L,i as B}from"./index-452c6e27.js";const D={name:"JobGroup",data(){return{loading:!1,showOnlineAddress:!1,form:{appname:""},rules:{appname:[{trigger:"blur",required:!0,message:"名称不能为空"},{min:4,max:64,message:"名称长度在3到10个字符",trigger:"change"}],title:[{trigger:"blur",required:!0,message:"描述不能为空"}],addressList:[{trigger:"blur",required:!0,message:"注册地址不能为空"}]},forms:{addressType:0},jobName:"全部",jobGroupName:"全部",data:[],onlineAddress:[],apiObj:this.$API.scheduler.jobgroupPageList,addShow:!1}},mounted:function(){this.initial()},methods:{showRegistryList(n){this.onlineAddress=n||[],this.showOnlineAddress=!0},del(n){this.$API.scheduler.jobgroupDel.get({id:n.id}).then(o=>{if(o.code==="00000")return this.$message.success("操作成功"),this.data=this.data.filter(f=>f.id!=n.id),!1;this.$message.error(o.msg)})},addSubmit(){this.$refs.addRef.validate(n=>{if(n){var o=void 0;this.forms.id?o=this.$API.scheduler.jobgroupUpdate:o=this.$API.scheduler.jobgroupAdd,this.loading=!0,o.get(this.forms).then(f=>{if(f.code==="00000")return this.$message.success("操作成功"),this.search(),this.addShow=!1,!1;this.$message.error(f.msg)}).finally(()=>this.loading=!1)}})},add(n={}){this.addShow=!0,this.loading=!1,n?Object.assign(this.forms,n):this.forms={}},async initial(){this.search()},search(){this.$refs.table.reload(this.form)}}},I=b("div",{class:"left-panel"},null,-1),q={class:"right-panel"},F={class:"dialog-footer"};function G(n,o,f,E,s,a){const _=r("el-input"),u=r("el-form-item"),y=r("el-form"),i=r("el-button"),j=r("el-header"),m=r("el-table-column"),g=r("el-tag"),T=r("el-button-group"),O=r("scTable"),w=r("el-main"),h=r("el-container"),V=r("el-radio-button"),S=r("el-radio-group"),k=r("el-dialog"),U=r("el-row"),N=R("time");return p(),x(L,null,[e(h,null,{default:l(()=>[C(` <el-aside width="220px">\r
			<el-tree ref="category" class="menu" node-key="label" :data="category" :default-expanded-keys="['系统日志']"\r
				current-node-key="系统日志" :highlight-current="true" :expand-on-click-node="false">\r
			</el-tree>\r
		</el-aside> `),e(h,null,{default:l(()=>[e(w,{class:"nopadding"},{default:l(()=>[e(h,null,{default:l(()=>[e(j,null,{default:l(()=>[I,b("div",q,[e(y,{model:s.form,"label-width":"50px",style:{height:"32px",display:"inline-flex"}},{default:l(()=>[e(u,{label:"名称:"},{default:l(()=>[e(_,{modelValue:s.form.appname,"onUpdate:modelValue":o[0]||(o[0]=t=>s.form.appname=t),placeholder:"名称",clearable:""},null,8,["modelValue"])]),_:1}),e(u,{label:"描述:"},{default:l(()=>[e(_,{modelValue:s.form.title,"onUpdate:modelValue":o[1]||(o[1]=t=>s.form.title=t),placeholder:"应用描述",clearable:""},null,8,["modelValue"])]),_:1})]),_:1},8,["model"]),e(i,{type:"primary",icon:"el-icon-search",onClick:a.search},null,8,["onClick"]),e(i,{type:"primary",icon:"el-icon-plus",onClick:a.add},null,8,["onClick"])])]),_:1}),e(w,{class:"nopadding"},{default:l(()=>[e(O,{ref:"table",loading:s.loading,params:s.form,apiObj:s.apiObj,stripe:"",highlightCurrentRow:""},{default:l(()=>[e(m,{label:"应用名称",prop:"appname",width:"250"}),e(m,{label:"应用描述",prop:"title",width:"150"}),e(m,{label:"注册方式",prop:"addressType","show-overflow-tooltip":""},{default:l(t=>[t.row.addressType==0?(p(),c(g,{key:0},{default:l(()=>[d("自动注册")]),_:1})):(p(),c(g,{key:1},{default:l(()=>[d("手动注册")]),_:1}))]),_:1}),e(m,{label:"OnLine 机器地址",prop:"registryList",width:"150"},{default:l(t=>[e(i,{onClick:v=>a.showRegistryList(t.row.registryList),text:"",type:"primary",size:"small"},{default:l(()=>[d("查看("+A(t.row.registryList?t.row.registryList.length:0)+")",1)]),_:2},1032,["onClick"])]),_:1}),e(m,{label:"更新时间",prop:"updateTime"},{default:l(t=>[z(e(g,null,null,512),[[N,t.row.updateTime]])]),_:1}),e(m,{label:"操作",fixed:"right",align:"right",width:"170"},{default:l(t=>[e(T,null,{default:l(()=>[e(i,{text:"",type:"primary",size:"small",onClick:v=>a.add(t.row)},{default:l(()=>[d("编辑")]),_:2},1032,["onClick"]),e(i,{text:"",type:"primary",size:"small",onClick:v=>a.del(t.row,t.$index)},{default:l(()=>[d("删除")]),_:2},1032,["onClick"])]),_:2},1024)]),_:1})]),_:1},8,["loading","params","apiObj"])]),_:1})]),_:1})]),_:1})]),_:1})]),_:1}),e(k,{title:"添加执行器",modelValue:s.addShow,"onUpdate:modelValue":o[7]||(o[7]=t=>s.addShow=t),onClose:o[8]||(o[8]=t=>n.clearShow=!1)},{footer:l(()=>[b("span",F,[e(i,{loading:s.loading,onClick:o[6]||(o[6]=t=>s.addShow=!1)},{default:l(()=>[d("取消")]),_:1},8,["loading"]),e(i,{loading:s.loading,type:"primary",onClick:a.addSubmit},{default:l(()=>[d("确定")]),_:1},8,["loading","onClick"])])]),default:l(()=>[e(y,{ref:"addRef",rules:s.rules,model:s.forms,"label-width":"120px"},{default:l(()=>[e(u,{label:"执行器名称",prop:"appname"},{default:l(()=>[e(_,{modelValue:s.forms.appname,"onUpdate:modelValue":o[2]||(o[2]=t=>s.forms.appname=t),max:"64",min:"4"},null,8,["modelValue"])]),_:1}),e(u,{label:"描述",prop:"title"},{default:l(()=>[e(_,{modelValue:s.forms.title,"onUpdate:modelValue":o[3]||(o[3]=t=>s.forms.title=t)},null,8,["modelValue"])]),_:1}),e(u,{label:"注册方式",prop:"addressType"},{default:l(()=>[e(S,{modelValue:s.forms.addressType,"onUpdate:modelValue":o[4]||(o[4]=t=>s.forms.addressType=t)},{default:l(()=>[e(V,{label:0},{default:l(()=>[d("自动注册")]),_:1}),e(V,{label:1},{default:l(()=>[d("手动注册")]),_:1})]),_:1},8,["modelValue"])]),_:1}),s.forms.addressType===1?(p(),c(u,{key:0,label:"机器地址",prop:"addressList"},{default:l(()=>[e(_,{type:"textarea",modelValue:s.forms.addressList,"onUpdate:modelValue":o[5]||(o[5]=t=>s.forms.addressList=t)},null,8,["modelValue"])]),_:1})):C("v-if",!0)]),_:1},8,["rules","model"])]),_:1},8,["modelValue"]),e(k,{draggable:"",width:"15%",title:"在线列表",modelValue:s.showOnlineAddress,"onUpdate:modelValue":o[9]||(o[9]=t=>s.showOnlineAddress=t),onClose:o[10]||(o[10]=t=>s.showOnlineAddress=!1)},{default:l(()=>[(p(!0),x(L,null,B(s.onlineAddress,t=>(p(),c(U,null,{default:l(()=>[e(g,null,{default:l(()=>[d(A(t),1)]),_:2},1024)]),_:2},1024))),256))]),_:1},8,["modelValue"])],64)}const Z=P(D,[["render",G],["__file","Z:/workspace/vue-support-parent-starter/vue-support-scheduler-starter/src/views/scheduler/jobgroup/index.vue"]]);export{Z as default};
