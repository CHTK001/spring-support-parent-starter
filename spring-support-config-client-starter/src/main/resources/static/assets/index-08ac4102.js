import{_ as H,m as L,r as i,a as W,o as s,c as v,b as t,w as o,d as k,e as m,t as O,g as u,j as g,h as V,F as y,f as j,n as R,v as Z}from"./index-56cdd313.js";import{s as q}from"./index-4d05358e.js";const G=L(()=>R(()=>import("./index-f65b96e2.js"),["./index-f65b96e2.js","./index-56cdd313.js","./index-a041ed62.css","./index-deaa83c3.css"],import.meta.url)),J={name:"ConfigBean",components:{scSelectFilter:q,scCodeEditor:G},data(){return{statusFilters:[{text:"启用",value:0},{text:"禁用",value:1}],form:{mapMethod:[]},code:"",beanId:void 0,visible:0,isSaveFileing:0,showFile:0,searchParams:{},data:[{title:"环境",key:"beanProfile",multiple:!1,options:[{label:"全部",value:""}]}],row:{},profiles:[],applications:[],list:{apiObj:this.$API.config.bean.page,apiObjUpdate:this.$API.config.bean.update,apiObjSave:this.$API.config.bean.save,apiObjDelete:this.$API.config.bean.delete,apiObjDetail:this.$API.config.bean.detail,apiObjUpdateDetail:this.$API.config.bean.detailUpdate},selection:[]}},mounted(){this.initial()},methods:{look(n){this.list.apiObjDetail.get({configId:n.beanId}).then(e=>{if(e.code==="00000")return this.showFile=!0,this.code=e.data,this.beanId=n.beanId,0;this.$message.error(e.msg)})},submitFormUpdateFile(){this.list.apiObjUpdateDetail.post({configId:this.beanId,content:this.$refs.coder.coder.getValue()}).then(n=>{if(n.code==="00000")return this.showFile=0,this.$message.success("更新成功"),0;this.$message.error(n.msg)})},selectionChange(n){this.selection=n},search(){this.$refs.table.reload(this.searchParams)},table_del(n){this.list.apiObjDelete.delete(n).then(e=>{if(e.code==="00000")return this.$message.success("操作成功"),this.search(),0;this.$message.error(e.msg)})},async batch_del(){this.$confirm(`确定删除选中的 ${this.selection.length} 项吗？如果删除项中含有子集将会被一并删除`,"提示",{type:"warning"}).then(()=>{const n=this.$loading(),e=[];for(const p of this.selection)e.push(p.beanId);this.list.apiObjDelete.delete({beanId:e.join(",")}).then(p=>{if(p.code==="00000")return this.$message.success("操作成功"),this.search(),0}).finally(()=>{n.close()})}).catch(()=>{})},table_edit(n){this.visible=!0,Object.assign(this.row,n),delete this.row.disable},async initial(){const n=await this.$API.config.bean.profile.get();n.code==="00000"&&(this.profiles=n.data,n.data.forEach(p=>{this.data[0].options.push({label:p,value:p})}));const e=await this.$API.config.bean.applications.get();e.code==="00000"&&(this.applications=e.data)},submitFormUpdate(n){this.list.apiObjSave.post(n||this.row).then(e=>{if(e.code==="00000")return this.$message.success("操作成功"),this.search(),this.visible=!1,0;this.$message.error(e.msg)})},filterHandler(n,e,p){const C=p.property;return e[C]===n},change(n){this.searchParams=n,this.$refs.table.reload(n)}}},K={class:"left-panel"},Q=k("br",null,null,-1),X={class:"right-panel"};function Y(n,e,p,C,a,r){const N=i("sc-select-filter"),d=i("el-button"),A=i("el-header"),c=i("el-table-column"),I=i("el-tag"),x=i("el-switch"),D=i("el-popconfirm"),S=i("el-button-group"),B=i("scTable"),E=i("el-main"),M=i("el-container"),h=i("el-input"),b=i("el-form-item"),F=i("el-option"),U=i("el-select"),T=i("el-form"),P=i("el-dialog"),z=i("sc-code-editor"),w=W("auth");return s(),v(y,null,[t(M,null,{default:o(()=>[t(A,null,{default:o(()=>[k("div",K,[t(N,{data:a.data,"selected-values":n.selectedValues,"label-width":80,onOnChange:r.change},null,8,["data","selected-values","onOnChange"]),Q]),k("div",X,[t(d,{type:"primary",icon:"el-icon-search",onClick:r.search},null,8,["onClick"]),t(d,{type:"primary",icon:"el-icon-plus",onClick:e[0]||(e[0]=l=>r.table_edit({}))}),t(d,{type:"danger",plain:"",icon:"el-icon-delete",disabled:a.selection.length==0,onClick:r.batch_del},null,8,["disabled","onClick"])])]),_:1}),t(E,{class:"nopadding"},{default:o(()=>[t(B,{ref:"table",apiObj:a.list.apiObj,"row-key":"id",stripe:"",onSelectionChange:r.selectionChange},{default:o(()=>[t(c,{type:"selection",width:"50"}),t(c,{label:"应用名称",prop:"beanApplicationName",width:"150"}),t(c,{label:"环境",prop:"beanProfile",width:"150"},{default:o(l=>[t(I,null,{default:o(()=>[m(O(l.row.beanProfile),1)]),_:2},1024)]),_:1}),t(c,{label:"Bean名称",prop:"beanName"}),t(c,{label:"脚本","show-overflow-tooltip":""},{default:o(l=>[t(d,{text:"",type:"primary",onClick:f=>r.look(l.row)},{default:o(()=>[m("查看")]),_:2},1032,["onClick"])]),_:1}),t(c,{label:"描述",prop:"beanMarker","show-overflow-tooltip":""}),t(c,{label:"是否禁用",prop:"disable",width:"150",filters:a.statusFilters,"filter-method":r.filterHandler},{default:o(l=>{var f;return[(f=l.row.beanName)!=null&&f.startsWith("bean-")?(s(),u(I,{key:1},{default:o(()=>[m(O(l.row.disable==1?"是":"否"),1)]),_:2},1024)):(s(),u(x,{key:0,onChange:_=>r.submitFormUpdate(l.row),modelValue:l.row.disable,"onUpdate:modelValue":_=>l.row.disable=_,class:"ml-2","active-value":0,"inactive-value":1,style:{"--el-switch-on-color":"#13ce66","--el-switch-off-color":"#ff4949"}},null,8,["onChange","modelValue","onUpdate:modelValue"]))]}),_:1},8,["filters","filter-method"]),t(c,{label:"操作",fixed:"right",align:"right",width:"260"},{default:o(l=>{var f;return[(f=l.row.beanName)!=null&&f.startsWith("bean-")?V("v-if",!0):(s(),u(S,{key:0},{default:o(()=>[g((s(),u(d,{text:"",type:"primary",size:"small",onClick:_=>r.table_edit(l.row,l.$index)},{default:o(()=>[m("编辑")]),_:2},1032,["onClick"])),[[w,"sys:bean:edit"]]),g((s(),u(D,{title:"确定删除吗？",onConfirm:_=>r.table_del(l.row,l.$index)},{reference:o(()=>[g((s(),u(d,{text:"",type:"primary",size:"small"},{default:o(()=>[m("删除")]),_:1})),[[w,"sys:bean:del"]])]),_:2},1032,["onConfirm"])),[[w,"sys:bean:del"]])]),_:2},1024))]}),_:1})]),_:1},8,["apiObj","onSelectionChange"])]),_:1})]),_:1}),t(P,{draggable:"",modelValue:a.visible,"onUpdate:modelValue":e[9]||(e[9]=l=>a.visible=l),width:500,"destroy-on-close":"",onClosed:e[10]||(e[10]=l=>n.$emit("closed"))},{footer:o(()=>[t(d,{onClick:e[7]||(e[7]=l=>a.visible=!1)},{default:o(()=>[m("取 消")]),_:1}),n.mode!="show"?(s(),u(d,{key:0,type:"primary",loading:n.isSaveing,onClick:e[8]||(e[8]=l=>r.submitFormUpdate())},{default:o(()=>[m("保 存")]),_:1},8,["loading"])):V("v-if",!0)]),default:o(()=>[t(T,{model:a.form,disabled:n.mode=="show",ref:"dialogForm","label-width":"100px","label-position":"left"},{default:o(()=>[g(t(b,{label:"索引",prop:"beanName"},{default:o(()=>[t(h,{modelValue:a.row.beanId,"onUpdate:modelValue":e[1]||(e[1]=l=>a.row.beanId=l),clearable:""},null,8,["modelValue"])]),_:1},512),[[Z,!1]]),t(b,{label:"环境",prop:"beanProfile"},{default:o(()=>[t(U,{modelValue:a.row.beanProfile,"onUpdate:modelValue":e[2]||(e[2]=l=>a.row.beanProfile=l)},{default:o(()=>[(s(!0),v(y,null,j(a.profiles,l=>(s(),u(F,{label:l,value:l},null,8,["label","value"]))),256))]),_:1},8,["modelValue"])]),_:1}),a.row.beanId?V("v-if",!0):(s(),u(b,{key:0,label:"应用名称",prop:"beanApplicationName"},{default:o(()=>[t(U,{modelValue:a.row.beanApplicationName,"onUpdate:modelValue":e[3]||(e[3]=l=>a.row.beanApplicationName=l)},{default:o(()=>[(s(!0),v(y,null,j(a.applications,l=>(s(),u(F,{label:l,value:l},null,8,["label","value"]))),256))]),_:1},8,["modelValue"])]),_:1})),t(b,{label:"Bean名称",prop:"beanName"},{default:o(()=>[t(h,{readonly:a.row.beanId,disabled:a.row.beanId,modelValue:a.row.beanName,"onUpdate:modelValue":e[4]||(e[4]=l=>a.row.beanName=l),clearable:""},null,8,["readonly","disabled","modelValue"])]),_:1}),t(b,{label:"配置值",prop:"beanValue"},{default:o(()=>[t(h,{modelValue:a.row.beanValue,"onUpdate:modelValue":e[5]||(e[5]=l=>a.row.beanValue=l),clearable:""},null,8,["modelValue"])]),_:1}),t(b,{label:"描述",prop:"beanMarker"},{default:o(()=>[t(h,{type:"textarea",modelValue:a.row.beanMarker,"onUpdate:modelValue":e[6]||(e[6]=l=>a.row.beanMarker=l),clearable:""},null,8,["modelValue"])]),_:1})]),_:1},8,["model","disabled"])]),_:1},8,["modelValue"]),t(P,{draggable:"",modelValue:a.showFile,"onUpdate:modelValue":e[14]||(e[14]=l=>a.showFile=l)},{footer:o(()=>[t(d,{onClick:e[12]||(e[12]=l=>a.showFile=!1)},{default:o(()=>[m("取 消")]),_:1}),t(d,{type:"primary",loading:a.isSaveFileing,onClick:e[13]||(e[13]=l=>r.submitFormUpdateFile())},{default:o(()=>[m("保 存")]),_:1},8,["loading"])]),default:o(()=>[t(z,{ref:"coder",modelValue:a.code,"onUpdate:modelValue":e[11]||(e[11]=l=>a.code=l),height:700,mode:"groovy"},null,8,["modelValue"])]),_:1},8,["modelValue"])],64)}const le=H(J,[["render",Y],["__file","Z:/workspace/vue-support-parent-starter/vue-support-config-starter/src/views/config/bean/index.vue"]]);export{le as default};
