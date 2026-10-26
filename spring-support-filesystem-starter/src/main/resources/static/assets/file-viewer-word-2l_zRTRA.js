const __vite__mapDeps=(i,m=__vite__mapDeps,d=(m.f||(m.f=["assets/docx-preview-D5kIIrUC.js","assets/rolldown-runtime-C0FnF6B9.js","assets/file-viewer-mindmap-DvZ02N8Z.js","assets/file-viewer-3d-DR-r5IH4.js","assets/file-viewer-drawing-CvUaIOVy.js"])))=>i.map(i=>d[i]);
import{o as e}from"./rolldown-runtime-C0FnF6B9.js";import{$i as t,Bu as n,Du as r,Eu as i,Hs as a,Jc as o,Qi as s,Zi as c,Zo as l,Zu as u,ea as d,gn as f,ho as p,nd as m,ts as h,ud as g,zs as _}from"./file-viewer-3d-DR-r5IH4.js";import{a as v}from"./file-viewer-mindmap-DvZ02N8Z.js";import{C as y,D as b,E as x,O as S,T as C,w as ee}from"./file-viewer-preset-all-BiQ3hrwo.js";var w=e(v(),1),te=`
.odf-viewer{min-height:100%;padding:28px;overflow:auto;background:var(--file-viewer-render-surface-background,#dfe5eb);box-sizing:border-box}
.odf-shell{width:min(100%,980px);margin:0 auto}
.odf-page{min-height:360px;margin:0 auto 18px;padding:42px 48px;border-radius:4px;background:#fff;box-shadow:0 16px 38px rgba(15,23,42,.12);color:#1f2937;box-sizing:border-box}
.odf-page p{margin:0 0 12px;font-size:15px;line-height:1.85;white-space:pre-wrap}
.flyfish-rtf-viewer{min-height:100%;padding:28px;overflow:auto;background:var(--file-viewer-render-surface-background,#dfe5eb);color:#1f2937;box-sizing:border-box}
.flyfish-rtf-paper{width:min(100%,900px);min-height:980px;margin:0 auto;padding:54px 62px;background:#fff;box-shadow:0 16px 38px rgba(15,23,42,.12);line-height:1.75;box-sizing:border-box}.flyfish-rtf-paper p{margin:0 0 12px}
[data-viewer-theme='dark'] .odf-viewer,[data-viewer-theme='dark'] .flyfish-rtf-viewer{color-scheme:dark;background:var(--file-viewer-render-surface-background,#0d1117);color:#e6edf3}
[data-viewer-theme='dark'] .odf-page,[data-viewer-theme='dark'] .flyfish-rtf-paper{border:1px solid rgba(139,148,158,.24);background:#161b22;color:#e6edf3;box-shadow:0 18px 44px rgba(0,0,0,.34)}
@media (prefers-color-scheme:dark){[data-viewer-theme='system'] .odf-viewer,[data-viewer-theme='system'] .flyfish-rtf-viewer{color-scheme:dark;background:var(--file-viewer-render-surface-background,#0d1117);color:#e6edf3}[data-viewer-theme='system'] .odf-page,[data-viewer-theme='system'] .flyfish-rtf-paper{border:1px solid rgba(139,148,158,.24);background:#161b22;color:#e6edf3;box-shadow:0 18px 44px rgba(0,0,0,.34)}}
@media (max-width:720px){.odf-viewer,.flyfish-rtf-viewer{padding:14px}.odf-page{padding:28px 24px}.flyfish-rtf-paper{padding:36px 28px}}
`,T=(e=document)=>{let t=e.createElement(`style`);return t.textContent=te,t},ne=(e,t,n,r)=>{let i=document.createElement(t);return r&&(i.className=r),i.textContent=n,e.appendChild(i),i},E=e=>(e.textContent||``).replace(/\s+/g,` `).trim(),re=async(e,t,n)=>{let r=await(await w.default.loadAsync(e)).file(`content.xml`)?.async(`text`);if(!r)throw Error(n(`word.error.missingOdfContent`));let i=new DOMParser().parseFromString(r,`application/xml`);if(i.querySelector(`parsererror`))throw Error(n(`word.error.odfXmlParseFailed`));return t===`odp`?Array.from(i.getElementsByTagName(`draw:page`)).map(e=>({blocks:Array.from(e.getElementsByTagName(`text:p`)).map(E).filter(Boolean)})):[{blocks:[...Array.from(i.getElementsByTagName(`text:h`)).map(E),...Array.from(i.getElementsByTagName(`text:p`)).map(E)].filter(Boolean)}]},ie=e=>{let t=document.createElement(`div`);t.className=`odf-viewer`;let n=document.createElement(`section`);return n.className=`odf-shell`,e.forEach(e=>{let t=document.createElement(`article`);t.className=`odf-page`,e.blocks.forEach(e=>ne(t,`p`,e)),n.appendChild(t)}),t.appendChild(n),t},ae=async()=>{let e=S();if(!e)throw Error("RTF support is opt-in. Run `npx file-viewer-cli add rtf --write`, then `npx file-viewer-cli install --yes`.");let t=await e();return t.RTFJS||t.default||t},oe=async(e,t,n)=>{var r,i;let a=await ae(),o=t.ownerDocument||document,s=n?.options?.docx?.externalLinkPolicy??`block`,c=n?.options?.docx?.externalResourcePolicy??`block`;(r=a.loggingEnabled)==null||r.call(a,!1);let l=await new a.Document(e,{onHyperlink:(e,t)=>y(o,e,t,s)}).render(),u=o.createElement(`div`);u.className=`flyfish-rtf-viewer`;let d=o.createElement(`article`);return d.className=`flyfish-rtf-paper`,l.forEach(e=>d.append(ee(o,e,{externalLinkPolicy:s,externalResourcePolicy:c}))),u.appendChild(d),t.replaceChildren(T(o),u),(i=n?.registerThumbnailAdapter)==null||i.call(n,{getTarget:()=>d}),{$el:t,unmount(){var e;(e=n?.registerThumbnailAdapter)==null||e.call(n,null),t.replaceChildren()}}};async function se(e,t,n,r){var i;let a=p(r?.options),o=(n||`odt`).toLowerCase();if(o===`rtf`)return oe(e,t,r);let s=await re(e,o,a);return t.replaceChildren(T(),ie(s)),(i=r?.registerThumbnailAdapter)==null||i.call(r,{getTarget:()=>t.querySelector(`.odf-page`)||t}),{$el:t,unmount(){var e;(e=r?.registerThumbnailAdapter)==null||e.call(r,null),t.replaceChildren()}}}var ce=`<span class="msdoc-page-break"></span>`,le=`<p class="msdoc-paragraph"><br></p>`,D={width:794,height:1123},O=.24,k=3,A=.15,ue=`
.msdoc-stage{
  box-sizing:border-box;
  min-height:100%;
  padding:32px 24px 48px;
  background:var(--file-viewer-render-surface-background,#ececec);
  display:flex;
  flex-direction:column;
  align-items:center;
  gap:24px;
}
.msdoc-page{
  width:min(100%,794px);
  box-sizing:border-box;
}
.msdoc-page > .msdoc-root{
  box-sizing:border-box;
  width:100%;
  max-width:none;
  min-height:1123px;
  padding:clamp(24px,7%,96px) clamp(20px,6%,88px);
  background:#fff;
  border:1px solid #d9d9d9;
  box-shadow:0 1px 3px rgba(0,0,0,0.08), 0 12px 32px rgba(0,0,0,0.12);
  overflow-wrap:anywhere;
}
/* 表格不能沿用正文的 anywhere 断词，否则旧 DOC 的列宽和单元格内容会被过度压缩。 */
.msdoc-page .msdoc-table{
  display:table;
  width:auto;
  max-width:100%;
  table-layout:auto!important;
  border-collapse:collapse;
  border-spacing:0;
}
.msdoc-page .msdoc-table tbody,
.msdoc-page .msdoc-table tr{
  width:auto;
}
.msdoc-page .msdoc-cell{
  min-width:1.5em;
  padding:4px 6px;
  vertical-align:top;
  word-break:normal;
  overflow-wrap:normal;
  white-space:normal;
}
.msdoc-page .msdoc-cell .msdoc-paragraph{
  margin:0 0 4px;
  word-break:normal;
  overflow-wrap:break-word;
}
.msdoc-page .msdoc-cell .msdoc-paragraph:last-child{
  margin-bottom:0;
}
.msdoc-page .msdoc-cell span{
  white-space:normal!important;
}
.msdoc-page .msdoc-page-break{
  display:none;
}
[data-viewer-theme='dark'] .msdoc-stage{
  background:var(--file-viewer-render-surface-background,#111827);
}
[data-viewer-theme='dark'] .msdoc-page > .msdoc-root{
  color-scheme:dark;
  background:#161b22;
  border-color:rgba(139,148,158,.24);
  color:#e6edf3;
  box-shadow:0 18px 44px rgba(0,0,0,.36);
}
[data-viewer-theme='dark'] .msdoc-page > .msdoc-root span[style*='color:#000000']{
  color:#e6edf3!important;
}
[data-viewer-theme='dark'] .msdoc-page .msdoc-link{
  color:#58a6ff;
}
[data-viewer-theme='dark'] .msdoc-page .msdoc-attachment{
  border-color:rgba(139,148,158,.3);
  background:#21262d;
  color:#58a6ff;
}
@media (prefers-color-scheme:dark){
  [data-viewer-theme='system'] .msdoc-stage{
    background:var(--file-viewer-render-surface-background,#111827);
  }
  [data-viewer-theme='system'] .msdoc-page > .msdoc-root{
    color-scheme:dark;
    background:#161b22;
    border-color:rgba(139,148,158,.24);
    color:#e6edf3;
    box-shadow:0 18px 44px rgba(0,0,0,.36);
  }
  [data-viewer-theme='system'] .msdoc-page > .msdoc-root span[style*='color:#000000']{
    color:#e6edf3!important;
  }
  [data-viewer-theme='system'] .msdoc-page .msdoc-link{
    color:#58a6ff;
  }
  [data-viewer-theme='system'] .msdoc-page .msdoc-attachment{
    border-color:rgba(139,148,158,.3);
    background:#21262d;
    color:#58a6ff;
  }
}
@media (max-width: 860px){
  .msdoc-stage{
    padding:16px 12px 24px;
    gap:16px;
  }
  .msdoc-page{
    width:100%;
  }
  .msdoc-page > .msdoc-root{
    min-height:auto;
    padding:24px 20px;
    box-shadow:none;
  }
}
`,de=`
.msdoc-zoom-viewer{
  box-sizing:border-box;
  height:100%;
  overflow:auto;
  background:var(--file-viewer-render-surface-background,#ececec);
}
[data-viewer-theme='dark'] .msdoc-zoom-viewer{
  background:var(--file-viewer-render-surface-background,#111827);
}
@media (prefers-color-scheme:dark){
  [data-viewer-theme='system'] .msdoc-zoom-viewer{
    background:var(--file-viewer-render-surface-background,#111827);
  }
}
.msdoc-zoom-viewer .msdoc-stage{
  min-width:max-content;
}
.msdoc-zoom-viewer .msdoc-page{
  position:relative;
  max-width:none;
  overflow:visible;
}
.msdoc-zoom-viewer .msdoc-page > .msdoc-root{
  position:absolute;
  top:0;
  left:50%;
  width:${D.width}px;
  max-width:none;
  margin:0;
  transform-origin:top center;
}
`,fe=e=>e.ownerDocument.defaultView;function pe(e){return e.replace(/<(p|table|section)([^>]*?)style="([^"]*?\bbreak-before\s*:\s*page;?[^"]*?)"([^>]*)>/gi,e=>`${ce}${e}`)}function me(e){return`<div class="msdoc-stage">${pe(e).split(ce).map(e=>`<section class="msdoc-page"><div class="msdoc-root">${e||le}</div></section>`).join(``)}</div>`}function he(e){let n=e.cloneNode(!0);return n.classList.remove(`msdoc-zoom-viewer`),n.querySelectorAll(`style[data-msdoc-zoom]`).forEach(e=>e.remove()),n.querySelectorAll(`.msdoc-stage, .msdoc-page, .msdoc-root`).forEach(e=>{e.style.height=`auto`,e.style.maxHeight=`none`,e.style.overflow=`visible`,e.style.transform=`none`}),n.querySelectorAll(`.msdoc-page`).forEach((e,n)=>{e.dataset.viewerPrintPageIndex=String(n),c(e,D,{heightMode:`min`}),e.style.position=`relative`,e.style.width=t(D.width),e.style.maxWidth=`none`,e.style.margin=`0 auto 18px`;let r=e.querySelector(`.msdoc-root`);r&&(r.style.position=`relative`,r.style.top=`auto`,r.style.left=`auto`,r.style.width=t(D.width),r.style.maxWidth=`none`,r.style.minHeight=t(D.height),r.style.height=`auto`,r.style.transform=`none`,r.style.transformOrigin=`top left`,r.style.boxShadow=`none`,r.style.border=`0`,r.style.overflow=`visible`)}),n.innerHTML}function ge(){return s({selector:`.viewer-export-content .msdoc-page`,width:D.width,height:D.height,heightMode:`min`})}function _e(e){let t=Array.from(e.querySelectorAll(`.msdoc-page`));if(!t.length)return()=>{};e.classList.add(`msdoc-zoom-viewer`);let n=fe(e),r=n?.ResizeObserver,i=e.ownerDocument.createElement(`style`);i.dataset.msdocZoom=`true`,i.textContent=de,e.prepend(i);let o=h(),s=0,c=1,u=1,d=1,f=e=>Math.min(k,Math.max(O,Number(e.toFixed(2)))),p=()=>{let n=u,r=d,i=!1;t.forEach(t=>{let a=t.querySelector(`.msdoc-root`);if(!a)return;let o=a.offsetWidth||D.width,s=Math.max(a.scrollHeight,a.offsetHeight,D.height),l=Math.max(e.clientWidth-48,120),u=Math.min(1,Math.max(O,l/o)),d=f(u*c);i||=(n=d,r=u,!0),a.style.transform=`translateX(-50%) scale(${d})`,t.style.width=`${Math.ceil(Math.max(o*d,120))}px`,t.style.height=`${Math.ceil(s*d)}px`}),i&&(u=n,d=r,o.emit())},m=()=>{if(!n){p();return}n.cancelAnimationFrame(s),s=n.requestAnimationFrame(()=>{p()})},g=()=>({scale:u,label:`${Math.round(u*100)}%`,canZoomIn:u<k,canZoomOut:u>O,canReset:c!==1,minScale:O,maxScale:k}),v=e=>(c=Math.min(6,Math.max(.2,Number(e.toFixed(2)))),n?.cancelAnimationFrame(s),p(),g()),y=e=>v(e/Math.max(d,.01)),b=()=>({width:t[0]?.querySelector(`.msdoc-root`)?.offsetWidth||D.width,height:D.height}),x=t=>{let n=b(),r=t.mode===`auto`?`width`:t.mode,i=l({mode:r,viewportWidth:Math.max(1,t.viewportWidth||e.clientWidth||0),viewportHeight:Math.max(1,t.viewportHeight||e.clientHeight||0),contentWidth:n.width,contentHeight:n.height,currentScale:u,minScale:t.minScale??O,maxScale:t.maxScale??k});if(!i)return{applied:!1,mode:t.mode,resize:t.resize,source:t.source,reason:`unmeasurable`,provider:`zoom`};let a=y(i);return{applied:!0,mode:t.mode,resize:t.resize,scale:a.scale,source:t.source,provider:`zoom`}};e.dataset.viewerZoomProvider=`doc`,_(e,{zoomIn:()=>v((u+A)/Math.max(d,.01)),zoomOut:()=>v((u-A)/Math.max(d,.01)),resetZoom:()=>v(1),setZoom:y,fit:x,getState:g,subscribe:o.subscribe});let S=r?new r(m):null;return S?.observe(e),t.forEach(e=>{let t=e.querySelector(`.msdoc-root`);t&&S?.observe(t)}),p(),()=>{n?.cancelAnimationFrame(s),S?.disconnect(),a(e),i.remove(),e.classList.remove(`msdoc-zoom-viewer`)}}async function ve(e,t,n){var r,i;let a=await C(e,{renderOptions:{reviewMode:n?.options?.docx?.reviewMode??`all`,css:`${b()}\n${ue}`,externalLinkPolicy:n?.options?.docx?.externalLinkPolicy??`block`,externalResourcePolicy:n?.options?.docx?.externalResourcePolicy??`block`}}),o=t.ownerDocument.defaultView;if(!o)throw Error(`The DOC target must belong to a browser document`);let s=t.ownerDocument.createElement(`style`);s.dataset.msdoc=``,s.textContent=a.css;let c=t.ownerDocument.createElement(`div`);c.append(x(me(a.html),o)),t.replaceChildren(s,...Array.from(c.childNodes));let l=_e(t);return(r=n?.registerExportAdapter)==null||r.call(n,{includeDocumentStyles:!1,getPrintMaskPages:()=>Array.from(t.querySelectorAll(`.msdoc-page`)),printStyle:ge,toHtml:()=>he(t)}),(i=n?.registerThumbnailAdapter)==null||i.call(n,{getTarget:()=>t.querySelector(`.msdoc-page`)||t}),{$el:t,unmount(){var e,r;(e=n?.registerExportAdapter)==null||e.call(n,null),(r=n?.registerThumbnailAdapter)==null||r.call(n,null),l(),t.replaceChildren()}}}var ye=new WeakMap,j=e=>Number.parseFloat(e)||0;function be(e){let t=e.ownerDocument.defaultView;if(!t)return;let n=e.getBoundingClientRect(),r=t.getComputedStyle(e),i=j(r.width)+(r.boxSizing===`border-box`?0:j(r.paddingLeft)+j(r.paddingRight)+j(r.borderLeftWidth)+j(r.borderRightWidth)),a=i?n.width/i:0;if(!(!Number.isFinite(a)||a<=0))for(let n of e.querySelectorAll(`[data-docx-anchor-horizontal="column"], [data-docx-anchor-horizontal="margin"]`)){let r=n.closest(`[data-docx-anchor-context="paragraph"]`);if(!r||n.offsetParent!==r||t.getComputedStyle(n).position!==`absolute`||!n.style.left||r.closest(`td, th`))continue;let i=n.dataset.docxAnchorHorizontal===`margin`?e:r.closest(`article, header, footer`);if(!i||!e.contains(i)&&i!==e)continue;let o=t.getComputedStyle(i),s=i.getBoundingClientRect().left+(j(o.borderLeftWidth)+j(o.paddingLeft))*a,c=t.getComputedStyle(r),l=r.getBoundingClientRect();if(i!==e){let e=j(o.columnCount),t=j(o.columnWidth);if(e>1||t>0){let n=o.columnGap===`normal`?j(o.fontSize):j(o.columnGap),r=i.clientWidth-j(o.paddingLeft)-j(o.paddingRight),u=e>0?e:Math.max(1,Math.floor((r+n)/(t+n))),d=(r+n)/u;if(d>0){let e=(l.left-s)/a-j(c.marginLeft);s+=Math.round(e/d)*d*a}}}let u=l.left+j(c.borderLeftWidth)*a,d=(s-u)/a;if(!Number.isFinite(d))continue;let f=ye.get(n)??n.style.left;ye.set(n,f),n.style.left=Math.abs(d)<.01?f:`calc(${f} + ${d.toFixed(4)}px)`}}var M={width:794,height:1123},xe=new Set([`file:`,`about:`,`data:`]),N=.24,P=3,Se=.15,Ce=20555,we=`http://schemas.openxmlformats.org/wordprocessingml/2006/main`,Te=`http://schemas.openxmlformats.org/officeDocument/2006/relationships`,Ee=`http://schemas.openxmlformats.org/package/2006/relationships`,De=`urn:schemas-microsoft-com:vml`,Oe=`word/document.xml`,ke=`word/_rels/document.xml.rels`,F=`docx-page-background`,Ae={bmp:`image/bmp`,gif:`image/gif`,jpeg:`image/jpeg`,jpg:`image/jpeg`,png:`image/png`,svg:`image/svg+xml`,tif:`image/tiff`,tiff:`image/tiff`,webp:`image/webp`},je=e=>{let t=typeof e.renderAsync==`function`?e:e.default;if(!t||typeof t.renderAsync!=`function`)throw TypeError(`@file-viewer/docx did not expose a compatible renderAsync function.`);return t},Me=(()=>{let e={module:null,async load(){return this.module||=g(()=>import(`./docx-preview-D5kIIrUC.js`),__vite__mapDeps([0,1,2,3,4])),this.module}};return async()=>je(await e.load())})(),I=e=>e instanceof Error&&/(?:undefined|null).*children|children.*(?:undefined|null)/i.test(e.message)&&/renderHeaderFooter/i.test(e.stack||``),L=async(e,t,n,r)=>{try{return await e(t,n,void 0,r),!1}catch(i){if(!I(i))throw i;return n.replaceChildren(),await e(t,n,void 0,{...r,renderHeaders:!1,renderFooters:!1}),!0}},Ne=(e,t)=>{if((e.byteLength>=4?new DataView(e).getUint16(0,!1):0)!==Ce)throw Error(p(t?.options)(`word.error.invalidDocx`))},R=e=>e.ownerDocument.defaultView,Pe=e=>new((R(e)?.DOMParser)??globalThis.DOMParser),Fe=e=>{let t=[e.ownerDocument.URL,R(e)?.location?.href,globalThis.location?.href].filter(Boolean);for(let e of t)try{return new URL(e).protocol}catch{}return``},z=(e,t,n)=>{let r=Array.from(e.getElementsByTagNameNS(t,n));return r.length?r:Array.from(e.getElementsByTagName(`*`)).filter(e=>e.localName===n)},B=(e,t)=>{let n=t.parseFromString(e,`application/xml`);return z(n,`http://www.mozilla.org/newlayout/xml/parsererror.xml`,`parsererror`).length?null:n},Ie=(e,t)=>{let n=t.startsWith(`/`)?[]:e.split(`/`).slice(0,-1);return t.replace(/^\/+/,``).split(`/`).forEach(e=>{if(!(!e||e===`.`)){if(e===`..`){n.pop();return}n.push(e)}}),n.join(`/`)},Le=e=>Ae[e.split(`.`).pop()?.toLowerCase()||``],V=async(e,t=()=>new DOMParser)=>{try{let n=await w.default.loadAsync(e),r=n.file(Oe),i=n.file(ke);if(!r||!i)return;let a=t(),o=B(await r.async(`string`),a),s=B(await i.async(`string`),a);if(!o||!s)return;let c=z(o,we,`background`)[0],l=c&&z(c,De,`fill`)[0],u=l?.getAttributeNS(Te,`id`)||l?.getAttribute(`r:id`);if(!u)return;let d=z(s,Ee,`Relationship`).find(e=>e.getAttribute(`Id`)===u),f=d?.getAttribute(`Target`);if(!f||d?.getAttribute(`TargetMode`)===`External`)return;let p=Ie(Oe,f),m=Le(p),h=n.file(p)||n.file(decodeURIComponent(p));return!m||!h?void 0:`data:${m};base64,${await h.async(`base64`)}`}catch{return}},H=(e,t)=>{if(!t)return 0;let n=0;return e.querySelectorAll(`section.docx`).forEach(r=>{let i=Array.from(r.children).find(e=>e.classList.contains(F)),a=i||e.ownerDocument.createElement(`div`);a.className=F,a.setAttribute(`aria-hidden`,`true`),a.style.backgroundImage=`url("${t}")`,i||r.prepend(a),n+=1}),n},Re=(e,t)=>t?.worker===!1?!1:t?.worker===!0||!!((R(e)?.Worker??globalThis.Worker)&&t?.workerUrl&&!xe.has(Fe(e))),ze=e=>{let t=R(e)?.matchMedia??globalThis.matchMedia;return typeof t==`function`&&t(`(prefers-color-scheme: dark)`).matches},Be=(e,t,n)=>{if(n?.darkMode!==void 0)return n.darkMode;let r=f(t?.options?.theme);return r===`dark`||r!==`light`&&ze(e)},U=(e,t)=>!e||t?e:/[?&]file-viewer-docx=[^&#]*/.test(e)?e.replace(/([?&])file-viewer-docx=[^&#]*/,`$1file-viewer-docx=${o}`):`${e}${e.includes(`?`)?`&`:`?`}file-viewer-docx=${o}`,W=(e,t)=>{if(t===`allow`)return 0;let n=0;return e.querySelectorAll(`a[href]`).forEach(e=>{let t=e.getAttribute(`href`);!t||t.startsWith(`#`)||(e.hasAttribute(`data-docx-external-href`)||e.setAttribute(`data-docx-external-href`,t),e.removeAttribute(`href`),e.setAttribute(`aria-disabled`,`true`),n+=1)}),n},G=(e,t,a)=>{let o=t?.options?.docx,s=n(e.ownerDocument),c=Re(e,o),l=o?.visualPagination===!0,u=Be(e,t,o),d=e=>{(e.phase===`render`||e.phase===`layout`||e.phase===`done`)&&a()},f=o?.externalLinkPolicy??`block`,p=o?.externalResourcePolicy??`block`,m={useWorker:c,reviewMode:o?.reviewMode??`all`,breakPages:!0,fixedPageHeight:l,ignoreLastRenderedPageBreak:o?.ignoreLastRenderedPageBreak??!l,externalLinkPolicy:f,externalResourcePolicy:p,darkMode:u,progress:t=>{(t.phase===`render`||t.phase===`layout`||t.phase===`done`)&&W(e,f),d(t)}};return c&&(m.workerUrl=U(r(o,s),!!o?.workerUrl),m.workerJsZipUrl=U(i(o,s),!!o?.workerJsZipUrl)),o?.workerTimeout!==void 0&&(m.workerTimeout=o.workerTimeout),o?.renderPageBatchSize===void 0?o?.progressive===!1&&(m.renderPageBatchSize=2**53-1):m.renderPageBatchSize=o.renderPageBatchSize,o?.renderYieldEveryMs!==void 0&&(m.renderYieldEveryMs=o.renderYieldEveryMs),o?.strictWordCompatibility!==void 0&&(m.strictWordCompatibility=o.strictWordCompatibility),o?.paginationTolerance!==void 0&&(m.paginationTolerance=o.paginationTolerance),o?.maxDynamicPaginationPasses!==void 0&&(m.maxDynamicPaginationPasses=o.maxDynamicPaginationPasses),o?.awaitLayout!==void 0&&(m.awaitLayout=o.awaitLayout),o?.preserveComplexFieldResults!==void 0&&(m.preserveComplexFieldResults=o.preserveComplexFieldResults),o?.updatePageReferences!==void 0&&(m.updatePageReferences=o.updatePageReferences),o?.hideWebHiddenContent!==void 0&&(m.hideWebHiddenContent=o.hideWebHiddenContent),m},K=(e,t)=>{let n=R(t)?.HTMLElement;return n?e instanceof n:e instanceof HTMLElement},Ve=`
/* This component has no review balloon rail, so keep all-markup deletions readable inline. */
.docx-fit-viewer [data-docx-review-enabled="true"][data-docx-review-mode="all"] del[data-docx-change-kind]{display:inline!important;width:auto!important;max-width:none!important;height:auto!important;overflow:visible!important;line-height:inherit!important;color:var(--docx-review-color,#c2410c);text-decoration:line-through;text-decoration-color:var(--docx-review-color,#c2410c)}
.docx-fit-viewer {
  box-sizing: border-box;
  height: 100%;
  overflow: auto;
  background: var(--file-viewer-render-surface-background, #ececec);
  color-scheme: light;
}
.docx-fit-viewer[data-docx-dark-mode='true'] {
  background: var(--file-viewer-render-surface-background, #242424);
  color-scheme: dark;
}
.docx-fit-viewer .docx-wrapper {
  box-sizing: border-box;
  min-width: 0 !important;
  width: 100% !important;
  padding: 24px 14px 40px !important;
  background: var(--file-viewer-render-surface-background, #e7e9ec) !important;
}
.docx-fit-viewer[data-docx-dark-mode='true'] .docx-wrapper {
  background: var(--file-viewer-render-surface-background, #242424) !important;
}
.docx-fit-viewer .docx-page-frame {
  position: relative;
  width: 100%;
  min-width: 0;
  margin: 0 auto 24px;
  overflow: visible;
}
.docx-fit-viewer .docx-flow-frame {
  position: relative;
  width: 100%;
  min-width: 0;
  margin: 0 auto 28px;
  overflow: visible;
}
.docx-fit-viewer .docx-page-frame > section.docx,
.docx-fit-viewer .docx-flow-frame > section.docx {
  position: absolute;
  top: 0;
  left: 50%;
  margin: 0 !important;
  background: #ffffff !important;
  box-shadow: 0 2px 14px rgba(25, 35, 48, 0.18);
  box-sizing: border-box;
  overflow: hidden;
  transform-origin: top center;
}
.docx-fit-viewer .docx-page-background {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background-position: center;
  background-repeat: no-repeat;
  background-size: 100% 100%;
}
.docx-fit-viewer[data-docx-dark-mode='true'] .docx-page-frame > section.docx,
.docx-fit-viewer[data-docx-dark-mode='true'] .docx-flow-frame > section.docx {
  background: rgb(51, 51, 51) !important;
  box-shadow: 0 0 10px rgba(0, 0, 0, 0.8);
  outline: 1px solid rgba(255, 255, 255, 0.15);
  outline-offset: -1px;
}
.docx-fit-viewer .docx-flow-frame > section.docx {
  height: auto !important;
  min-height: var(--docx-page-height, auto) !important;
  overflow: visible !important;
}
.docx-fit-viewer .docx-page-frame > section.docx > article,
.docx-fit-viewer .docx-flow-frame > section.docx > article {
  position: relative;
  z-index: 1;
}
`;function He(e){let t=e.ownerDocument.createElement(`style`);return t.textContent=Ve,e.prepend(t),t}function Ue(e,t){let n=e.querySelector(`.docx-wrapper`);return n?Array.from(n.children).flatMap(n=>{if(!K(n,e)||!n.matches(`section.docx`))return[];let r=e.ownerDocument.createElement(`div`);return r.className=t?`docx-page-frame`:`docx-flow-frame`,n.before(r),r.appendChild(n),[r]}):[]}function We(e,t){e.classList.add(`docx-fit-viewer`);let n=He(e),r=t?.options?.docx?.visualPagination===!0,i=Ue(e,r),o=R(e),s=o?.ResizeObserver,c=0,u=1,f=1,p=1,m=h(),g=e=>Math.min(P,Math.max(N,Number(e.toFixed(2)))),v=()=>{let t=f,n=p,a=!1;i.forEach(i=>{let o=i.firstElementChild;if(!K(o,e))return;o.style.transform=`translateX(-50%)`,be(o);let s=o.offsetWidth,c=r?o.offsetHeight:Math.max(o.scrollHeight,o.offsetHeight);if(!s||!c)return;let l=Math.max(e.clientWidth-28,120),d=Math.min(1,Math.max(N,l/s)),f=g(d*u);a||=(t=f,n=d,!0),o.style.transform=`translateX(-50%) scale(${f})`,i.style.width=`${Math.ceil(Math.max(s*f,e.clientWidth-28,120))}px`,i.style.maxWidth=`none`,i.style.height=`${Math.ceil(c*f)}px`}),a&&(f=t,p=n,m.emit())},y=()=>{if(!o){v();return}o.cancelAnimationFrame(c),c=o.requestAnimationFrame(()=>{v()})},b=()=>({scale:f,label:`${Math.round(f*100)}%`,canZoomIn:f<P,canZoomOut:f>N,canReset:u!==1,minScale:N,maxScale:P}),x=e=>(u=Math.min(6,Math.max(.2,Number(e.toFixed(2)))),o?.cancelAnimationFrame(c),v(),b()),S=e=>x(e/Math.max(p,.01)),C=()=>{for(let e of i){let t=q(e);if(!t)continue;let n=d(t,M);return{width:t.offsetWidth||n.width||M.width,height:J(e)?M.height:t.offsetHeight||n.height||M.height}}return null},ee=t=>{let n=C();if(!n)return{applied:!1,mode:t.mode,resize:t.resize,source:t.source,reason:`unmeasurable`,provider:`zoom`};let r=t.mode===`auto`?`width`:t.mode,i=l({mode:r,viewportWidth:Math.max(1,t.viewportWidth||e.clientWidth||0),viewportHeight:Math.max(1,t.viewportHeight||e.clientHeight||0),contentWidth:n.width,contentHeight:n.height,currentScale:f,minScale:t.minScale??N,maxScale:t.maxScale??P});if(!i)return{applied:!1,mode:t.mode,resize:t.resize,source:t.source,reason:`unmeasurable`,provider:`zoom`};let a=S(i);return{applied:!0,mode:t.mode,resize:t.resize,scale:a.scale,source:t.source,provider:`zoom`}};e.dataset.viewerZoomProvider=`docx`,_(e,{zoomIn:()=>x((f+Se)/Math.max(p,.01)),zoomOut:()=>x((f-Se)/Math.max(p,.01)),resetZoom:()=>x(1),setZoom:S,fit:ee,getState:b,subscribe:m.subscribe});let w=s?new s(y):null;return w?.observe(e),i.forEach(e=>{let t=q(e);t&&w?.observe(t)}),v(),()=>{o?.cancelAnimationFrame(c),w?.disconnect(),a(e),n.remove(),e.classList.remove(`docx-fit-viewer`)}}function q(e){let t=e.firstElementChild,n=e.ownerDocument.defaultView?.HTMLElement;return n&&t instanceof n?t:null}function J(e){return!!e?.classList.contains(`docx-flow-frame`)}function Ge(e){return!!e?.classList.contains(`docx-canvas-sheet`)}function Ke(e){let t=e?q(e):null;if(!t)return M;let n=d(t,M);return J(e)?{width:n.width,height:Math.max(t.scrollHeight||0,t.offsetHeight||0,M.height)}:n}function qe(e,n){var r;if(Ge(e)){c(e,n),(r=e.dataset).viewerPrintPageIndex||(r.viewerPrintPageIndex=`0`),e.classList.remove(`docx-canvas-sheet-pending`,`docx-canvas-sheet-virtualized`),e.style.position=`relative`,e.style.contain=`none`,e.style.margin=`0 auto 18px`,e.style.boxShadow=`none`;return}let i=J(e),a=t(n.width),o=t(n.height);c(e,n,{heightMode:i?`min`:`fixed`}),e.style.margin=`0 auto 18px`;let s=q(e);s&&(s.style.position=`relative`,s.style.top=`auto`,s.style.left=`auto`,s.style.width=a,s.style.maxWidth=`none`,s.style.minHeight=i?`0`:o,s.style.height=i?`auto`:o,s.style.margin=`0 auto`,s.style.transform=`none`,s.style.transformOrigin=`top left`,s.style.overflow=i?`visible`:`hidden`,s.style.boxShadow=`none`)}function Je(e){let t=e.querySelector(`.docx-page-frame, .docx-flow-frame, .docx-canvas-sheet`),n=Ke(t||void 0),r=Ge(t||void 0)?`.viewer-export-content .docx-canvas-sheet`:t?.classList.contains(`docx-flow-frame`)?`.viewer-export-content .docx-flow-frame`:`.viewer-export-content .docx-page-frame`;return s({selector:r,width:n.width,height:t?.classList.contains(`docx-flow-frame`)?M.height:n.height,heightMode:t?.classList.contains(`docx-flow-frame`)?`min`:`fixed`})}async function Ye(e){let t=R(e),n=!!e.querySelector(`.docx-canvas-sheet`),r=t?.__docxCanvasMaterializeAllPages,i=!1;n&&typeof r==`function`&&(await r.call(t,`file-viewer-print-snapshot`),await m(t),i=!0);try{let t=`.docx-page-frame, .docx-flow-frame, .docx-canvas-sheet`,n=Array.from(e.querySelectorAll(t)),r=e.cloneNode(!0);u(e,r);let i=e.ownerDocument.createElement(`div`);i.className=`docx-print-document`;let a=Array.from(r.querySelectorAll(`style`)).filter(e=>!e.textContent?.includes(`.docx-fit-viewer`)).map(e=>e.outerHTML).join(``);return r.querySelectorAll(t).forEach((e,t)=>{e.dataset.viewerPrintPageIndex=String(t),qe(e,Ke(n[t])),i.appendChild(e.cloneNode(!0))}),i.childElementCount?`${a}${i.outerHTML}`:r.innerHTML}finally{if(i&&t){let n=e.ownerDocument.createEvent(`Event`);n.initEvent(`afterprint`,!1,!1),t.dispatchEvent(n)}}}async function Xe(e,t,n){var r,i;Ne(e,n),t.replaceChildren();let a=!1,o=()=>{var e;a||(a=!0,(e=n?.onProgressiveRender)==null||e.call(n))},s=G(t,n,o),[{defaultOptions:c,renderAsync:l},u]=await Promise.all([Me(),V(e,()=>Pe(t))]);t.dataset.docxWorker=s.useWorker?`self`:`false`,t.dataset.docxDarkMode=s.darkMode?`true`:`false`;let d=await L(l,e,t,{...c,...s});W(t,s.externalLinkPolicy),t.dataset.docxHeaderFooterFallback=d?`true`:`false`,t.dataset.docxPageBackground=H(t,u)>0?`true`:`false`,o();let f=We(t,n);return(r=n?.registerExportAdapter)==null||r.call(n,{includeDocumentStyles:!1,getPrintMaskPages:()=>Array.from(t.querySelectorAll(`.docx-page-frame, .docx-flow-frame, .docx-canvas-sheet`)),beforeSnapshot:()=>{let e=R(t);e&&e.dispatchEvent(new e.Event(`resize`))},printStyle:()=>Je(t),toHtml:()=>Ye(t)}),(i=n?.registerThumbnailAdapter)==null||i.call(n,{getTarget:()=>t.querySelector(`.docx-page-frame, .docx-flow-frame, .docx-canvas-sheet`)||t}),{$el:t,unmount(){var e,r;(e=n?.registerExportAdapter)==null||e.call(n,null),(r=n?.registerThumbnailAdapter)==null||r.call(n,null),f(),delete t.dataset.docxWorker,delete t.dataset.docxDarkMode,delete t.dataset.docxHeaderFooterFallback,delete t.dataset.docxPageBackground,t.replaceChildren()}}}var Y=`http://schemas.microsoft.com/office/word/2003/wordml`,X=`http://schemas.openxmlformats.org/wordprocessingml/2006/main`,Z=`http://schemas.openxmlformats.org/officeDocument/2006/relationships`,Q=`http://schemas.openxmlformats.org/package/2006/relationships`,$=`http://schemas.openxmlformats.org/package/2006/content-types`,Ze=`http://schemas.microsoft.com/office/word/2003/auxHint`,Qe=`urn:schemas-microsoft-com:vml`,$e=`http://www.w3.org/2000/xmlns/`,et={"h-ansi":`hAnsi`,fareast:`eastAsia`,"b-cs":`bCs`,"i-cs":`iCs`,"sz-cs":`szCs`,"lang-cs":`lang`,"panose-1":`panose1`,"line-rule":`lineRule`,"line-pitch":`linePitch`,"char-space":`charSpace`,"hanging-chars":`hangingChars`,"first-line":`firstLine`,"first-line-chars":`firstLineChars`,"left-chars":`leftChars`,"right-chars":`rightChars`};function tt(e){let t=new Uint8Array(e),n=t[0]===255&&t[1]===254||t[0]===60&&t[1]===0,r=t[0]===254&&t[1]===255||t[0]===0&&t[1]===60;return new TextDecoder(n?`utf-16le`:r?`utf-16be`:`utf-8`).decode(t)}async function nt(e,t){let n=tt(e);if(/<!DOCTYPE\b|<!ENTITY\b/i.test(n))throw Error(`Word 2003 XML must not contain a DTD or entity declarations.`);let r=t.ownerDocument.defaultView,i=r?.DOMParser??globalThis.DOMParser,a=r?.XMLSerializer??globalThis.XMLSerializer,o=new i().parseFromString(n,`application/xml`);if(o.getElementsByTagName(`parsererror`).length||o.documentElement.localName!==`wordDocument`||o.documentElement.namespaceURI!==Y)throw Error(`Invalid Word 2003 XML document.`);let s=Array.from(o.documentElement.children).find(e=>e.namespaceURI===Y&&e.localName===`body`);if(!s)throw Error(`Word 2003 XML document has no body.`);let c=new w.default,l=new a,u=[],d=new Map;for(let e of Array.from(o.getElementsByTagNameNS(Y,`binData`))){let t=e.getAttributeNS(Y,`name`)||``;if(d.has(t))continue;let n=/\.(png|jpe?g|gif|bmp|tiff?|emf|wmf)$/i.exec(t)?.[1].toLowerCase(),r=e.textContent?.replace(/\s/g,``)||``;if(t&&n&&/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(r)&&r){let e=`media/image${d.size+1}.${n}`;d.set(t,{path:e,bytes:r}),c.file(`word/${e}`,r,{base64:!0})}}let f=e=>{let t=o.implementation.createDocument(X,`w:${e}`,null);return t.documentElement.setAttributeNS($e,`xmlns:r`,Z),t},p=e=>`<?xml version="1.0" encoding="UTF-8"?>${l.serializeToString(e)}`,m=new Map;function h(e,t,n){let r=m.get(e);r||(r=o.implementation.createDocument(Q,`Relationships`,null),m.set(e,r));let i=`rId${r.documentElement.children.length+1}`,a=r.createElementNS(Q,`Relationship`);return a.setAttribute(`Id`,i),a.setAttribute(`Type`,`${Z}/${t}`),a.setAttribute(`Target`,n),r.documentElement.appendChild(a),i}let g=0,_=0;function v(e,t,n,r=0){if(r>256)throw Error(`Word 2003 XML nesting is too deep.`);let i=t.ownerDocument;if(e.nodeType===3||e.nodeType===4){t.appendChild(i.createTextNode(e.textContent||``));return}if(e.nodeType!==1)return;let a=e,o=a.namespaceURI===Y;if(o&&a.localName===`binData`)return;if(a.namespaceURI===Ze){for(let e of Array.from(a.childNodes))v(e,t,n,r+1);return}if(o&&(a.localName===`hdr`||a.localName===`ftr`)&&t.localName===`sectPr`){let e=a.localName===`hdr`,o=`${e?`header`:`footer`}${e?++g:++_}.xml`,s=f(e?`hdr`:`ftr`);for(let e of Array.from(a.childNodes))v(e,s.documentElement,o,r+1);c.file(`word/${o}`,p(s)),u.push([`/word/${o}`,`application/vnd.openxmlformats-officedocument.wordprocessingml.${e?`header`:`footer`}+xml`]);let l=i.createElementNS(X,`w:${e?`header`:`footer`}Reference`),d=a.getAttributeNS(Y,`type`);l.setAttributeNS(X,`w:type`,d===`first`||d===`even`?d:`default`),l.setAttributeNS(Z,`r:id`,h(n,e?`header`:`footer`,o)),t.appendChild(l);return}let s=o?X:a.namespaceURI,l=o&&et[a.localName]||a.localName,m=o?`w:${l}`:a.nodeName,y=i.createElementNS(s,m);for(let e of Array.from(a.attributes)){if(e.namespaceURI===$e||s===Qe&&(l===`imagedata`||l===`fill`)&&(e.localName===`src`||e.localName===`href`))continue;let t=e.namespaceURI===Y,n=t&&et[e.localName]||e.localName,r=e.value;t&&n===`hint`&&r===`fareast`&&(r=`eastAsia`),o&&l===`fldChar`&&t&&n===`fldCharType`&&r===`start`&&(r=`begin`),y.setAttributeNS(t?X:e.namespaceURI,t?`w:${n}`:e.name,r)}if(s===Qe&&(l===`imagedata`||l===`fill`)){let e=d.get(a.getAttribute(`src`)||``);e&&y.setAttributeNS(Z,`r:id`,h(n,`image`,e.path))}t.appendChild(y);for(let e of Array.from(a.childNodes))v(e,y,n,r+1)}let y=f(`document`),b=y.createElementNS(X,`w:body`);y.documentElement.appendChild(b);for(let e of Array.from(s.childNodes))v(e,b,`document.xml`);for(let e of Array.from(b.children)){if(e.localName!==`sectPr`||e===b.lastElementChild)continue;let t=e.previousElementSibling;(!t||t.localName!==`p`)&&(t=y.createElementNS(X,`w:p`),b.insertBefore(t,e));let n=Array.from(t.children).find(e=>e.localName===`pPr`);n||(n=y.createElementNS(X,`w:pPr`),t.insertBefore(n,t.firstChild)),n.appendChild(e)}c.file(`word/document.xml`,p(y)),u.push([`/word/document.xml`,`application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml`]);for(let[e,t,n,r]of[[`styles`,`styles`,`styles.xml`,`styles`],[`fonts`,`fonts`,`fontTable.xml`,`fontTable`],[`docPr`,`settings`,`settings.xml`,`settings`]]){let i=Array.from(o.documentElement.children).find(t=>t.namespaceURI===Y&&t.localName===e);if(!i)continue;let a=f(t);for(let e of Array.from(i.childNodes))v(e,a.documentElement,n);c.file(`word/${n}`,p(a)),h(`document.xml`,r,n),u.push([`/word/${n}`,`application/vnd.openxmlformats-officedocument.wordprocessingml.${r}+xml`])}for(let[e,t]of m)c.file(`word/_rels/${e}.rels`,p(t));let x=o.implementation.createDocument(Q,`Relationships`,null),S=x.createElementNS(Q,`Relationship`);for(let[e,t]of[[`Id`,`rId1`],[`Type`,`${Z}/officeDocument`],[`Target`,`word/document.xml`]])S.setAttribute(e,t);x.documentElement.appendChild(S),c.file(`_rels/.rels`,p(x));let C=o.implementation.createDocument($,`Types`,null);for(let[e,t]of[[`rels`,`application/vnd.openxmlformats-package.relationships+xml`],[`xml`,`application/xml`],...Object.entries({png:`image/png`,jpg:`image/jpeg`,jpeg:`image/jpeg`,gif:`image/gif`,bmp:`image/bmp`,tif:`image/tiff`,tiff:`image/tiff`,emf:`image/x-emf`,wmf:`image/x-wmf`})]){let n=C.createElementNS($,`Default`);n.setAttribute(`Extension`,e),n.setAttribute(`ContentType`,t),C.documentElement.appendChild(n)}for(let[e,t]of u){let n=C.createElementNS($,`Override`);n.setAttribute(`PartName`,e),n.setAttribute(`ContentType`,t),C.documentElement.appendChild(n)}return c.file(`[Content_Types].xml`,p(C)),c.generateAsync({type:`arraybuffer`})}export{G as a,V as c,se as d,H as i,Xe as l,tt as n,I as o,W as r,L as s,nt as t,ve as u};