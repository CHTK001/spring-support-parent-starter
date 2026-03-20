var L=(e,r,u)=>new Promise((h,i)=>{var c=a=>{try{t(u.next(a))}catch(o){i(o)}},l=a=>{try{t(u.throw(a))}catch(o){i(o)}},t=a=>a.done?h(a.value):Promise.resolve(a.value).then(c,l);t((u=u.apply(e,r)).next())});import{g as q,s as H,a as K,b as Q,t as Z,q as J,_ as g,l as F,c as X,F as Y,K as ee,a4 as te,e as ae,z as re,H as ne,Q as y,aE as ie,T as z}from"./mermaid.core-B0WXDFZp-CLU2JDNN.js";import{p as se}from"./chunk-4BX2VUAB-AWtG75oi-Dgi2Lbrr.js";import{p as le}from"./treemap-75Q7IDZK-BVE2z8EB-CfNXZ-E3.js";import{d as W}from"./arc-DKxWWLDg-Bui-_Pk_.js";import{o as oe}from"./ordinal-DSZU4PqD-DOFoVEQk.js";import"./index-Dr1fsBAF.js";import"./index-DIwWSYtT.js";import"./_baseUniq-CuM2RS0h-B1k_w8KH.js";import"./min-cDCw1MM3-DUWnU5Ju.js";import"./clone-CYku6Kza-CevTyaq1.js";import"./init-ZxktEp_H-Gi6I4Gst.js";function ce(e,r){return r<e?-1:r>e?1:r>=e?0:NaN}function ue(e){return e}function pe(){var e=ue,r=ce,u=null,h=y(0),i=y(z),c=y(0);function l(t){var a,o=(t=ie(t)).length,d,S,x=0,p=new Array(o),s=new Array(o),f=+h.apply(this,arguments),w=Math.min(z,Math.max(-z,i.apply(this,arguments)-f)),v,$=Math.min(Math.abs(w)/o,c.apply(this,arguments)),T=$*(w<0?-1:1),m;for(a=0;a<o;++a)(m=s[p[a]=a]=+e(t[a],a,t))>0&&(x+=m);for(r!=null?p.sort(function(D,C){return r(s[D],s[C])}):u!=null&&p.sort(function(D,C){return u(t[D],t[C])}),a=0,S=x?(w-o*T)/x:0;a<o;++a,f=v)d=p[a],m=s[d],v=f+(m>0?m*S:0)+T,s[d]={data:t[d],index:a,value:m,startAngle:f,endAngle:v,padAngle:$};return s}return l.value=function(t){return arguments.length?(e=typeof t=="function"?t:y(+t),l):e},l.sortValues=function(t){return arguments.length?(r=t,u=null,l):r},l.sort=function(t){return arguments.length?(u=t,r=null,l):u},l.startAngle=function(t){return arguments.length?(h=typeof t=="function"?t:y(+t),l):h},l.endAngle=function(t){return arguments.length?(i=typeof t=="function"?t:y(+t),l):i},l.padAngle=function(t){return arguments.length?(c=typeof t=="function"?t:y(+t),l):c},l}var ge=ne.pie,G={sections:new Map,showData:!1},E=G.sections,N=G.showData,de=structuredClone(ge),fe=g(()=>structuredClone(de),"getConfig"),me=g(()=>{E=new Map,N=G.showData,re()},"clear"),he=g(({label:e,value:r})=>{if(r<0)throw new Error(`"${e}" has invalid value: ${r}. Negative values are not allowed in pie charts. All slice values must be >= 0.`);E.has(e)||(E.set(e,r),F.debug(`added new section: ${e}, with value: ${r}`))},"addSection"),ve=g(()=>E,"getSections"),xe=g(e=>{N=e},"setShowData"),ye=g(()=>N,"getShowData"),_={getConfig:fe,clear:me,setDiagramTitle:J,getDiagramTitle:Z,setAccTitle:Q,getAccTitle:K,setAccDescription:H,getAccDescription:q,addSection:he,getSections:ve,setShowData:xe,getShowData:ye},Se=g((e,r)=>{se(e,r),r.setShowData(e.showData),e.sections.map(r.addSection)},"populateDb"),we={parse:g(e=>L(void 0,null,function*(){const r=yield le("pie",e);F.debug(r),Se(r,_)}),"parse")},Ae=g(e=>`
  .pieCircle{
    stroke: ${e.pieStrokeColor};
    stroke-width : ${e.pieStrokeWidth};
    opacity : ${e.pieOpacity};
  }
  .pieOuterCircle{
    stroke: ${e.pieOuterStrokeColor};
    stroke-width: ${e.pieOuterStrokeWidth};
    fill: none;
  }
  .pieTitleText {
    text-anchor: middle;
    font-size: ${e.pieTitleTextSize};
    fill: ${e.pieTitleTextColor};
    font-family: ${e.fontFamily};
  }
  .slice {
    font-family: ${e.fontFamily};
    fill: ${e.pieSectionTextColor};
    font-size:${e.pieSectionTextSize};
    // fill: white;
  }
  .legend text {
    fill: ${e.pieLegendTextColor};
    font-family: ${e.fontFamily};
    font-size: ${e.pieLegendTextSize};
  }
`,"getStyles"),De=Ae,Ce=g(e=>{const r=[...e.values()].reduce((i,c)=>i+c,0),u=[...e.entries()].map(([i,c])=>({label:i,value:c})).filter(i=>i.value/r*100>=1).sort((i,c)=>c.value-i.value);return pe().value(i=>i.value)(u)},"createPieArcs"),$e=g((e,r,u,h)=>{F.debug(`rendering pie chart
`+e);const i=h.db,c=X(),l=Y(i.getConfig(),c.pie),t=40,a=18,o=4,d=450,S=d,x=ee(r),p=x.append("g");p.attr("transform","translate("+S/2+","+d/2+")");const{themeVariables:s}=c;let[f]=te(s.pieOuterStrokeWidth);f!=null||(f=2);const w=l.textPosition,v=Math.min(S,d)/2-t,$=W().innerRadius(0).outerRadius(v),T=W().innerRadius(v*w).outerRadius(v*w);p.append("circle").attr("cx",0).attr("cy",0).attr("r",v+f/2).attr("class","pieOuterCircle");const m=i.getSections(),D=Ce(m),C=[s.pie1,s.pie2,s.pie3,s.pie4,s.pie5,s.pie6,s.pie7,s.pie8,s.pie9,s.pie10,s.pie11,s.pie12];let b=0;m.forEach(n=>{b+=n});const O=D.filter(n=>(n.data.value/b*100).toFixed(0)!=="0"),M=oe(C);p.selectAll("mySlices").data(O).enter().append("path").attr("d",$).attr("fill",n=>M(n.data.label)).attr("class","pieCircle"),p.selectAll("mySlices").data(O).enter().append("text").text(n=>(n.data.value/b*100).toFixed(0)+"%").attr("transform",n=>"translate("+T.centroid(n)+")").style("text-anchor","middle").attr("class","slice"),p.append("text").text(i.getDiagramTitle()).attr("x",0).attr("y",-400/2).attr("class","pieTitleText");const P=[...m.entries()].map(([n,A])=>({label:n,value:A})),k=p.selectAll(".legend").data(P).enter().append("g").attr("class","legend").attr("transform",(n,A)=>{const I=a+o,V=I*P.length/2,U=12*a,j=A*I-V;return"translate("+U+","+j+")"});k.append("rect").attr("width",a).attr("height",a).style("fill",n=>M(n.label)).style("stroke",n=>M(n.label)),k.append("text").attr("x",a+o).attr("y",a-o).text(n=>i.getShowData()?`${n.label} [${n.value}]`:n.label);const B=Math.max(...k.selectAll("text").nodes().map(n=>{var A;return(A=n==null?void 0:n.getBoundingClientRect().width)!=null?A:0})),R=S+t+a+o+B;x.attr("viewBox",`0 0 ${R} ${d}`),ae(x,d,R,l.useMaxWidth)},"draw"),Te={draw:$e},Le={parser:we,db:_,renderer:Te,styles:De};export{Le as diagram};
