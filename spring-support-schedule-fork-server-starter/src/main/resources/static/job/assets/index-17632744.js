import{_ as me,o as ve,c as we,g as nt,p as be,k as ye}from"./index-bd432aa0.js";/*!
 * Cropper.js v1.5.13
 * https://fengyuanchen.github.io/cropperjs
 *
 * Copyright 2015-present Chen Fengyuan
 * Released under the MIT license
 *
 * Date: 2022-11-20T05:30:46.114Z
 */function Pt(a,t){var i=Object.keys(a);if(Object.getOwnPropertySymbols){var e=Object.getOwnPropertySymbols(a);t&&(e=e.filter(function(n){return Object.getOwnPropertyDescriptor(a,n).enumerable})),i.push.apply(i,e)}return i}function Jt(a){for(var t=1;t<arguments.length;t++){var i=arguments[t]!=null?arguments[t]:{};t%2?Pt(Object(i),!0).forEach(function(e){Ee(a,e,i[e])}):Object.getOwnPropertyDescriptors?Object.defineProperties(a,Object.getOwnPropertyDescriptors(i)):Pt(Object(i)).forEach(function(e){Object.defineProperty(a,e,Object.getOwnPropertyDescriptor(i,e))})}return a}function wt(a){"@babel/helpers - typeof";return wt=typeof Symbol=="function"&&typeof Symbol.iterator=="symbol"?function(t){return typeof t}:function(t){return t&&typeof Symbol=="function"&&t.constructor===Symbol&&t!==Symbol.prototype?"symbol":typeof t},wt(a)}function xe(a,t){if(!(a instanceof t))throw new TypeError("Cannot call a class as a function")}function Yt(a,t){for(var i=0;i<t.length;i++){var e=t[i];e.enumerable=e.enumerable||!1,e.configurable=!0,"value"in e&&(e.writable=!0),Object.defineProperty(a,e.key,e)}}function De(a,t,i){return t&&Yt(a.prototype,t),i&&Yt(a,i),Object.defineProperty(a,"prototype",{writable:!1}),a}function Ee(a,t,i){return t in a?Object.defineProperty(a,t,{value:i,enumerable:!0,configurable:!0,writable:!0}):a[t]=i,a}function te(a){return Me(a)||Ce(a)||Te(a)||Oe()}function Me(a){if(Array.isArray(a))return bt(a)}function Ce(a){if(typeof Symbol<"u"&&a[Symbol.iterator]!=null||a["@@iterator"]!=null)return Array.from(a)}function Te(a,t){if(a){if(typeof a=="string")return bt(a,t);var i=Object.prototype.toString.call(a).slice(8,-1);if(i==="Object"&&a.constructor&&(i=a.constructor.name),i==="Map"||i==="Set")return Array.from(a);if(i==="Arguments"||/^(?:Ui|I)nt(?:8|16|32)(?:Clamped)?Array$/.test(i))return bt(a,t)}}function bt(a,t){(t==null||t>a.length)&&(t=a.length);for(var i=0,e=new Array(t);i<t;i++)e[i]=a[i];return e}function Oe(){throw new TypeError(`Invalid attempt to spread non-iterable instance.
In order to be iterable, non-array objects must have a [Symbol.iterator]() method.`)}var dt=typeof window<"u"&&typeof window.document<"u",P=dt?window:{},Nt=dt&&P.document.documentElement?"ontouchstart"in P.document.documentElement:!1,_t=dt?"PointerEvent"in P:!1,y="cropper",At="all",ee="crop",ie="move",ae="zoom",G="e",q="w",Q="s",H="n",et="ne",it="nw",at="se",rt="sw",yt="".concat(y,"-crop"),Xt="".concat(y,"-disabled"),A="".concat(y,"-hidden"),zt="".concat(y,"-hide"),Ne="".concat(y,"-invisible"),ft="".concat(y,"-modal"),xt="".concat(y,"-move"),st="".concat(y,"Action"),lt="".concat(y,"Preview"),Rt="crop",re="move",ne="none",Dt="crop",Et="cropend",Mt="cropmove",Ct="cropstart",Ht="dblclick",_e=Nt?"touchstart":"mousedown",Ae=Nt?"touchmove":"mousemove",Re=Nt?"touchend touchcancel":"mouseup",Wt=_t?"pointerdown":_e,Ut=_t?"pointermove":Ae,jt=_t?"pointerup pointercancel":Re,Vt="ready",$t="resize",Gt="wheel",Tt="zoom",qt="image/jpeg",Se=/^e|w|s|n|se|sw|ne|nw|all|crop|move|zoom$/,Ie=/^data:/,Be=/^data:image\/jpeg;base64,/,Le=/^img|canvas$/i,oe=200,se=100,Ft={viewMode:0,dragMode:Rt,initialAspectRatio:NaN,aspectRatio:NaN,data:null,preview:"",responsive:!0,restore:!0,checkCrossOrigin:!0,checkOrientation:!0,modal:!0,guides:!0,center:!0,highlight:!0,background:!0,autoCrop:!0,autoCropArea:.8,movable:!0,rotatable:!0,scalable:!0,zoomable:!0,zoomOnTouch:!0,zoomOnWheel:!0,wheelZoomRatio:.1,cropBoxMovable:!0,cropBoxResizable:!0,toggleDragModeOnDblclick:!0,minCanvasWidth:0,minCanvasHeight:0,minCropBoxWidth:0,minCropBoxHeight:0,minContainerWidth:oe,minContainerHeight:se,ready:null,cropstart:null,cropmove:null,cropend:null,crop:null,zoom:null},ke='<div class="cropper-container" touch-action="none"><div class="cropper-wrap-box"><div class="cropper-canvas"></div></div><div class="cropper-drag-box"></div><div class="cropper-crop-box"><span class="cropper-view-box"></span><span class="cropper-dashed dashed-h"></span><span class="cropper-dashed dashed-v"></span><span class="cropper-center"></span><span class="cropper-face"></span><span class="cropper-line line-e" data-cropper-action="e"></span><span class="cropper-line line-n" data-cropper-action="n"></span><span class="cropper-line line-w" data-cropper-action="w"></span><span class="cropper-line line-s" data-cropper-action="s"></span><span class="cropper-point point-e" data-cropper-action="e"></span><span class="cropper-point point-n" data-cropper-action="n"></span><span class="cropper-point point-w" data-cropper-action="w"></span><span class="cropper-point point-s" data-cropper-action="s"></span><span class="cropper-point point-ne" data-cropper-action="ne"></span><span class="cropper-point point-nw" data-cropper-action="nw"></span><span class="cropper-point point-sw" data-cropper-action="sw"></span><span class="cropper-point point-se" data-cropper-action="se"></span></div></div>',Pe=Number.isNaN||P.isNaN;function u(a){return typeof a=="number"&&!Pe(a)}var Qt=function(t){return t>0&&t<1/0};function mt(a){return typeof a>"u"}function F(a){return wt(a)==="object"&&a!==null}var Ye=Object.prototype.hasOwnProperty;function Z(a){if(!F(a))return!1;try{var t=a.constructor,i=t.prototype;return t&&i&&Ye.call(i,"isPrototypeOf")}catch{return!1}}function _(a){return typeof a=="function"}var Xe=Array.prototype.slice;function he(a){return Array.from?Array.from(a):Xe.call(a)}function E(a,t){return a&&_(t)&&(Array.isArray(a)||u(a.length)?he(a).forEach(function(i,e){t.call(a,i,e,a)}):F(a)&&Object.keys(a).forEach(function(i){t.call(a,a[i],i,a)})),a}var x=Object.assign||function(t){for(var i=arguments.length,e=new Array(i>1?i-1:0),n=1;n<i;n++)e[n-1]=arguments[n];return F(t)&&e.length>0&&e.forEach(function(r){F(r)&&Object.keys(r).forEach(function(o){t[o]=r[o]})}),t},ze=/\.\d*(?:0|9){12}\d*$/;function J(a){var t=arguments.length>1&&arguments[1]!==void 0?arguments[1]:1e11;return ze.test(a)?Math.round(a*t)/t:a}var He=/^width|height|left|top|marginLeft|marginTop$/;function W(a,t){var i=a.style;E(t,function(e,n){He.test(n)&&u(e)&&(e="".concat(e,"px")),i[n]=e})}function We(a,t){return a.classList?a.classList.contains(t):a.className.indexOf(t)>-1}function O(a,t){if(t){if(u(a.length)){E(a,function(e){O(e,t)});return}if(a.classList){a.classList.add(t);return}var i=a.className.trim();i?i.indexOf(t)<0&&(a.className="".concat(i," ").concat(t)):a.className=t}}function k(a,t){if(t){if(u(a.length)){E(a,function(i){k(i,t)});return}if(a.classList){a.classList.remove(t);return}a.className.indexOf(t)>=0&&(a.className=a.className.replace(t,""))}}function K(a,t,i){if(t){if(u(a.length)){E(a,function(e){K(e,t,i)});return}i?O(a,t):k(a,t)}}var Ue=/([a-z\d])([A-Z])/g;function St(a){return a.replace(Ue,"$1-$2").toLowerCase()}function Ot(a,t){return F(a[t])?a[t]:a.dataset?a.dataset[t]:a.getAttribute("data-".concat(St(t)))}function ht(a,t,i){F(i)?a[t]=i:a.dataset?a.dataset[t]=i:a.setAttribute("data-".concat(St(t)),i)}function je(a,t){if(F(a[t]))try{delete a[t]}catch{a[t]=void 0}else if(a.dataset)try{delete a.dataset[t]}catch{a.dataset[t]=void 0}else a.removeAttribute("data-".concat(St(t)))}var ce=/\s\s*/,le=function(){var a=!1;if(dt){var t=!1,i=function(){},e=Object.defineProperty({},"once",{get:function(){return a=!0,t},set:function(r){t=r}});P.addEventListener("test",i,e),P.removeEventListener("test",i,e)}return a}();function B(a,t,i){var e=arguments.length>3&&arguments[3]!==void 0?arguments[3]:{},n=i;t.trim().split(ce).forEach(function(r){if(!le){var o=a.listeners;o&&o[r]&&o[r][i]&&(n=o[r][i],delete o[r][i],Object.keys(o[r]).length===0&&delete o[r],Object.keys(o).length===0&&delete a.listeners)}a.removeEventListener(r,n,e)})}function I(a,t,i){var e=arguments.length>3&&arguments[3]!==void 0?arguments[3]:{},n=i;t.trim().split(ce).forEach(function(r){if(e.once&&!le){var o=a.listeners,s=o===void 0?{}:o;n=function(){delete s[r][i],a.removeEventListener(r,n,e);for(var l=arguments.length,h=new Array(l),c=0;c<l;c++)h[c]=arguments[c];i.apply(a,h)},s[r]||(s[r]={}),s[r][i]&&a.removeEventListener(r,s[r][i],e),s[r][i]=n,a.listeners=s}a.addEventListener(r,n,e)})}function tt(a,t,i){var e;return _(Event)&&_(CustomEvent)?e=new CustomEvent(t,{detail:i,bubbles:!0,cancelable:!0}):(e=document.createEvent("CustomEvent"),e.initCustomEvent(t,!0,!0,i)),a.dispatchEvent(e)}function pe(a){var t=a.getBoundingClientRect();return{left:t.left+(window.pageXOffset-document.documentElement.clientLeft),top:t.top+(window.pageYOffset-document.documentElement.clientTop)}}var vt=P.location,Ve=/^(\w+:)\/\/([^:/?#]*):?(\d*)/i;function Zt(a){var t=a.match(Ve);return t!==null&&(t[1]!==vt.protocol||t[2]!==vt.hostname||t[3]!==vt.port)}function Kt(a){var t="timestamp=".concat(new Date().getTime());return a+(a.indexOf("?")===-1?"?":"&")+t}function ot(a){var t=a.rotate,i=a.scaleX,e=a.scaleY,n=a.translateX,r=a.translateY,o=[];u(n)&&n!==0&&o.push("translateX(".concat(n,"px)")),u(r)&&r!==0&&o.push("translateY(".concat(r,"px)")),u(t)&&t!==0&&o.push("rotate(".concat(t,"deg)")),u(i)&&i!==1&&o.push("scaleX(".concat(i,")")),u(e)&&e!==1&&o.push("scaleY(".concat(e,")"));var s=o.length?o.join(" "):"none";return{WebkitTransform:s,msTransform:s,transform:s}}function $e(a){var t=Jt({},a),i=0;return E(a,function(e,n){delete t[n],E(t,function(r){var o=Math.abs(e.startX-r.startX),s=Math.abs(e.startY-r.startY),f=Math.abs(e.endX-r.endX),l=Math.abs(e.endY-r.endY),h=Math.sqrt(o*o+s*s),c=Math.sqrt(f*f+l*l),p=(c-h)/h;Math.abs(p)>Math.abs(i)&&(i=p)})}),i}function pt(a,t){var i=a.pageX,e=a.pageY,n={endX:i,endY:e};return t?n:Jt({startX:i,startY:e},n)}function Ge(a){var t=0,i=0,e=0;return E(a,function(n){var r=n.startX,o=n.startY;t+=r,i+=o,e+=1}),t/=e,i/=e,{pageX:t,pageY:i}}function U(a){var t=a.aspectRatio,i=a.height,e=a.width,n=arguments.length>1&&arguments[1]!==void 0?arguments[1]:"contain",r=Qt(e),o=Qt(i);if(r&&o){var s=i*t;n==="contain"&&s>e||n==="cover"&&s<e?i=e/t:e=i*t}else r?i=e/t:o&&(e=i*t);return{width:e,height:i}}function qe(a){var t=a.width,i=a.height,e=a.degree;if(e=Math.abs(e)%180,e===90)return{width:i,height:t};var n=e%90*Math.PI/180,r=Math.sin(n),o=Math.cos(n),s=t*o+i*r,f=t*r+i*o;return e>90?{width:f,height:s}:{width:s,height:f}}function Fe(a,t,i,e){var n=t.aspectRatio,r=t.naturalWidth,o=t.naturalHeight,s=t.rotate,f=s===void 0?0:s,l=t.scaleX,h=l===void 0?1:l,c=t.scaleY,p=c===void 0?1:c,m=i.aspectRatio,g=i.naturalWidth,b=i.naturalHeight,v=e.fillColor,M=v===void 0?"transparent":v,T=e.imageSmoothingEnabled,D=T===void 0?!0:T,Y=e.imageSmoothingQuality,R=Y===void 0?"low":Y,d=e.maxWidth,w=d===void 0?1/0:d,C=e.maxHeight,S=C===void 0?1/0:C,X=e.minWidth,j=X===void 0?0:X,V=e.minHeight,z=V===void 0?0:V,L=document.createElement("canvas"),N=L.getContext("2d"),$=U({aspectRatio:m,width:w,height:S}),ct=U({aspectRatio:m,width:j,height:z},"cover"),ut=Math.min($.width,Math.max(ct.width,g)),gt=Math.min($.height,Math.max(ct.height,b)),It=U({aspectRatio:n,width:w,height:S}),Bt=U({aspectRatio:n,width:j,height:z},"cover"),Lt=Math.min(It.width,Math.max(Bt.width,r)),kt=Math.min(It.height,Math.max(Bt.height,o)),ue=[-Lt/2,-kt/2,Lt,kt];return L.width=J(ut),L.height=J(gt),N.fillStyle=M,N.fillRect(0,0,ut,gt),N.save(),N.translate(ut/2,gt/2),N.rotate(f*Math.PI/180),N.scale(h,p),N.imageSmoothingEnabled=D,N.imageSmoothingQuality=R,N.drawImage.apply(N,[a].concat(te(ue.map(function(ge){return Math.floor(J(ge))})))),N.restore(),L}var fe=String.fromCharCode;function Qe(a,t,i){var e="";i+=t;for(var n=t;n<i;n+=1)e+=fe(a.getUint8(n));return e}var Ze=/^data:.*,/;function Ke(a){var t=a.replace(Ze,""),i=atob(t),e=new ArrayBuffer(i.length),n=new Uint8Array(e);return E(n,function(r,o){n[o]=i.charCodeAt(o)}),e}function Je(a,t){for(var i=[],e=8192,n=new Uint8Array(a);n.length>0;)i.push(fe.apply(null,he(n.subarray(0,e)))),n=n.subarray(e);return"data:".concat(t,";base64,").concat(btoa(i.join("")))}function ti(a){var t=new DataView(a),i;try{var e,n,r;if(t.getUint8(0)===255&&t.getUint8(1)===216)for(var o=t.byteLength,s=2;s+1<o;){if(t.getUint8(s)===255&&t.getUint8(s+1)===225){n=s;break}s+=1}if(n){var f=n+4,l=n+10;if(Qe(t,f,4)==="Exif"){var h=t.getUint16(l);if(e=h===18761,(e||h===19789)&&t.getUint16(l+2,e)===42){var c=t.getUint32(l+4,e);c>=8&&(r=l+c)}}}if(r){var p=t.getUint16(r,e),m,g;for(g=0;g<p;g+=1)if(m=r+g*12+2,t.getUint16(m,e)===274){m+=8,i=t.getUint16(m,e),t.setUint16(m,1,e);break}}}catch{i=1}return i}function ei(a){var t=0,i=1,e=1;switch(a){case 2:i=-1;break;case 3:t=-180;break;case 4:e=-1;break;case 5:t=90,e=-1;break;case 6:t=90;break;case 7:t=90,i=-1;break;case 8:t=-90;break}return{rotate:t,scaleX:i,scaleY:e}}var ii={render:function(){this.initContainer(),this.initCanvas(),this.initCropBox(),this.renderCanvas(),this.cropped&&this.renderCropBox()},initContainer:function(){var t=this.element,i=this.options,e=this.container,n=this.cropper,r=Number(i.minContainerWidth),o=Number(i.minContainerHeight);O(n,A),k(t,A);var s={width:Math.max(e.offsetWidth,r>=0?r:oe),height:Math.max(e.offsetHeight,o>=0?o:se)};this.containerData=s,W(n,{width:s.width,height:s.height}),O(t,A),k(n,A)},initCanvas:function(){var t=this.containerData,i=this.imageData,e=this.options.viewMode,n=Math.abs(i.rotate)%180===90,r=n?i.naturalHeight:i.naturalWidth,o=n?i.naturalWidth:i.naturalHeight,s=r/o,f=t.width,l=t.height;t.height*s>t.width?e===3?f=t.height*s:l=t.width/s:e===3?l=t.width/s:f=t.height*s;var h={aspectRatio:s,naturalWidth:r,naturalHeight:o,width:f,height:l};this.canvasData=h,this.limited=e===1||e===2,this.limitCanvas(!0,!0),h.width=Math.min(Math.max(h.width,h.minWidth),h.maxWidth),h.height=Math.min(Math.max(h.height,h.minHeight),h.maxHeight),h.left=(t.width-h.width)/2,h.top=(t.height-h.height)/2,h.oldLeft=h.left,h.oldTop=h.top,this.initialCanvasData=x({},h)},limitCanvas:function(t,i){var e=this.options,n=this.containerData,r=this.canvasData,o=this.cropBoxData,s=e.viewMode,f=r.aspectRatio,l=this.cropped&&o;if(t){var h=Number(e.minCanvasWidth)||0,c=Number(e.minCanvasHeight)||0;s>1?(h=Math.max(h,n.width),c=Math.max(c,n.height),s===3&&(c*f>h?h=c*f:c=h/f)):s>0&&(h?h=Math.max(h,l?o.width:0):c?c=Math.max(c,l?o.height:0):l&&(h=o.width,c=o.height,c*f>h?h=c*f:c=h/f));var p=U({aspectRatio:f,width:h,height:c});h=p.width,c=p.height,r.minWidth=h,r.minHeight=c,r.maxWidth=1/0,r.maxHeight=1/0}if(i)if(s>(l?0:1)){var m=n.width-r.width,g=n.height-r.height;r.minLeft=Math.min(0,m),r.minTop=Math.min(0,g),r.maxLeft=Math.max(0,m),r.maxTop=Math.max(0,g),l&&this.limited&&(r.minLeft=Math.min(o.left,o.left+(o.width-r.width)),r.minTop=Math.min(o.top,o.top+(o.height-r.height)),r.maxLeft=o.left,r.maxTop=o.top,s===2&&(r.width>=n.width&&(r.minLeft=Math.min(0,m),r.maxLeft=Math.max(0,m)),r.height>=n.height&&(r.minTop=Math.min(0,g),r.maxTop=Math.max(0,g))))}else r.minLeft=-r.width,r.minTop=-r.height,r.maxLeft=n.width,r.maxTop=n.height},renderCanvas:function(t,i){var e=this.canvasData,n=this.imageData;if(i){var r=qe({width:n.naturalWidth*Math.abs(n.scaleX||1),height:n.naturalHeight*Math.abs(n.scaleY||1),degree:n.rotate||0}),o=r.width,s=r.height,f=e.width*(o/e.naturalWidth),l=e.height*(s/e.naturalHeight);e.left-=(f-e.width)/2,e.top-=(l-e.height)/2,e.width=f,e.height=l,e.aspectRatio=o/s,e.naturalWidth=o,e.naturalHeight=s,this.limitCanvas(!0,!1)}(e.width>e.maxWidth||e.width<e.minWidth)&&(e.left=e.oldLeft),(e.height>e.maxHeight||e.height<e.minHeight)&&(e.top=e.oldTop),e.width=Math.min(Math.max(e.width,e.minWidth),e.maxWidth),e.height=Math.min(Math.max(e.height,e.minHeight),e.maxHeight),this.limitCanvas(!1,!0),e.left=Math.min(Math.max(e.left,e.minLeft),e.maxLeft),e.top=Math.min(Math.max(e.top,e.minTop),e.maxTop),e.oldLeft=e.left,e.oldTop=e.top,W(this.canvas,x({width:e.width,height:e.height},ot({translateX:e.left,translateY:e.top}))),this.renderImage(t),this.cropped&&this.limited&&this.limitCropBox(!0,!0)},renderImage:function(t){var i=this.canvasData,e=this.imageData,n=e.naturalWidth*(i.width/i.naturalWidth),r=e.naturalHeight*(i.height/i.naturalHeight);x(e,{width:n,height:r,left:(i.width-n)/2,top:(i.height-r)/2}),W(this.image,x({width:e.width,height:e.height},ot(x({translateX:e.left,translateY:e.top},e)))),t&&this.output()},initCropBox:function(){var t=this.options,i=this.canvasData,e=t.aspectRatio||t.initialAspectRatio,n=Number(t.autoCropArea)||.8,r={width:i.width,height:i.height};e&&(i.height*e>i.width?r.height=r.width/e:r.width=r.height*e),this.cropBoxData=r,this.limitCropBox(!0,!0),r.width=Math.min(Math.max(r.width,r.minWidth),r.maxWidth),r.height=Math.min(Math.max(r.height,r.minHeight),r.maxHeight),r.width=Math.max(r.minWidth,r.width*n),r.height=Math.max(r.minHeight,r.height*n),r.left=i.left+(i.width-r.width)/2,r.top=i.top+(i.height-r.height)/2,r.oldLeft=r.left,r.oldTop=r.top,this.initialCropBoxData=x({},r)},limitCropBox:function(t,i){var e=this.options,n=this.containerData,r=this.canvasData,o=this.cropBoxData,s=this.limited,f=e.aspectRatio;if(t){var l=Number(e.minCropBoxWidth)||0,h=Number(e.minCropBoxHeight)||0,c=s?Math.min(n.width,r.width,r.width+r.left,n.width-r.left):n.width,p=s?Math.min(n.height,r.height,r.height+r.top,n.height-r.top):n.height;l=Math.min(l,n.width),h=Math.min(h,n.height),f&&(l&&h?h*f>l?h=l/f:l=h*f:l?h=l/f:h&&(l=h*f),p*f>c?p=c/f:c=p*f),o.minWidth=Math.min(l,c),o.minHeight=Math.min(h,p),o.maxWidth=c,o.maxHeight=p}i&&(s?(o.minLeft=Math.max(0,r.left),o.minTop=Math.max(0,r.top),o.maxLeft=Math.min(n.width,r.left+r.width)-o.width,o.maxTop=Math.min(n.height,r.top+r.height)-o.height):(o.minLeft=0,o.minTop=0,o.maxLeft=n.width-o.width,o.maxTop=n.height-o.height))},renderCropBox:function(){var t=this.options,i=this.containerData,e=this.cropBoxData;(e.width>e.maxWidth||e.width<e.minWidth)&&(e.left=e.oldLeft),(e.height>e.maxHeight||e.height<e.minHeight)&&(e.top=e.oldTop),e.width=Math.min(Math.max(e.width,e.minWidth),e.maxWidth),e.height=Math.min(Math.max(e.height,e.minHeight),e.maxHeight),this.limitCropBox(!1,!0),e.left=Math.min(Math.max(e.left,e.minLeft),e.maxLeft),e.top=Math.min(Math.max(e.top,e.minTop),e.maxTop),e.oldLeft=e.left,e.oldTop=e.top,t.movable&&t.cropBoxMovable&&ht(this.face,st,e.width>=i.width&&e.height>=i.height?ie:At),W(this.cropBox,x({width:e.width,height:e.height},ot({translateX:e.left,translateY:e.top}))),this.cropped&&this.limited&&this.limitCanvas(!0,!0),this.disabled||this.output()},output:function(){this.preview(),tt(this.element,Dt,this.getData())}},ai={initPreview:function(){var t=this.element,i=this.crossOrigin,e=this.options.preview,n=i?this.crossOriginUrl:this.url,r=t.alt||"The image to preview",o=document.createElement("img");if(i&&(o.crossOrigin=i),o.src=n,o.alt=r,this.viewBox.appendChild(o),this.viewBoxImage=o,!!e){var s=e;typeof e=="string"?s=t.ownerDocument.querySelectorAll(e):e.querySelector&&(s=[e]),this.previews=s,E(s,function(f){var l=document.createElement("img");ht(f,lt,{width:f.offsetWidth,height:f.offsetHeight,html:f.innerHTML}),i&&(l.crossOrigin=i),l.src=n,l.alt=r,l.style.cssText='display:block;width:100%;height:auto;min-width:0!important;min-height:0!important;max-width:none!important;max-height:none!important;image-orientation:0deg!important;"',f.innerHTML="",f.appendChild(l)})}},resetPreview:function(){E(this.previews,function(t){var i=Ot(t,lt);W(t,{width:i.width,height:i.height}),t.innerHTML=i.html,je(t,lt)})},preview:function(){var t=this.imageData,i=this.canvasData,e=this.cropBoxData,n=e.width,r=e.height,o=t.width,s=t.height,f=e.left-i.left-t.left,l=e.top-i.top-t.top;!this.cropped||this.disabled||(W(this.viewBoxImage,x({width:o,height:s},ot(x({translateX:-f,translateY:-l},t)))),E(this.previews,function(h){var c=Ot(h,lt),p=c.width,m=c.height,g=p,b=m,v=1;n&&(v=p/n,b=r*v),r&&b>m&&(v=m/r,g=n*v,b=m),W(h,{width:g,height:b}),W(h.getElementsByTagName("img")[0],x({width:o*v,height:s*v},ot(x({translateX:-f*v,translateY:-l*v},t))))}))}},ri={bind:function(){var t=this.element,i=this.options,e=this.cropper;_(i.cropstart)&&I(t,Ct,i.cropstart),_(i.cropmove)&&I(t,Mt,i.cropmove),_(i.cropend)&&I(t,Et,i.cropend),_(i.crop)&&I(t,Dt,i.crop),_(i.zoom)&&I(t,Tt,i.zoom),I(e,Wt,this.onCropStart=this.cropStart.bind(this)),i.zoomable&&i.zoomOnWheel&&I(e,Gt,this.onWheel=this.wheel.bind(this),{passive:!1,capture:!0}),i.toggleDragModeOnDblclick&&I(e,Ht,this.onDblclick=this.dblclick.bind(this)),I(t.ownerDocument,Ut,this.onCropMove=this.cropMove.bind(this)),I(t.ownerDocument,jt,this.onCropEnd=this.cropEnd.bind(this)),i.responsive&&I(window,$t,this.onResize=this.resize.bind(this))},unbind:function(){var t=this.element,i=this.options,e=this.cropper;_(i.cropstart)&&B(t,Ct,i.cropstart),_(i.cropmove)&&B(t,Mt,i.cropmove),_(i.cropend)&&B(t,Et,i.cropend),_(i.crop)&&B(t,Dt,i.crop),_(i.zoom)&&B(t,Tt,i.zoom),B(e,Wt,this.onCropStart),i.zoomable&&i.zoomOnWheel&&B(e,Gt,this.onWheel,{passive:!1,capture:!0}),i.toggleDragModeOnDblclick&&B(e,Ht,this.onDblclick),B(t.ownerDocument,Ut,this.onCropMove),B(t.ownerDocument,jt,this.onCropEnd),i.responsive&&B(window,$t,this.onResize)}},ni={resize:function(){if(!this.disabled){var t=this.options,i=this.container,e=this.containerData,n=i.offsetWidth/e.width,r=i.offsetHeight/e.height,o=Math.abs(n-1)>Math.abs(r-1)?n:r;if(o!==1){var s,f;t.restore&&(s=this.getCanvasData(),f=this.getCropBoxData()),this.render(),t.restore&&(this.setCanvasData(E(s,function(l,h){s[h]=l*o})),this.setCropBoxData(E(f,function(l,h){f[h]=l*o})))}}},dblclick:function(){this.disabled||this.options.dragMode===ne||this.setDragMode(We(this.dragBox,yt)?re:Rt)},wheel:function(t){var i=this,e=Number(this.options.wheelZoomRatio)||.1,n=1;this.disabled||(t.preventDefault(),!this.wheeling&&(this.wheeling=!0,setTimeout(function(){i.wheeling=!1},50),t.deltaY?n=t.deltaY>0?1:-1:t.wheelDelta?n=-t.wheelDelta/120:t.detail&&(n=t.detail>0?1:-1),this.zoom(-n*e,t)))},cropStart:function(t){var i=t.buttons,e=t.button;if(!(this.disabled||(t.type==="mousedown"||t.type==="pointerdown"&&t.pointerType==="mouse")&&(u(i)&&i!==1||u(e)&&e!==0||t.ctrlKey))){var n=this.options,r=this.pointers,o;t.changedTouches?E(t.changedTouches,function(s){r[s.identifier]=pt(s)}):r[t.pointerId||0]=pt(t),Object.keys(r).length>1&&n.zoomable&&n.zoomOnTouch?o=ae:o=Ot(t.target,st),Se.test(o)&&tt(this.element,Ct,{originalEvent:t,action:o})!==!1&&(t.preventDefault(),this.action=o,this.cropping=!1,o===ee&&(this.cropping=!0,O(this.dragBox,ft)))}},cropMove:function(t){var i=this.action;if(!(this.disabled||!i)){var e=this.pointers;t.preventDefault(),tt(this.element,Mt,{originalEvent:t,action:i})!==!1&&(t.changedTouches?E(t.changedTouches,function(n){x(e[n.identifier]||{},pt(n,!0))}):x(e[t.pointerId||0]||{},pt(t,!0)),this.change(t))}},cropEnd:function(t){if(!this.disabled){var i=this.action,e=this.pointers;t.changedTouches?E(t.changedTouches,function(n){delete e[n.identifier]}):delete e[t.pointerId||0],i&&(t.preventDefault(),Object.keys(e).length||(this.action=""),this.cropping&&(this.cropping=!1,K(this.dragBox,ft,this.cropped&&this.options.modal)),tt(this.element,Et,{originalEvent:t,action:i}))}}},oi={change:function(t){var i=this.options,e=this.canvasData,n=this.containerData,r=this.cropBoxData,o=this.pointers,s=this.action,f=i.aspectRatio,l=r.left,h=r.top,c=r.width,p=r.height,m=l+c,g=h+p,b=0,v=0,M=n.width,T=n.height,D=!0,Y;!f&&t.shiftKey&&(f=c&&p?c/p:1),this.limited&&(b=r.minLeft,v=r.minTop,M=b+Math.min(n.width,e.width,e.left+e.width),T=v+Math.min(n.height,e.height,e.top+e.height));var R=o[Object.keys(o)[0]],d={x:R.endX-R.startX,y:R.endY-R.startY},w=function(S){switch(S){case G:m+d.x>M&&(d.x=M-m);break;case q:l+d.x<b&&(d.x=b-l);break;case H:h+d.y<v&&(d.y=v-h);break;case Q:g+d.y>T&&(d.y=T-g);break}};switch(s){case At:l+=d.x,h+=d.y;break;case G:if(d.x>=0&&(m>=M||f&&(h<=v||g>=T))){D=!1;break}w(G),c+=d.x,c<0&&(s=q,c=-c,l-=c),f&&(p=c/f,h+=(r.height-p)/2);break;case H:if(d.y<=0&&(h<=v||f&&(l<=b||m>=M))){D=!1;break}w(H),p-=d.y,h+=d.y,p<0&&(s=Q,p=-p,h-=p),f&&(c=p*f,l+=(r.width-c)/2);break;case q:if(d.x<=0&&(l<=b||f&&(h<=v||g>=T))){D=!1;break}w(q),c-=d.x,l+=d.x,c<0&&(s=G,c=-c,l-=c),f&&(p=c/f,h+=(r.height-p)/2);break;case Q:if(d.y>=0&&(g>=T||f&&(l<=b||m>=M))){D=!1;break}w(Q),p+=d.y,p<0&&(s=H,p=-p,h-=p),f&&(c=p*f,l+=(r.width-c)/2);break;case et:if(f){if(d.y<=0&&(h<=v||m>=M)){D=!1;break}w(H),p-=d.y,h+=d.y,c=p*f}else w(H),w(G),d.x>=0?m<M?c+=d.x:d.y<=0&&h<=v&&(D=!1):c+=d.x,d.y<=0?h>v&&(p-=d.y,h+=d.y):(p-=d.y,h+=d.y);c<0&&p<0?(s=rt,p=-p,c=-c,h-=p,l-=c):c<0?(s=it,c=-c,l-=c):p<0&&(s=at,p=-p,h-=p);break;case it:if(f){if(d.y<=0&&(h<=v||l<=b)){D=!1;break}w(H),p-=d.y,h+=d.y,c=p*f,l+=r.width-c}else w(H),w(q),d.x<=0?l>b?(c-=d.x,l+=d.x):d.y<=0&&h<=v&&(D=!1):(c-=d.x,l+=d.x),d.y<=0?h>v&&(p-=d.y,h+=d.y):(p-=d.y,h+=d.y);c<0&&p<0?(s=at,p=-p,c=-c,h-=p,l-=c):c<0?(s=et,c=-c,l-=c):p<0&&(s=rt,p=-p,h-=p);break;case rt:if(f){if(d.x<=0&&(l<=b||g>=T)){D=!1;break}w(q),c-=d.x,l+=d.x,p=c/f}else w(Q),w(q),d.x<=0?l>b?(c-=d.x,l+=d.x):d.y>=0&&g>=T&&(D=!1):(c-=d.x,l+=d.x),d.y>=0?g<T&&(p+=d.y):p+=d.y;c<0&&p<0?(s=et,p=-p,c=-c,h-=p,l-=c):c<0?(s=at,c=-c,l-=c):p<0&&(s=it,p=-p,h-=p);break;case at:if(f){if(d.x>=0&&(m>=M||g>=T)){D=!1;break}w(G),c+=d.x,p=c/f}else w(Q),w(G),d.x>=0?m<M?c+=d.x:d.y>=0&&g>=T&&(D=!1):c+=d.x,d.y>=0?g<T&&(p+=d.y):p+=d.y;c<0&&p<0?(s=it,p=-p,c=-c,h-=p,l-=c):c<0?(s=rt,c=-c,l-=c):p<0&&(s=et,p=-p,h-=p);break;case ie:this.move(d.x,d.y),D=!1;break;case ae:this.zoom($e(o),t),D=!1;break;case ee:if(!d.x||!d.y){D=!1;break}Y=pe(this.cropper),l=R.startX-Y.left,h=R.startY-Y.top,c=r.minWidth,p=r.minHeight,d.x>0?s=d.y>0?at:et:d.x<0&&(l-=c,s=d.y>0?rt:it),d.y<0&&(h-=p),this.cropped||(k(this.cropBox,A),this.cropped=!0,this.limited&&this.limitCropBox(!0,!0));break}D&&(r.width=c,r.height=p,r.left=l,r.top=h,this.action=s,this.renderCropBox()),E(o,function(C){C.startX=C.endX,C.startY=C.endY})}},si={crop:function(){return this.ready&&!this.cropped&&!this.disabled&&(this.cropped=!0,this.limitCropBox(!0,!0),this.options.modal&&O(this.dragBox,ft),k(this.cropBox,A),this.setCropBoxData(this.initialCropBoxData)),this},reset:function(){return this.ready&&!this.disabled&&(this.imageData=x({},this.initialImageData),this.canvasData=x({},this.initialCanvasData),this.cropBoxData=x({},this.initialCropBoxData),this.renderCanvas(),this.cropped&&this.renderCropBox()),this},clear:function(){return this.cropped&&!this.disabled&&(x(this.cropBoxData,{left:0,top:0,width:0,height:0}),this.cropped=!1,this.renderCropBox(),this.limitCanvas(!0,!0),this.renderCanvas(),k(this.dragBox,ft),O(this.cropBox,A)),this},replace:function(t){var i=arguments.length>1&&arguments[1]!==void 0?arguments[1]:!1;return!this.disabled&&t&&(this.isImg&&(this.element.src=t),i?(this.url=t,this.image.src=t,this.ready&&(this.viewBoxImage.src=t,E(this.previews,function(e){e.getElementsByTagName("img")[0].src=t}))):(this.isImg&&(this.replaced=!0),this.options.data=null,this.uncreate(),this.load(t))),this},enable:function(){return this.ready&&this.disabled&&(this.disabled=!1,k(this.cropper,Xt)),this},disable:function(){return this.ready&&!this.disabled&&(this.disabled=!0,O(this.cropper,Xt)),this},destroy:function(){var t=this.element;return t[y]?(t[y]=void 0,this.isImg&&this.replaced&&(t.src=this.originalUrl),this.uncreate(),this):this},move:function(t){var i=arguments.length>1&&arguments[1]!==void 0?arguments[1]:t,e=this.canvasData,n=e.left,r=e.top;return this.moveTo(mt(t)?t:n+Number(t),mt(i)?i:r+Number(i))},moveTo:function(t){var i=arguments.length>1&&arguments[1]!==void 0?arguments[1]:t,e=this.canvasData,n=!1;return t=Number(t),i=Number(i),this.ready&&!this.disabled&&this.options.movable&&(u(t)&&(e.left=t,n=!0),u(i)&&(e.top=i,n=!0),n&&this.renderCanvas(!0)),this},zoom:function(t,i){var e=this.canvasData;return t=Number(t),t<0?t=1/(1-t):t=1+t,this.zoomTo(e.width*t/e.naturalWidth,null,i)},zoomTo:function(t,i,e){var n=this.options,r=this.canvasData,o=r.width,s=r.height,f=r.naturalWidth,l=r.naturalHeight;if(t=Number(t),t>=0&&this.ready&&!this.disabled&&n.zoomable){var h=f*t,c=l*t;if(tt(this.element,Tt,{ratio:t,oldRatio:o/f,originalEvent:e})===!1)return this;if(e){var p=this.pointers,m=pe(this.cropper),g=p&&Object.keys(p).length?Ge(p):{pageX:e.pageX,pageY:e.pageY};r.left-=(h-o)*((g.pageX-m.left-r.left)/o),r.top-=(c-s)*((g.pageY-m.top-r.top)/s)}else Z(i)&&u(i.x)&&u(i.y)?(r.left-=(h-o)*((i.x-r.left)/o),r.top-=(c-s)*((i.y-r.top)/s)):(r.left-=(h-o)/2,r.top-=(c-s)/2);r.width=h,r.height=c,this.renderCanvas(!0)}return this},rotate:function(t){return this.rotateTo((this.imageData.rotate||0)+Number(t))},rotateTo:function(t){return t=Number(t),u(t)&&this.ready&&!this.disabled&&this.options.rotatable&&(this.imageData.rotate=t%360,this.renderCanvas(!0,!0)),this},scaleX:function(t){var i=this.imageData.scaleY;return this.scale(t,u(i)?i:1)},scaleY:function(t){var i=this.imageData.scaleX;return this.scale(u(i)?i:1,t)},scale:function(t){var i=arguments.length>1&&arguments[1]!==void 0?arguments[1]:t,e=this.imageData,n=!1;return t=Number(t),i=Number(i),this.ready&&!this.disabled&&this.options.scalable&&(u(t)&&(e.scaleX=t,n=!0),u(i)&&(e.scaleY=i,n=!0),n&&this.renderCanvas(!0,!0)),this},getData:function(){var t=arguments.length>0&&arguments[0]!==void 0?arguments[0]:!1,i=this.options,e=this.imageData,n=this.canvasData,r=this.cropBoxData,o;if(this.ready&&this.cropped){o={x:r.left-n.left,y:r.top-n.top,width:r.width,height:r.height};var s=e.width/e.naturalWidth;if(E(o,function(h,c){o[c]=h/s}),t){var f=Math.round(o.y+o.height),l=Math.round(o.x+o.width);o.x=Math.round(o.x),o.y=Math.round(o.y),o.width=l-o.x,o.height=f-o.y}}else o={x:0,y:0,width:0,height:0};return i.rotatable&&(o.rotate=e.rotate||0),i.scalable&&(o.scaleX=e.scaleX||1,o.scaleY=e.scaleY||1),o},setData:function(t){var i=this.options,e=this.imageData,n=this.canvasData,r={};if(this.ready&&!this.disabled&&Z(t)){var o=!1;i.rotatable&&u(t.rotate)&&t.rotate!==e.rotate&&(e.rotate=t.rotate,o=!0),i.scalable&&(u(t.scaleX)&&t.scaleX!==e.scaleX&&(e.scaleX=t.scaleX,o=!0),u(t.scaleY)&&t.scaleY!==e.scaleY&&(e.scaleY=t.scaleY,o=!0)),o&&this.renderCanvas(!0,!0);var s=e.width/e.naturalWidth;u(t.x)&&(r.left=t.x*s+n.left),u(t.y)&&(r.top=t.y*s+n.top),u(t.width)&&(r.width=t.width*s),u(t.height)&&(r.height=t.height*s),this.setCropBoxData(r)}return this},getContainerData:function(){return this.ready?x({},this.containerData):{}},getImageData:function(){return this.sized?x({},this.imageData):{}},getCanvasData:function(){var t=this.canvasData,i={};return this.ready&&E(["left","top","width","height","naturalWidth","naturalHeight"],function(e){i[e]=t[e]}),i},setCanvasData:function(t){var i=this.canvasData,e=i.aspectRatio;return this.ready&&!this.disabled&&Z(t)&&(u(t.left)&&(i.left=t.left),u(t.top)&&(i.top=t.top),u(t.width)?(i.width=t.width,i.height=t.width/e):u(t.height)&&(i.height=t.height,i.width=t.height*e),this.renderCanvas(!0)),this},getCropBoxData:function(){var t=this.cropBoxData,i;return this.ready&&this.cropped&&(i={left:t.left,top:t.top,width:t.width,height:t.height}),i||{}},setCropBoxData:function(t){var i=this.cropBoxData,e=this.options.aspectRatio,n,r;return this.ready&&this.cropped&&!this.disabled&&Z(t)&&(u(t.left)&&(i.left=t.left),u(t.top)&&(i.top=t.top),u(t.width)&&t.width!==i.width&&(n=!0,i.width=t.width),u(t.height)&&t.height!==i.height&&(r=!0,i.height=t.height),e&&(n?i.height=i.width/e:r&&(i.width=i.height*e)),this.renderCropBox()),this},getCroppedCanvas:function(){var t=arguments.length>0&&arguments[0]!==void 0?arguments[0]:{};if(!this.ready||!window.HTMLCanvasElement)return null;var i=this.canvasData,e=Fe(this.image,this.imageData,i,t);if(!this.cropped)return e;var n=this.getData(),r=n.x,o=n.y,s=n.width,f=n.height,l=e.width/Math.floor(i.naturalWidth);l!==1&&(r*=l,o*=l,s*=l,f*=l);var h=s/f,c=U({aspectRatio:h,width:t.maxWidth||1/0,height:t.maxHeight||1/0}),p=U({aspectRatio:h,width:t.minWidth||0,height:t.minHeight||0},"cover"),m=U({aspectRatio:h,width:t.width||(l!==1?e.width:s),height:t.height||(l!==1?e.height:f)}),g=m.width,b=m.height;g=Math.min(c.width,Math.max(p.width,g)),b=Math.min(c.height,Math.max(p.height,b));var v=document.createElement("canvas"),M=v.getContext("2d");v.width=J(g),v.height=J(b),M.fillStyle=t.fillColor||"transparent",M.fillRect(0,0,g,b);var T=t.imageSmoothingEnabled,D=T===void 0?!0:T,Y=t.imageSmoothingQuality;M.imageSmoothingEnabled=D,Y&&(M.imageSmoothingQuality=Y);var R=e.width,d=e.height,w=r,C=o,S,X,j,V,z,L;w<=-s||w>R?(w=0,S=0,j=0,z=0):w<=0?(j=-w,w=0,S=Math.min(R,s+w),z=S):w<=R&&(j=0,S=Math.min(s,R-w),z=S),S<=0||C<=-f||C>d?(C=0,X=0,V=0,L=0):C<=0?(V=-C,C=0,X=Math.min(d,f+C),L=X):C<=d&&(V=0,X=Math.min(f,d-C),L=X);var N=[w,C,S,X];if(z>0&&L>0){var $=g/s;N.push(j*$,V*$,z*$,L*$)}return M.drawImage.apply(M,[e].concat(te(N.map(function(ct){return Math.floor(J(ct))})))),v},setAspectRatio:function(t){var i=this.options;return!this.disabled&&!mt(t)&&(i.aspectRatio=Math.max(0,t)||NaN,this.ready&&(this.initCropBox(),this.cropped&&this.renderCropBox())),this},setDragMode:function(t){var i=this.options,e=this.dragBox,n=this.face;if(this.ready&&!this.disabled){var r=t===Rt,o=i.movable&&t===re;t=r||o?t:ne,i.dragMode=t,ht(e,st,t),K(e,yt,r),K(e,xt,o),i.cropBoxMovable||(ht(n,st,t),K(n,yt,r),K(n,xt,o))}return this}},hi=P.Cropper,de=function(){function a(t){var i=arguments.length>1&&arguments[1]!==void 0?arguments[1]:{};if(xe(this,a),!t||!Le.test(t.tagName))throw new Error("The first argument is required and must be an <img> or <canvas> element.");this.element=t,this.options=x({},Ft,Z(i)&&i),this.cropped=!1,this.disabled=!1,this.pointers={},this.ready=!1,this.reloading=!1,this.replaced=!1,this.sized=!1,this.sizing=!1,this.init()}return De(a,[{key:"init",value:function(){var i=this.element,e=i.tagName.toLowerCase(),n;if(!i[y]){if(i[y]=this,e==="img"){if(this.isImg=!0,n=i.getAttribute("src")||"",this.originalUrl=n,!n)return;n=i.src}else e==="canvas"&&window.HTMLCanvasElement&&(n=i.toDataURL());this.load(n)}}},{key:"load",value:function(i){var e=this;if(i){this.url=i,this.imageData={};var n=this.element,r=this.options;if(!r.rotatable&&!r.scalable&&(r.checkOrientation=!1),!r.checkOrientation||!window.ArrayBuffer){this.clone();return}if(Ie.test(i)){Be.test(i)?this.read(Ke(i)):this.clone();return}var o=new XMLHttpRequest,s=this.clone.bind(this);this.reloading=!0,this.xhr=o,o.onabort=s,o.onerror=s,o.ontimeout=s,o.onprogress=function(){o.getResponseHeader("content-type")!==qt&&o.abort()},o.onload=function(){e.read(o.response)},o.onloadend=function(){e.reloading=!1,e.xhr=null},r.checkCrossOrigin&&Zt(i)&&n.crossOrigin&&(i=Kt(i)),o.open("GET",i,!0),o.responseType="arraybuffer",o.withCredentials=n.crossOrigin==="use-credentials",o.send()}}},{key:"read",value:function(i){var e=this.options,n=this.imageData,r=ti(i),o=0,s=1,f=1;if(r>1){this.url=Je(i,qt);var l=ei(r);o=l.rotate,s=l.scaleX,f=l.scaleY}e.rotatable&&(n.rotate=o),e.scalable&&(n.scaleX=s,n.scaleY=f),this.clone()}},{key:"clone",value:function(){var i=this.element,e=this.url,n=i.crossOrigin,r=e;this.options.checkCrossOrigin&&Zt(e)&&(n||(n="anonymous"),r=Kt(e)),this.crossOrigin=n,this.crossOriginUrl=r;var o=document.createElement("img");n&&(o.crossOrigin=n),o.src=r||e,o.alt=i.alt||"The image to crop",this.image=o,o.onload=this.start.bind(this),o.onerror=this.stop.bind(this),O(o,zt),i.parentNode.insertBefore(o,i.nextSibling)}},{key:"start",value:function(){var i=this,e=this.image;e.onload=null,e.onerror=null,this.sizing=!0;var n=P.navigator&&/(?:iPad|iPhone|iPod).*?AppleWebKit/i.test(P.navigator.userAgent),r=function(l,h){x(i.imageData,{naturalWidth:l,naturalHeight:h,aspectRatio:l/h}),i.initialImageData=x({},i.imageData),i.sizing=!1,i.sized=!0,i.build()};if(e.naturalWidth&&!n){r(e.naturalWidth,e.naturalHeight);return}var o=document.createElement("img"),s=document.body||document.documentElement;this.sizingImage=o,o.onload=function(){r(o.width,o.height),n||s.removeChild(o)},o.src=e.src,n||(o.style.cssText="left:0;max-height:none!important;max-width:none!important;min-height:0!important;min-width:0!important;opacity:0;position:absolute;top:0;z-index:-1;",s.appendChild(o))}},{key:"stop",value:function(){var i=this.image;i.onload=null,i.onerror=null,i.parentNode.removeChild(i),this.image=null}},{key:"build",value:function(){if(!(!this.sized||this.ready)){var i=this.element,e=this.options,n=this.image,r=i.parentNode,o=document.createElement("div");o.innerHTML=ke;var s=o.querySelector(".".concat(y,"-container")),f=s.querySelector(".".concat(y,"-canvas")),l=s.querySelector(".".concat(y,"-drag-box")),h=s.querySelector(".".concat(y,"-crop-box")),c=h.querySelector(".".concat(y,"-face"));this.container=r,this.cropper=s,this.canvas=f,this.dragBox=l,this.cropBox=h,this.viewBox=s.querySelector(".".concat(y,"-view-box")),this.face=c,f.appendChild(n),O(i,A),r.insertBefore(s,i.nextSibling),k(n,zt),this.initPreview(),this.bind(),e.initialAspectRatio=Math.max(0,e.initialAspectRatio)||NaN,e.aspectRatio=Math.max(0,e.aspectRatio)||NaN,e.viewMode=Math.max(0,Math.min(3,Math.round(e.viewMode)))||0,O(h,A),e.guides||O(h.getElementsByClassName("".concat(y,"-dashed")),A),e.center||O(h.getElementsByClassName("".concat(y,"-center")),A),e.background&&O(s,"".concat(y,"-bg")),e.highlight||O(c,Ne),e.cropBoxMovable&&(O(c,xt),ht(c,st,At)),e.cropBoxResizable||(O(h.getElementsByClassName("".concat(y,"-line")),A),O(h.getElementsByClassName("".concat(y,"-point")),A)),this.render(),this.ready=!0,this.setDragMode(e.dragMode),e.autoCrop&&this.crop(),this.setData(e.data),_(e.ready)&&I(i,Vt,e.ready,{once:!0}),tt(i,Vt)}}},{key:"unbuild",value:function(){if(this.ready){this.ready=!1,this.unbind(),this.resetPreview();var i=this.cropper.parentNode;i&&i.removeChild(this.cropper),k(this.element,A)}}},{key:"uncreate",value:function(){this.ready?(this.unbuild(),this.ready=!1,this.cropped=!1):this.sizing?(this.sizingImage.onload=null,this.sizing=!1,this.sized=!1):this.reloading?(this.xhr.onabort=null,this.xhr.abort()):this.image&&this.stop()}}],[{key:"noConflict",value:function(){return window.Cropper=hi,a}},{key:"setDefaults",value:function(i){x(Ft,Z(i)&&i)}}]),a}();x(de.prototype,ii,ai,ri,ni,oi,si);const ci={props:{src:{type:String,default:""},compress:{type:Number,default:1},aspectRatio:{type:Number,default:NaN}},data(){return{crop:null}},watch:{aspectRatio(a){this.crop.setAspectRatio(a)}},mounted(){this.init()},methods:{init(){this.crop=new de(this.$refs.img,{viewMode:2,dragMode:"move",responsive:!1,aspectRatio:this.aspectRatio,preview:this.$refs.preview})},setAspectRatio(a){this.crop.setAspectRatio(a)},getCropData(a,t="image/jpeg"){a(this.crop.getCroppedCanvas().toDataURL(t,this.compress))},getCropBlob(a,t="image/jpeg"){this.crop.getCroppedCanvas().toBlob(i=>{a(i)},t,this.compress)},getCropFile(a,t="fileName.jpg",i="image/jpeg"){this.crop.getCroppedCanvas().toBlob(e=>{let n=new File([e],t,{type:i});a(n)},i,this.compress)}}},li=a=>(be("data-v-750da0d0"),a=a(),ye(),a),pi={class:"sc-cropper"},fi={class:"sc-cropper__img"},di=["src"],ui={class:"sc-cropper__preview"},gi=li(()=>nt("h4",null,"图像预览",-1)),mi={class:"sc-cropper__preview__img",ref:"preview"};function vi(a,t,i,e,n,r){return ve(),we("div",pi,[nt("div",fi,[nt("img",{src:i.src,ref:"img"},null,8,di)]),nt("div",ui,[gi,nt("div",mi,null,512)])])}const bi=me(ci,[["render",vi],["__scopeId","data-v-750da0d0"],["__file","F:/workspace/vue-support-parent-starter/vue-support-scheduler-starter/src/components/scCropper/index.vue"]]);export{bi as default};
