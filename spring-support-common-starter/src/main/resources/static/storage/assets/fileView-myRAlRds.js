import{_ as et,C as he,b as tt,o as L,d as F,e as A,a as _,q as U,s as x,k as je,u as q,l as P,x as nt,w as me,y as Q,F as rt,z as ye,A as ge,t as ot,r as st,i as it}from"./display-_0nm5nf0.js";const at={data(){return{file:null,text:"hello world"}},mounted(){this.file=he.enc.Base64.parse(document.getElementById("fileId").value).toString(he.enc.Utf8)}},ct={class:"simple-view"};function ut(e,t,n,r,o,s){const i=tt("file-viewer");return L(),F("div",ct,[A(i,{url:o.file},null,8,["url"])])}const lt=et(at,[["render",ut],["__file","H:/workspace/vue-support-parent-starter/vue-support-view-starter/src/views/FileView.vue"]]);var ft=Object.defineProperty,dt=(e,t,n)=>t in e?ft(e,t,{enumerable:!0,configurable:!0,writable:!0,value:n}):e[t]=n,pt=(e,t,n)=>(dt(e,typeof t!="symbol"?t+"":t,n),n);const ht=(()=>{const e={module:null,async load(){return this.module||(this.module=x(()=>import("./docx-preview.min-WXON4Pds.js"),__vite__mapDeps([0,1,2]),import.meta.url).then(t=>t.d)),this.module}};return async()=>await e.load()})();async function mt(e,t){const{defaultOptions:n,renderAsync:r}=await ht(),o=Object.assign(n,{debug:!0,experimental:!0});await r(e,t,void 0,o)}const yt=U(()=>x(()=>import("./PptxRender-QbqKjK9B.js"),__vite__mapDeps([3,4,5,1,6]),import.meta.url));async function gt(e,t){return _({render:()=>A(yt,{data:e},null)}).mount(t)}const wt=U(()=>x(()=>import("./XlsxTable-nO5epM5Q.js"),__vite__mapDeps([7,4,5,1,6,2]),import.meta.url));async function we(e,t,n){const r=_({render:()=>A(wt,{data:e,type:n},null)});return r.mount(t),r}const bt=U(()=>x(()=>import("./PdfView-yB-sN4oQ.js"),__vite__mapDeps([8,4,5,1,2,6]),import.meta.url));async function Et(e,t){const n=_({render:()=>A(bt,{data:e},null)});return n.mount(t),n}async function Ot(e){return new Promise((t,n)=>{const r=new FileReader;r.onload=o=>{var s;return t((s=o.target)==null?void 0:s.result)},r.onerror=o=>n(o),r.readAsArrayBuffer(e)})}async function vt(e){return new Promise((t,n)=>{const r=new FileReader;r.onload=o=>{var s;const i=(s=o.target)==null?void 0:s.result;typeof i=="string"&&t(i)},r.onerror=o=>n(o),r.readAsDataURL(new Blob([e]))})}async function Pe(e){return new Promise((t,n)=>{const r=new FileReader;r.onload=o=>{var s;const i=(s=o.target)==null?void 0:s.result;typeof i=="string"&&t(i)},r.onerror=o=>n(o),r.readAsText(new Blob([e]),"utf-8")})}const Rt=U(()=>x(()=>import("./ImageViewer-_DjVnkB6.js"),__vite__mapDeps([9,4,5]),import.meta.url));async function St(e,t){const n=await vt(e),r=_({render:()=>A(Rt,{image:n},null)});return r.mount(t),r}const At=U(()=>x(()=>import("./MarkdownViewer-tTVCagFM.js"),__vite__mapDeps([10,4,5]),import.meta.url));async function _t(e,t){const n=await Pe(e),r=_({render:()=>A(At,{data:n},null)});return r.mount(t),r}const Tt={class:"code-area"},xt=je({__name:"CodeViewer",props:{value:null},setup(e){return(t,n)=>(L(),F("pre",Tt,"    "+q(e.value)+`
  `,1))}}),Ne=(e,t)=>{const n=e.__vccOpts||e;for(const[r,o]of t)n[r]=o;return n},Ct=Ne(xt,[["__scopeId","data-v-3be501eb"]]);async function jt(e,t){const n=await Pe(e),r=_({render:()=>A(Ct,{value:n},null)});return r.mount(t),r}function Pt(e,t){const n=document.createElement("video");n.width=840,n.height=480,n.controls=!0;const r=document.createElement("source");r.src=URL.createObjectURL(new Blob([e])),n.appendChild(r),t.appendChild(n)}const I=e=>({$el:e,unmount(){}}),Nt=[{accepts:["docx"],handler:async(e,t)=>(await mt(e,t),window.dispatchEvent(new Event("resize")),I(t))},{accepts:["pptx"],handler:async(e,t)=>(await gt(e,t),window.dispatchEvent(new Event("resize")),I(t))},{accepts:["xlsx"],handler:async(e,t)=>we(e,t,"xml")},{accepts:["xlsm","xlsb","xls","csv","ods","fods","numbers"],handler:async(e,t)=>we(e,t,"binary")},{accepts:["pdf"],handler:async(e,t)=>Et(e,t)},{accepts:["gif","jpg","jpeg","bmp","tiff","tif","png","svg"],handler:async(e,t)=>St(e,t)},{accepts:["md","markdown"],handler:async(e,t)=>_t(e,t)},{accepts:["txt","json","js","css","java","py","html","jsx","ts","tsx","xml","log"],handler:async(e,t)=>jt(e,t)},{accepts:["mp4"],handler:async(e,t)=>(Pt(e,t),I(t))},{accepts:["error"],handler:async(e,t,n)=>(t.innerHTML=`<div style='text-align: center; margin-top: 80px'>不支持.${n}格式的在线预览，请下载后预览或转换为支持的格式</div>
<div style='text-align: center'>支持docx, xlsx, pptx, pdf, 以及纯文本格式和各种图片格式的在线预览</div>`,I(t))}],Le=Nt.reduce((e,{accepts:t,handler:n})=>(t.forEach(r=>e.set(r,n)),e),new Map),be=Le.get("error");function Lt(e){const t=e.lastIndexOf(".");return e.substring(t+1)}async function Ft(e,t,n){const r=Le.get(t.toLowerCase());if(r)return r(e,n);if(be)return be(e,n,t)}function Fe(e,t){return function(){return e.apply(t,arguments)}}const{toString:Be}=Object.prototype,{getPrototypeOf:ie}=Object,ae=(e=>t=>{const n=Be.call(t);return e[n]||(e[n]=n.slice(8,-1).toLowerCase())})(Object.create(null)),v=e=>(e=e.toLowerCase(),t=>ae(t)===e),$=e=>t=>typeof t===e,{isArray:C}=Array,B=$("undefined");function Bt(e){return e!==null&&!B(e)&&e.constructor!==null&&!B(e.constructor)&&S(e.constructor.isBuffer)&&e.constructor.isBuffer(e)}const Ue=v("ArrayBuffer");function Ut(e){let t;return typeof ArrayBuffer<"u"&&ArrayBuffer.isView?t=ArrayBuffer.isView(e):t=e&&e.buffer&&Ue(e.buffer),t}const Dt=$("string"),S=$("function"),De=$("number"),ce=e=>e!==null&&typeof e=="object",kt=e=>e===!0||e===!1,M=e=>{if(ae(e)!=="object")return!1;const t=ie(e);return(t===null||t===Object.prototype||Object.getPrototypeOf(t)===null)&&!(Symbol.toStringTag in e)&&!(Symbol.iterator in e)},It=v("Date"),qt=v("File"),Mt=v("Blob"),zt=v("FileList"),Vt=e=>ce(e)&&S(e.pipe),Ht=e=>{const t="[object FormData]";return e&&(typeof FormData=="function"&&e instanceof FormData||Be.call(e)===t||S(e.toString)&&e.toString()===t)},Jt=v("URLSearchParams"),$t=e=>e.trim?e.trim():e.replace(/^[\s\uFEFF\xA0]+|[\s\uFEFF\xA0]+$/g,"");function D(e,t,{allOwnKeys:n=!1}={}){if(e===null||typeof e>"u")return;let r,o;if(typeof e!="object"&&(e=[e]),C(e))for(r=0,o=e.length;r<o;r++)t.call(null,e[r],r,e);else{const s=n?Object.getOwnPropertyNames(e):Object.keys(e),i=s.length;let l;for(r=0;r<i;r++)l=s[r],t.call(null,e[l],l,e)}}function ke(e,t){t=t.toLowerCase();const n=Object.keys(e);let r=n.length,o;for(;r-- >0;)if(o=n[r],t===o.toLowerCase())return o;return null}const Ie=typeof globalThis<"u"?globalThis:typeof self<"u"?self:typeof window<"u"?window:global,qe=e=>!B(e)&&e!==Ie;function ne(){const{caseless:e}=qe(this)&&this||{},t={},n=(r,o)=>{const s=e&&ke(t,o)||o;M(t[s])&&M(r)?t[s]=ne(t[s],r):M(r)?t[s]=ne({},r):C(r)?t[s]=r.slice():t[s]=r};for(let r=0,o=arguments.length;r<o;r++)arguments[r]&&D(arguments[r],n);return t}const Wt=(e,t,n,{allOwnKeys:r}={})=>(D(t,(o,s)=>{n&&S(o)?e[s]=Fe(o,n):e[s]=o},{allOwnKeys:r}),e),Kt=e=>(e.charCodeAt(0)===65279&&(e=e.slice(1)),e),Gt=(e,t,n,r)=>{e.prototype=Object.create(t.prototype,r),e.prototype.constructor=e,Object.defineProperty(e,"super",{value:t.prototype}),n&&Object.assign(e.prototype,n)},Xt=(e,t,n,r)=>{let o,s,i;const l={};if(t=t||{},e==null)return t;do{for(o=Object.getOwnPropertyNames(e),s=o.length;s-- >0;)i=o[s],(!r||r(i,e,t))&&!l[i]&&(t[i]=e[i],l[i]=!0);e=n!==!1&&ie(e)}while(e&&(!n||n(e,t))&&e!==Object.prototype);return t},Qt=(e,t,n)=>{e=String(e),(n===void 0||n>e.length)&&(n=e.length),n-=t.length;const r=e.indexOf(t,n);return r!==-1&&r===n},Yt=e=>{if(!e)return null;if(C(e))return e;let t=e.length;if(!De(t))return null;const n=new Array(t);for(;t-- >0;)n[t]=e[t];return n},Zt=(e=>t=>e&&t instanceof e)(typeof Uint8Array<"u"&&ie(Uint8Array)),en=(e,t)=>{const n=(e&&e[Symbol.iterator]).call(e);let r;for(;(r=n.next())&&!r.done;){const o=r.value;t.call(e,o[0],o[1])}},tn=(e,t)=>{let n;const r=[];for(;(n=e.exec(t))!==null;)r.push(n);return r},nn=v("HTMLFormElement"),rn=e=>e.toLowerCase().replace(/[-_\s]([a-z\d])(\w*)/g,function(t,n,r){return n.toUpperCase()+r}),Ee=(({hasOwnProperty:e})=>(t,n)=>e.call(t,n))(Object.prototype),on=v("RegExp"),Me=(e,t)=>{const n=Object.getOwnPropertyDescriptors(e),r={};D(n,(o,s)=>{t(o,s,e)!==!1&&(r[s]=o)}),Object.defineProperties(e,r)},sn=e=>{Me(e,(t,n)=>{if(S(e)&&["arguments","caller","callee"].indexOf(n)!==-1)return!1;const r=e[n];if(S(r)){if(t.enumerable=!1,"writable"in t){t.writable=!1;return}t.set||(t.set=()=>{throw Error("Can not rewrite read-only method '"+n+"'")})}})},an=(e,t)=>{const n={},r=o=>{o.forEach(s=>{n[s]=!0})};return C(e)?r(e):r(String(e).split(t)),n},cn=()=>{},un=(e,t)=>(e=+e,Number.isFinite(e)?e:t),Y="abcdefghijklmnopqrstuvwxyz",Oe="0123456789",ze={DIGIT:Oe,ALPHA:Y,ALPHA_DIGIT:Y+Y.toUpperCase()+Oe},ln=(e=16,t=ze.ALPHA_DIGIT)=>{let n="";const{length:r}=t;for(;e--;)n+=t[Math.random()*r|0];return n};function fn(e){return!!(e&&S(e.append)&&e[Symbol.toStringTag]==="FormData"&&e[Symbol.iterator])}const dn=e=>{const t=new Array(10),n=(r,o)=>{if(ce(r)){if(t.indexOf(r)>=0)return;if(!("toJSON"in r)){t[o]=r;const s=C(r)?[]:{};return D(r,(i,l)=>{const u=n(i,o+1);!B(u)&&(s[l]=u)}),t[o]=void 0,s}}return r};return n(e,0)},a={isArray:C,isArrayBuffer:Ue,isBuffer:Bt,isFormData:Ht,isArrayBufferView:Ut,isString:Dt,isNumber:De,isBoolean:kt,isObject:ce,isPlainObject:M,isUndefined:B,isDate:It,isFile:qt,isBlob:Mt,isRegExp:on,isFunction:S,isStream:Vt,isURLSearchParams:Jt,isTypedArray:Zt,isFileList:zt,forEach:D,merge:ne,extend:Wt,trim:$t,stripBOM:Kt,inherits:Gt,toFlatObject:Xt,kindOf:ae,kindOfTest:v,endsWith:Qt,toArray:Yt,forEachEntry:en,matchAll:tn,isHTMLForm:nn,hasOwnProperty:Ee,hasOwnProp:Ee,reduceDescriptors:Me,freezeMethods:sn,toObjectSet:an,toCamelCase:rn,noop:cn,toFiniteNumber:un,findKey:ke,global:Ie,isContextDefined:qe,ALPHABET:ze,generateString:ln,isSpecCompliantForm:fn,toJSONObject:dn};function y(e,t,n,r,o){Error.call(this),Error.captureStackTrace?Error.captureStackTrace(this,this.constructor):this.stack=new Error().stack,this.message=e,this.name="AxiosError",t&&(this.code=t),n&&(this.config=n),r&&(this.request=r),o&&(this.response=o)}a.inherits(y,Error,{toJSON:function(){return{message:this.message,name:this.name,description:this.description,number:this.number,fileName:this.fileName,lineNumber:this.lineNumber,columnNumber:this.columnNumber,stack:this.stack,config:a.toJSONObject(this.config),code:this.code,status:this.response&&this.response.status?this.response.status:null}}});const Ve=y.prototype,He={};["ERR_BAD_OPTION_VALUE","ERR_BAD_OPTION","ECONNABORTED","ETIMEDOUT","ERR_NETWORK","ERR_FR_TOO_MANY_REDIRECTS","ERR_DEPRECATED","ERR_BAD_RESPONSE","ERR_BAD_REQUEST","ERR_CANCELED","ERR_NOT_SUPPORT","ERR_INVALID_URL"].forEach(e=>{He[e]={value:e}});Object.defineProperties(y,He);Object.defineProperty(Ve,"isAxiosError",{value:!0});y.from=(e,t,n,r,o,s)=>{const i=Object.create(Ve);return a.toFlatObject(e,i,function(l){return l!==Error.prototype},l=>l!=="isAxiosError"),y.call(i,e.message,t,n,r,o),i.cause=e,i.name=e.name,s&&Object.assign(i,s),i};const pn=null;function re(e){return a.isPlainObject(e)||a.isArray(e)}function Je(e){return a.endsWith(e,"[]")?e.slice(0,-2):e}function ve(e,t,n){return e?e.concat(t).map(function(r,o){return r=Je(r),!n&&o?"["+r+"]":r}).join(n?".":""):t}function hn(e){return a.isArray(e)&&!e.some(re)}const mn=a.toFlatObject(a,{},null,function(e){return/^is[A-Z]/.test(e)});function W(e,t,n){if(!a.isObject(e))throw new TypeError("target must be an object");t=t||new FormData,n=a.toFlatObject(n,{metaTokens:!0,dots:!1,indexes:!1},!1,function(h,f){return!a.isUndefined(f[h])});const r=n.metaTokens,o=n.visitor||d,s=n.dots,i=n.indexes,l=(n.Blob||typeof Blob<"u"&&Blob)&&a.isSpecCompliantForm(t);if(!a.isFunction(o))throw new TypeError("visitor must be a function");function u(h){if(h===null)return"";if(a.isDate(h))return h.toISOString();if(!l&&a.isBlob(h))throw new y("Blob is not supported. Use a Buffer instead.");return a.isArrayBuffer(h)||a.isTypedArray(h)?l&&typeof Blob=="function"?new Blob([h]):Buffer.from(h):h}function d(h,f,w){let b=h;if(h&&!w&&typeof h=="object"){if(a.endsWith(f,"{}"))f=r?f:f.slice(0,-2),h=JSON.stringify(h);else if(a.isArray(h)&&hn(h)||(a.isFileList(h)||a.endsWith(f,"[]"))&&(b=a.toArray(h)))return f=Je(f),b.forEach(function(j,X){!(a.isUndefined(j)||j===null)&&t.append(i===!0?ve([f],X,s):i===null?f:f+"[]",u(j))}),!1}return re(h)?!0:(t.append(ve(w,f,s),u(h)),!1)}const c=[],p=Object.assign(mn,{defaultVisitor:d,convertValue:u,isVisitable:re});function m(h,f){if(!a.isUndefined(h)){if(c.indexOf(h)!==-1)throw Error("Circular reference detected in "+f.join("."));c.push(h),a.forEach(h,function(w,b){(!(a.isUndefined(w)||w===null)&&o.call(t,w,a.isString(b)?b.trim():b,f,p))===!0&&m(w,f?f.concat(b):[b])}),c.pop()}}if(!a.isObject(e))throw new TypeError("data must be an object");return m(e),t}function Re(e){const t={"!":"%21","'":"%27","(":"%28",")":"%29","~":"%7E","%20":"+","%00":"\0"};return encodeURIComponent(e).replace(/[!'()~]|%20|%00/g,function(n){return t[n]})}function ue(e,t){this._pairs=[],e&&W(e,this,t)}const $e=ue.prototype;$e.append=function(e,t){this._pairs.push([e,t])};$e.toString=function(e){const t=e?function(n){return e.call(this,n,Re)}:Re;return this._pairs.map(function(n){return t(n[0])+"="+t(n[1])},"").join("&")};function yn(e){return encodeURIComponent(e).replace(/%3A/gi,":").replace(/%24/g,"$").replace(/%2C/gi,",").replace(/%20/g,"+").replace(/%5B/gi,"[").replace(/%5D/gi,"]")}function We(e,t,n){if(!t)return e;const r=n&&n.encode||yn,o=n&&n.serialize;let s;if(o?s=o(t,n):s=a.isURLSearchParams(t)?t.toString():new ue(t,n).toString(r),s){const i=e.indexOf("#");i!==-1&&(e=e.slice(0,i)),e+=(e.indexOf("?")===-1?"?":"&")+s}return e}class gn{constructor(){this.handlers=[]}use(t,n,r){return this.handlers.push({fulfilled:t,rejected:n,synchronous:r?r.synchronous:!1,runWhen:r?r.runWhen:null}),this.handlers.length-1}eject(t){this.handlers[t]&&(this.handlers[t]=null)}clear(){this.handlers&&(this.handlers=[])}forEach(t){a.forEach(this.handlers,function(n){n!==null&&t(n)})}}const Se=gn,Ke={silentJSONParsing:!0,forcedJSONParsing:!0,clarifyTimeoutError:!1},wn=typeof URLSearchParams<"u"?URLSearchParams:ue,bn=typeof FormData<"u"?FormData:null,En=typeof Blob<"u"?Blob:null,On=(()=>{let e;return typeof navigator<"u"&&((e=navigator.product)==="ReactNative"||e==="NativeScript"||e==="NS")?!1:typeof window<"u"&&typeof document<"u"})(),vn=typeof WorkerGlobalScope<"u"&&self instanceof WorkerGlobalScope&&typeof self.importScripts=="function",E={isBrowser:!0,classes:{URLSearchParams:wn,FormData:bn,Blob:En},isStandardBrowserEnv:On,isStandardBrowserWebWorkerEnv:vn,protocols:["http","https","file","blob","url","data"]};function Rn(e,t){return W(e,new E.classes.URLSearchParams,Object.assign({visitor:function(n,r,o,s){return E.isNode&&a.isBuffer(n)?(this.append(r,n.toString("base64")),!1):s.defaultVisitor.apply(this,arguments)}},t))}function Sn(e){return a.matchAll(/\w+|\[(\w*)]/g,e).map(t=>t[0]==="[]"?"":t[1]||t[0])}function An(e){const t={},n=Object.keys(e);let r;const o=n.length;let s;for(r=0;r<o;r++)s=n[r],t[s]=e[s];return t}function Ge(e){function t(n,r,o,s){let i=n[s++];const l=Number.isFinite(+i),u=s>=n.length;return i=!i&&a.isArray(o)?o.length:i,u?(a.hasOwnProp(o,i)?o[i]=[o[i],r]:o[i]=r,!l):((!o[i]||!a.isObject(o[i]))&&(o[i]=[]),t(n,r,o[i],s)&&a.isArray(o[i])&&(o[i]=An(o[i])),!l)}if(a.isFormData(e)&&a.isFunction(e.entries)){const n={};return a.forEachEntry(e,(r,o)=>{t(Sn(r),o,n,0)}),n}return null}const _n={"Content-Type":void 0};function Tn(e,t,n){if(a.isString(e))try{return(t||JSON.parse)(e),a.trim(e)}catch(r){if(r.name!=="SyntaxError")throw r}return(n||JSON.stringify)(e)}const K={transitional:Ke,adapter:["xhr","http"],transformRequest:[function(e,t){const n=t.getContentType()||"",r=n.indexOf("application/json")>-1,o=a.isObject(e);if(o&&a.isHTMLForm(e)&&(e=new FormData(e)),a.isFormData(e))return r&&r?JSON.stringify(Ge(e)):e;if(a.isArrayBuffer(e)||a.isBuffer(e)||a.isStream(e)||a.isFile(e)||a.isBlob(e))return e;if(a.isArrayBufferView(e))return e.buffer;if(a.isURLSearchParams(e))return t.setContentType("application/x-www-form-urlencoded;charset=utf-8",!1),e.toString();let s;if(o){if(n.indexOf("application/x-www-form-urlencoded")>-1)return Rn(e,this.formSerializer).toString();if((s=a.isFileList(e))||n.indexOf("multipart/form-data")>-1){const i=this.env&&this.env.FormData;return W(s?{"files[]":e}:e,i&&new i,this.formSerializer)}}return o||r?(t.setContentType("application/json",!1),Tn(e)):e}],transformResponse:[function(e){const t=this.transitional||K.transitional,n=t&&t.forcedJSONParsing,r=this.responseType==="json";if(e&&a.isString(e)&&(n&&!this.responseType||r)){const o=!(t&&t.silentJSONParsing)&&r;try{return JSON.parse(e)}catch(s){if(o)throw s.name==="SyntaxError"?y.from(s,y.ERR_BAD_RESPONSE,this,null,this.response):s}}return e}],timeout:0,xsrfCookieName:"XSRF-TOKEN",xsrfHeaderName:"X-XSRF-TOKEN",maxContentLength:-1,maxBodyLength:-1,env:{FormData:E.classes.FormData,Blob:E.classes.Blob},validateStatus:function(e){return e>=200&&e<300},headers:{common:{Accept:"application/json, text/plain, */*"}}};a.forEach(["delete","get","head"],function(e){K.headers[e]={}});a.forEach(["post","put","patch"],function(e){K.headers[e]=a.merge(_n)});const le=K,xn=a.toObjectSet(["age","authorization","content-length","content-type","etag","expires","from","host","if-modified-since","if-unmodified-since","last-modified","location","max-forwards","proxy-authorization","referer","retry-after","user-agent"]),Cn=e=>{const t={};let n,r,o;return e&&e.split(`
`).forEach(function(s){o=s.indexOf(":"),n=s.substring(0,o).trim().toLowerCase(),r=s.substring(o+1).trim(),!(!n||t[n]&&xn[n])&&(n==="set-cookie"?t[n]?t[n].push(r):t[n]=[r]:t[n]=t[n]?t[n]+", "+r:r)}),t},Ae=Symbol("internals");function N(e){return e&&String(e).trim().toLowerCase()}function z(e){return e===!1||e==null?e:a.isArray(e)?e.map(z):String(e)}function jn(e){const t=Object.create(null),n=/([^\s,;=]+)\s*(?:=\s*([^,;]+))?/g;let r;for(;r=n.exec(e);)t[r[1]]=r[2];return t}function Pn(e){return/^[-_a-zA-Z]+$/.test(e.trim())}function Z(e,t,n,r,o){if(a.isFunction(r))return r.call(this,t,n);if(o&&(t=n),!!a.isString(t)){if(a.isString(r))return t.indexOf(r)!==-1;if(a.isRegExp(r))return r.test(t)}}function Nn(e){return e.trim().toLowerCase().replace(/([a-z\d])(\w*)/g,(t,n,r)=>n.toUpperCase()+r)}function Ln(e,t){const n=a.toCamelCase(" "+t);["get","set","has"].forEach(r=>{Object.defineProperty(e,r+n,{value:function(o,s,i){return this[r].call(this,t,o,s,i)},configurable:!0})})}class G{constructor(t){t&&this.set(t)}set(t,n,r){const o=this;function s(l,u,d){const c=N(u);if(!c)throw new Error("header name must be a non-empty string");const p=a.findKey(o,c);(!p||o[p]===void 0||d===!0||d===void 0&&o[p]!==!1)&&(o[p||u]=z(l))}const i=(l,u)=>a.forEach(l,(d,c)=>s(d,c,u));return a.isPlainObject(t)||t instanceof this.constructor?i(t,n):a.isString(t)&&(t=t.trim())&&!Pn(t)?i(Cn(t),n):t!=null&&s(n,t,r),this}get(t,n){if(t=N(t),t){const r=a.findKey(this,t);if(r){const o=this[r];if(!n)return o;if(n===!0)return jn(o);if(a.isFunction(n))return n.call(this,o,r);if(a.isRegExp(n))return n.exec(o);throw new TypeError("parser must be boolean|regexp|function")}}}has(t,n){if(t=N(t),t){const r=a.findKey(this,t);return!!(r&&this[r]!==void 0&&(!n||Z(this,this[r],r,n)))}return!1}delete(t,n){const r=this;let o=!1;function s(i){if(i=N(i),i){const l=a.findKey(r,i);l&&(!n||Z(r,r[l],l,n))&&(delete r[l],o=!0)}}return a.isArray(t)?t.forEach(s):s(t),o}clear(t){const n=Object.keys(this);let r=n.length,o=!1;for(;r--;){const s=n[r];(!t||Z(this,this[s],s,t,!0))&&(delete this[s],o=!0)}return o}normalize(t){const n=this,r={};return a.forEach(this,(o,s)=>{const i=a.findKey(r,s);if(i){n[i]=z(o),delete n[s];return}const l=t?Nn(s):String(s).trim();l!==s&&delete n[s],n[l]=z(o),r[l]=!0}),this}concat(...t){return this.constructor.concat(this,...t)}toJSON(t){const n=Object.create(null);return a.forEach(this,(r,o)=>{r!=null&&r!==!1&&(n[o]=t&&a.isArray(r)?r.join(", "):r)}),n}[Symbol.iterator](){return Object.entries(this.toJSON())[Symbol.iterator]()}toString(){return Object.entries(this.toJSON()).map(([t,n])=>t+": "+n).join(`
`)}get[Symbol.toStringTag](){return"AxiosHeaders"}static from(t){return t instanceof this?t:new this(t)}static concat(t,...n){const r=new this(t);return n.forEach(o=>r.set(o)),r}static accessor(t){const n=(this[Ae]=this[Ae]={accessors:{}}).accessors,r=this.prototype;function o(s){const i=N(s);n[i]||(Ln(r,s),n[i]=!0)}return a.isArray(t)?t.forEach(o):o(t),this}}G.accessor(["Content-Type","Content-Length","Accept","Accept-Encoding","User-Agent","Authorization"]);a.freezeMethods(G.prototype);a.freezeMethods(G);const O=G;function ee(e,t){const n=this||le,r=t||n,o=O.from(r.headers);let s=r.data;return a.forEach(e,function(i){s=i.call(n,s,o.normalize(),t?t.status:void 0)}),o.normalize(),s}function Xe(e){return!!(e&&e.__CANCEL__)}function k(e,t,n){y.call(this,e??"canceled",y.ERR_CANCELED,t,n),this.name="CanceledError"}a.inherits(k,y,{__CANCEL__:!0});function Fn(e,t,n){const r=n.config.validateStatus;!n.status||!r||r(n.status)?e(n):t(new y("Request failed with status code "+n.status,[y.ERR_BAD_REQUEST,y.ERR_BAD_RESPONSE][Math.floor(n.status/100)-4],n.config,n.request,n))}const Bn=E.isStandardBrowserEnv?function(){return{write:function(e,t,n,r,o,s){const i=[];i.push(e+"="+encodeURIComponent(t)),a.isNumber(n)&&i.push("expires="+new Date(n).toGMTString()),a.isString(r)&&i.push("path="+r),a.isString(o)&&i.push("domain="+o),s===!0&&i.push("secure"),document.cookie=i.join("; ")},read:function(e){const t=document.cookie.match(new RegExp("(^|;\\s*)("+e+")=([^;]*)"));return t?decodeURIComponent(t[3]):null},remove:function(e){this.write(e,"",Date.now()-864e5)}}}():function(){return{write:function(){},read:function(){return null},remove:function(){}}}();function Un(e){return/^([a-z][a-z\d+\-.]*:)?\/\//i.test(e)}function Dn(e,t){return t?e.replace(/\/+$/,"")+"/"+t.replace(/^\/+/,""):e}function Qe(e,t){return e&&!Un(t)?Dn(e,t):t}const kn=E.isStandardBrowserEnv?function(){const e=/(msie|trident)/i.test(navigator.userAgent),t=document.createElement("a");let n;function r(o){let s=o;return e&&(t.setAttribute("href",s),s=t.href),t.setAttribute("href",s),{href:t.href,protocol:t.protocol?t.protocol.replace(/:$/,""):"",host:t.host,search:t.search?t.search.replace(/^\?/,""):"",hash:t.hash?t.hash.replace(/^#/,""):"",hostname:t.hostname,port:t.port,pathname:t.pathname.charAt(0)==="/"?t.pathname:"/"+t.pathname}}return n=r(window.location.href),function(o){const s=a.isString(o)?r(o):o;return s.protocol===n.protocol&&s.host===n.host}}():function(){return function(){return!0}}();function In(e){const t=/^([-+\w]{1,25})(:?\/\/|:)/.exec(e);return t&&t[1]||""}function qn(e,t){e=e||10;const n=new Array(e),r=new Array(e);let o=0,s=0,i;return t=t!==void 0?t:1e3,function(l){const u=Date.now(),d=r[s];i||(i=u),n[o]=l,r[o]=u;let c=s,p=0;for(;c!==o;)p+=n[c++],c=c%e;if(o=(o+1)%e,o===s&&(s=(s+1)%e),u-i<t)return;const m=d&&u-d;return m?Math.round(p*1e3/m):void 0}}function _e(e,t){let n=0;const r=qn(50,250);return o=>{const s=o.loaded,i=o.lengthComputable?o.total:void 0,l=s-n,u=r(l),d=s<=i;n=s;const c={loaded:s,total:i,progress:i?s/i:void 0,bytes:l,rate:u||void 0,estimated:u&&i&&d?(i-s)/u:void 0,event:o};c[t?"download":"upload"]=!0,e(c)}}const Mn=typeof XMLHttpRequest<"u",zn=Mn&&function(e){return new Promise(function(t,n){let r=e.data;const o=O.from(e.headers).normalize(),s=e.responseType;let i;function l(){e.cancelToken&&e.cancelToken.unsubscribe(i),e.signal&&e.signal.removeEventListener("abort",i)}a.isFormData(r)&&(E.isStandardBrowserEnv||E.isStandardBrowserWebWorkerEnv)&&o.setContentType(!1);let u=new XMLHttpRequest;if(e.auth){const m=e.auth.username||"",h=e.auth.password?unescape(encodeURIComponent(e.auth.password)):"";o.set("Authorization","Basic "+btoa(m+":"+h))}const d=Qe(e.baseURL,e.url);u.open(e.method.toUpperCase(),We(d,e.params,e.paramsSerializer),!0),u.timeout=e.timeout;function c(){if(!u)return;const m=O.from("getAllResponseHeaders"in u&&u.getAllResponseHeaders()),h={data:!s||s==="text"||s==="json"?u.responseText:u.response,status:u.status,statusText:u.statusText,headers:m,config:e,request:u};Fn(function(f){t(f),l()},function(f){n(f),l()},h),u=null}if("onloadend"in u?u.onloadend=c:u.onreadystatechange=function(){!u||u.readyState!==4||u.status===0&&!(u.responseURL&&u.responseURL.indexOf("file:")===0)||setTimeout(c)},u.onabort=function(){u&&(n(new y("Request aborted",y.ECONNABORTED,e,u)),u=null)},u.onerror=function(){n(new y("Network Error",y.ERR_NETWORK,e,u)),u=null},u.ontimeout=function(){let m=e.timeout?"timeout of "+e.timeout+"ms exceeded":"timeout exceeded";const h=e.transitional||Ke;e.timeoutErrorMessage&&(m=e.timeoutErrorMessage),n(new y(m,h.clarifyTimeoutError?y.ETIMEDOUT:y.ECONNABORTED,e,u)),u=null},E.isStandardBrowserEnv){const m=(e.withCredentials||kn(d))&&e.xsrfCookieName&&Bn.read(e.xsrfCookieName);m&&o.set(e.xsrfHeaderName,m)}r===void 0&&o.setContentType(null),"setRequestHeader"in u&&a.forEach(o.toJSON(),function(m,h){u.setRequestHeader(h,m)}),a.isUndefined(e.withCredentials)||(u.withCredentials=!!e.withCredentials),s&&s!=="json"&&(u.responseType=e.responseType),typeof e.onDownloadProgress=="function"&&u.addEventListener("progress",_e(e.onDownloadProgress,!0)),typeof e.onUploadProgress=="function"&&u.upload&&u.upload.addEventListener("progress",_e(e.onUploadProgress)),(e.cancelToken||e.signal)&&(i=m=>{u&&(n(!m||m.type?new k(null,e,u):m),u.abort(),u=null)},e.cancelToken&&e.cancelToken.subscribe(i),e.signal&&(e.signal.aborted?i():e.signal.addEventListener("abort",i)));const p=In(d);if(p&&E.protocols.indexOf(p)===-1){n(new y("Unsupported protocol "+p+":",y.ERR_BAD_REQUEST,e));return}u.send(r||null)})},V={http:pn,xhr:zn};a.forEach(V,(e,t)=>{if(e){try{Object.defineProperty(e,"name",{value:t})}catch{}Object.defineProperty(e,"adapterName",{value:t})}});const Vn={getAdapter:e=>{e=a.isArray(e)?e:[e];const{length:t}=e;let n,r;for(let o=0;o<t&&(n=e[o],!(r=a.isString(n)?V[n.toLowerCase()]:n));o++);if(!r)throw r===!1?new y(`Adapter ${n} is not supported by the environment`,"ERR_NOT_SUPPORT"):new Error(a.hasOwnProp(V,n)?`Adapter '${n}' is not available in the build`:`Unknown adapter '${n}'`);if(!a.isFunction(r))throw new TypeError("adapter is not a function");return r},adapters:V};function te(e){if(e.cancelToken&&e.cancelToken.throwIfRequested(),e.signal&&e.signal.aborted)throw new k(null,e)}function Te(e){return te(e),e.headers=O.from(e.headers),e.data=ee.call(e,e.transformRequest),["post","put","patch"].indexOf(e.method)!==-1&&e.headers.setContentType("application/x-www-form-urlencoded",!1),Vn.getAdapter(e.adapter||le.adapter)(e).then(function(t){return te(e),t.data=ee.call(e,e.transformResponse,t),t.headers=O.from(t.headers),t},function(t){return Xe(t)||(te(e),t&&t.response&&(t.response.data=ee.call(e,e.transformResponse,t.response),t.response.headers=O.from(t.response.headers))),Promise.reject(t)})}const xe=e=>e instanceof O?e.toJSON():e;function T(e,t){t=t||{};const n={};function r(d,c,p){return a.isPlainObject(d)&&a.isPlainObject(c)?a.merge.call({caseless:p},d,c):a.isPlainObject(c)?a.merge({},c):a.isArray(c)?c.slice():c}function o(d,c,p){if(a.isUndefined(c)){if(!a.isUndefined(d))return r(void 0,d,p)}else return r(d,c,p)}function s(d,c){if(!a.isUndefined(c))return r(void 0,c)}function i(d,c){if(a.isUndefined(c)){if(!a.isUndefined(d))return r(void 0,d)}else return r(void 0,c)}function l(d,c,p){if(p in t)return r(d,c);if(p in e)return r(void 0,d)}const u={url:s,method:s,data:s,baseURL:i,transformRequest:i,transformResponse:i,paramsSerializer:i,timeout:i,timeoutMessage:i,withCredentials:i,adapter:i,responseType:i,xsrfCookieName:i,xsrfHeaderName:i,onUploadProgress:i,onDownloadProgress:i,decompress:i,maxContentLength:i,maxBodyLength:i,beforeRedirect:i,transport:i,httpAgent:i,httpsAgent:i,cancelToken:i,socketPath:i,responseEncoding:i,validateStatus:l,headers:(d,c)=>o(xe(d),xe(c),!0)};return a.forEach(Object.keys(e).concat(Object.keys(t)),function(d){const c=u[d]||o,p=c(e[d],t[d],d);a.isUndefined(p)&&c!==l||(n[d]=p)}),n}const Ye="1.3.4",fe={};["object","boolean","number","function","string","symbol"].forEach((e,t)=>{fe[e]=function(n){return typeof n===e||"a"+(t<1?"n ":" ")+e}});const Ce={};fe.transitional=function(e,t,n){function r(o,s){return"[Axios v"+Ye+"] Transitional option '"+o+"'"+s+(n?". "+n:"")}return(o,s,i)=>{if(e===!1)throw new y(r(s," has been removed"+(t?" in "+t:"")),y.ERR_DEPRECATED);return t&&!Ce[s]&&(Ce[s]=!0,console.warn(r(s," has been deprecated since v"+t+" and will be removed in the near future"))),e?e(o,s,i):!0}};function Hn(e,t,n){if(typeof e!="object")throw new y("options must be an object",y.ERR_BAD_OPTION_VALUE);const r=Object.keys(e);let o=r.length;for(;o-- >0;){const s=r[o],i=t[s];if(i){const l=e[s],u=l===void 0||i(l,s,e);if(u!==!0)throw new y("option "+s+" must be "+u,y.ERR_BAD_OPTION_VALUE);continue}if(n!==!0)throw new y("Unknown option "+s,y.ERR_BAD_OPTION)}}const oe={assertOptions:Hn,validators:fe},R=oe.validators;class J{constructor(t){this.defaults=t,this.interceptors={request:new Se,response:new Se}}request(t,n){typeof t=="string"?(n=n||{},n.url=t):n=t||{},n=T(this.defaults,n);const{transitional:r,paramsSerializer:o,headers:s}=n;r!==void 0&&oe.assertOptions(r,{silentJSONParsing:R.transitional(R.boolean),forcedJSONParsing:R.transitional(R.boolean),clarifyTimeoutError:R.transitional(R.boolean)},!1),o!==void 0&&oe.assertOptions(o,{encode:R.function,serialize:R.function},!0),n.method=(n.method||this.defaults.method||"get").toLowerCase();let i;i=s&&a.merge(s.common,s[n.method]),i&&a.forEach(["delete","get","head","post","put","patch","common"],f=>{delete s[f]}),n.headers=O.concat(i,s);const l=[];let u=!0;this.interceptors.request.forEach(function(f){typeof f.runWhen=="function"&&f.runWhen(n)===!1||(u=u&&f.synchronous,l.unshift(f.fulfilled,f.rejected))});const d=[];this.interceptors.response.forEach(function(f){d.push(f.fulfilled,f.rejected)});let c,p=0,m;if(!u){const f=[Te.bind(this),void 0];for(f.unshift.apply(f,l),f.push.apply(f,d),m=f.length,c=Promise.resolve(n);p<m;)c=c.then(f[p++],f[p++]);return c}m=l.length;let h=n;for(p=0;p<m;){const f=l[p++],w=l[p++];try{h=f(h)}catch(b){w.call(this,b);break}}try{c=Te.call(this,h)}catch(f){return Promise.reject(f)}for(p=0,m=d.length;p<m;)c=c.then(d[p++],d[p++]);return c}getUri(t){t=T(this.defaults,t);const n=Qe(t.baseURL,t.url);return We(n,t.params,t.paramsSerializer)}}a.forEach(["delete","get","head","options"],function(e){J.prototype[e]=function(t,n){return this.request(T(n||{},{method:e,url:t,data:(n||{}).data}))}});a.forEach(["post","put","patch"],function(e){function t(n){return function(r,o,s){return this.request(T(s||{},{method:e,headers:n?{"Content-Type":"multipart/form-data"}:{},url:r,data:o}))}}J.prototype[e]=t(),J.prototype[e+"Form"]=t(!0)});const H=J;class de{constructor(t){if(typeof t!="function")throw new TypeError("executor must be a function.");let n;this.promise=new Promise(function(o){n=o});const r=this;this.promise.then(o=>{if(!r._listeners)return;let s=r._listeners.length;for(;s-- >0;)r._listeners[s](o);r._listeners=null}),this.promise.then=o=>{let s;const i=new Promise(l=>{r.subscribe(l),s=l}).then(o);return i.cancel=function(){r.unsubscribe(s)},i},t(function(o,s,i){r.reason||(r.reason=new k(o,s,i),n(r.reason))})}throwIfRequested(){if(this.reason)throw this.reason}subscribe(t){if(this.reason){t(this.reason);return}this._listeners?this._listeners.push(t):this._listeners=[t]}unsubscribe(t){if(!this._listeners)return;const n=this._listeners.indexOf(t);n!==-1&&this._listeners.splice(n,1)}static source(){let t;return{token:new de(function(n){t=n}),cancel:t}}}const Jn=de;function $n(e){return function(t){return e.apply(null,t)}}function Wn(e){return a.isObject(e)&&e.isAxiosError===!0}const se={Continue:100,SwitchingProtocols:101,Processing:102,EarlyHints:103,Ok:200,Created:201,Accepted:202,NonAuthoritativeInformation:203,NoContent:204,ResetContent:205,PartialContent:206,MultiStatus:207,AlreadyReported:208,ImUsed:226,MultipleChoices:300,MovedPermanently:301,Found:302,SeeOther:303,NotModified:304,UseProxy:305,Unused:306,TemporaryRedirect:307,PermanentRedirect:308,BadRequest:400,Unauthorized:401,PaymentRequired:402,Forbidden:403,NotFound:404,MethodNotAllowed:405,NotAcceptable:406,ProxyAuthenticationRequired:407,RequestTimeout:408,Conflict:409,Gone:410,LengthRequired:411,PreconditionFailed:412,PayloadTooLarge:413,UriTooLong:414,UnsupportedMediaType:415,RangeNotSatisfiable:416,ExpectationFailed:417,ImATeapot:418,MisdirectedRequest:421,UnprocessableEntity:422,Locked:423,FailedDependency:424,TooEarly:425,UpgradeRequired:426,PreconditionRequired:428,TooManyRequests:429,RequestHeaderFieldsTooLarge:431,UnavailableForLegalReasons:451,InternalServerError:500,NotImplemented:501,BadGateway:502,ServiceUnavailable:503,GatewayTimeout:504,HttpVersionNotSupported:505,VariantAlsoNegotiates:506,InsufficientStorage:507,LoopDetected:508,NotExtended:510,NetworkAuthenticationRequired:511};Object.entries(se).forEach(([e,t])=>{se[t]=e});const Kn=se;function Ze(e){const t=new H(e),n=Fe(H.prototype.request,t);return a.extend(n,H.prototype,t,{allOwnKeys:!0}),a.extend(n,t,null,{allOwnKeys:!0}),n.create=function(r){return Ze(T(e,r))},n}const g=Ze(le);g.Axios=H;g.CanceledError=k;g.CancelToken=Jn;g.isCancel=Xe;g.VERSION=Ye;g.toFormData=W;g.AxiosError=y;g.Cancel=g.CanceledError;g.all=function(e){return Promise.all(e)};g.spread=$n;g.isAxiosError=Wn;g.mergeConfig=T;g.AxiosHeaders=O;g.formToJSON=e=>Ge(a.isHTMLForm(e)?new FormData(e):e);g.HttpStatusCode=Kn;g.default=g;const Gn=g,Xn={class:"file-viewer"},Qn={class:"name"},Yn={key:0,class:"content loading"},Zn=je({__name:"FileViewer",props:{file:null,url:null},setup(e){const t=e,n=P(!1),r=P(""),o=P(""),s=P(""),i=P();return(()=>{const l={loading:"正在加载中，请耐心等待...",reading:"正在努力解析文件...",errorLoading:c=>`加载文件异常：${c}`,errorReading:c=>`读取文件异常：${c}`};let u;const d={async loadFromUrl(){const{url:c}=t;if(!c)return;this.startLoading(l.loading);const p=c.substring(c.lastIndexOf("/")+1);try{const{data:m}=await Gn({url:c,method:"get",responseType:"blob"});if(!m)return this.showError("文件下载失败");const h=this.wrap(m,p);return this.resolveFile(h)}catch(m){this.showError(l.errorLoading(m))}finally{this.endLoading()}},wrap(c,p){if(c instanceof File)return c;if(c instanceof Blob&&p)return new File([c],p,{});if(c instanceof ArrayBuffer)return this.wrap(new Blob([c]));throw new Error("不支持的文件类型格式！")},async resolveFile(c){n.value&&this.endLoading();const p=this.wrap(c);this.startLoading(l.reading);try{s.value=p.name&&decodeURIComponent(p.name)||"";const m=await Ot(p);m instanceof ArrayBuffer&&(u=await this.displayResult(m,p))}catch(m){console.error(m),this.showError(l.errorReading(m))}finally{this.endLoading()}},displayResult(c,p){const{name:m}=p,h=Lt(m),f=i.value;if(!f)return Promise.resolve();u&&(f.lastChild&&f.removeChild(f.lastChild),u.unmount());const w=document.createElement("div");w.className="file-render";const b=f.appendChild(w);return new Promise((j,X)=>Ft(c,h,b).then(j).catch(X))},showError(c){r.value=c},startLoading(c){n.value=!0,o.value=c,r.value=""},endLoading(){n.value=!1,o.value=""}};nt(()=>{t.file&&d.resolveFile(t.file),d.loadFromUrl()}),me(()=>t.url,()=>d.loadFromUrl()),me(()=>t.file,c=>c&&d.resolveFile(c))})(),(l,u)=>(L(),F("div",Xn,[Q("div",Qn,q(s.value),1),r.value?(L(),F("div",Yn,q(r.value),1)):(L(),F(rt,{key:1},[ye(Q("div",{class:"content loading"},q(o.value),513),[[ge,n.value]]),ye(Q("div",{class:"content",ref_key:"output",ref:i},null,512),[[ge,!n.value]])],64))]))}}),er=Ne(Zn,[["__scopeId","data-v-4732f9f2"]]),tr=[["file-viewer",er]];class nr{constructor(){pt(this,"installed",!1)}install(t){this.installed||(tr.forEach(([n,r])=>t.component(n,r)),this.installed=!0)}}const rr=new nr,pe=_(lt);pe.config.globalProperties.$TOOL=ot;pe.use(st).use(rr).use(it);pe.mount("#app");export{Ne as F};
function __vite__mapDeps(indexes) {
  if (!__vite__mapDeps.viteFileDeps) {
    __vite__mapDeps.viteFileDeps = ["./docx-preview.min-WXON4Pds.js","./_commonjsHelpers-SRRim4kG.js","./_commonjs-dynamic-modules-OUSn7nVQ.js","./PptxRender-QbqKjK9B.js","./display-_0nm5nf0.js","./display-L0PJm20d.css","./worker-ref-CHNAV_Da.js","./XlsxTable-nO5epM5Q.js","./PdfView-yB-sN4oQ.js","./ImageViewer-_DjVnkB6.js","./MarkdownViewer-tTVCagFM.js"]
  }
  return indexes.map((i) => __vite__mapDeps.viteFileDeps[i])
}
