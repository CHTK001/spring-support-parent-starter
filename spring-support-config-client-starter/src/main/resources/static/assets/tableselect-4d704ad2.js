import{_ as _export_sfc,r as resolveComponent,o as openBlock,g as createBlock,w as withCtx,c as createElementBlock,F as Fragment,f as renderList}from"./index-56cdd313.js";const _sfc_main={name:"uploadRender",props:{modelValue:[String,Number,Boolean,Date,Object,Array],item:{type:Object,default:()=>{}}},data(){return{value:this.modelValue,apiObj:this.getApiObj()}},watch:{value(l){this.$emit("update:modelValue",l)}},mounted(){},methods:{getApiObj(){return eval("this."+this.item.options.apiObj)}}};function _sfc_render(l,a,t,p,o,c){const n=resolveComponent("el-table-column"),r=resolveComponent("sc-table-select");return openBlock(),createBlock(r,{modelValue:o.value,"onUpdate:modelValue":a[0]||(a[0]=e=>o.value=e),apiObj:o.apiObj,"table-width":600,multiple:t.item.options.multiple,props:t.item.options.props,style:{width:"100%"}},{default:withCtx(()=>[(openBlock(!0),createElementBlock(Fragment,null,renderList(t.item.options.column,(e,s)=>(openBlock(),createBlock(n,{key:s,prop:e.prop,label:e.label,width:e.width},null,8,["prop","label","width"]))),128))]),_:1},8,["modelValue","apiObj","multiple","props"])}const tableselect=_export_sfc(_sfc_main,[["render",_sfc_render],["__file","Z:/workspace/vue-support-parent-starter/vue-support-config-starter/src/components/scForm/items/tableselect.vue"]]);export{tableselect as default};
