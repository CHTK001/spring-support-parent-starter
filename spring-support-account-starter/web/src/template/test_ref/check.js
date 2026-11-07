const fs=require('fs');
const root='D:/ch/project/vue-support-parent-starter';
const src=fs.readFileSync(root+'/packages/ui/src/components/ReTable/ReTable.vue','utf8');
const i=src.indexOf('const props =');
if(i<0){const j=src.indexOf('props:');console.log(src.slice(j,j+3000));}
else console.log(src.slice(i,i+2500));
