import{Gt as e,Ht as t,It as n,_t as r,hn as i,ht as a,nn as o,vt as s,wt as c,zn as l,zt as u}from"./lib-antv-CsEhe1Bd.js";import{t as d}from"./_plugin-vue_export-helper-BDNMzG2s.js";var ee=1/60,te=`#version 300 es
precision highp float;
layout(location = 0) in vec2 aPos;
out vec2 vUv;
out vec2 vL;
out vec2 vR;
out vec2 vT;
out vec2 vB;
uniform vec2 texelSize;
void main () {
  vUv = aPos * 0.5 + 0.5;
  vL = vUv - vec2(texelSize.x, 0.0);
  vR = vUv + vec2(texelSize.x, 0.0);
  vT = vUv + vec2(0.0, texelSize.y);
  vB = vUv - vec2(0.0, texelSize.y);
  gl_Position = vec4(aPos, 0.0, 1.0);
}`,ne=`#version 300 es
precision highp float;
in vec2 vUv;
out vec4 outColor;
uniform sampler2D uContent;
uniform sampler2D uFluid;
uniform vec3 uColor;
uniform float uDistortion;
uniform float uIntensity;
uniform float uBlend;
uniform float uRainbow;
uniform float uHasContent;
vec3 toLinear (vec3 c) {
  return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(0.04045, c));
}
vec3 toSrgb (vec3 c) {
  return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(0.0031308, c));
}
void main () {
  vec3 fluid = texture(uFluid, vUv).rgb;
  if (uHasContent < 0.5) {
    float mag = length(fluid);
    vec3 tint = uRainbow == 1.0
      ? clamp(fluid / max(mag, 1e-3), 0.0, 1.0)
      : uColor;
    float overlay = (1.0 - exp(-mag * uIntensity * 0.5)) * 0.82;
    outColor = vec4(toSrgb(clamp(tint, 0.0, 1.0)) * overlay, overlay);
    return;
  }
  vec2 uv = vUv - fluid.rg * uDistortion * 0.001;
  vec4 content = texture(uContent, vec2(uv.x, 1.0 - uv.y));
  content.rgb = toLinear(content.rgb);
  vec3 tint = uRainbow == 1.0 ? fluid : uColor * length(fluid);
  vec4 fluidColor = vec4(tint, 1.0);
  vec4 blended = mix(content, fluidColor, uBlend * 0.01 * clamp(length(fluid), 0.0, 1.0));
  vec4 final = mix(blended, vec4(0.0), 1.0 - content.a);
  outColor = vec4(toSrgb(clamp(final.rgb, 0.0, 1.0)), final.a);
}`,re=`#version 300 es
precision highp float;
in vec2 vUv;
out vec4 outColor;
uniform sampler2D uTarget;
uniform float uAspect;
uniform vec3 uColor;
uniform vec2 uPoint;
uniform float uRadius;
void main () {
  vec2 p = vUv - uPoint;
  p.x *= uAspect;
  vec3 splat = exp(-dot(p, p) / uRadius) * uColor;
  vec3 base = texture(uTarget, vUv).xyz;
  outColor = vec4(base + splat, 1.0);
}`,ie=`#version 300 es
precision highp float;
in vec2 vUv;
out vec4 outColor;
uniform sampler2D uVelocity;
uniform sampler2D uSource;
uniform vec2 texelSize;
uniform float uDt;
uniform float uDissipation;
void main () {
  vec2 coord = vUv - uDt * texture(uVelocity, vUv).xy * texelSize;
  outColor = uDissipation * texture(uSource, coord);
  outColor.a = 1.0;
}`,ae=`#version 300 es
precision highp float;
in vec2 vUv;
out vec4 outColor;
uniform sampler2D uTexture;
uniform float uValue;
void main () {
  outColor = uValue * texture(uTexture, vUv);
}`,oe=`#version 300 es
precision highp float;
in vec2 vUv;
in vec2 vL;
in vec2 vR;
in vec2 vT;
in vec2 vB;
out vec4 outColor;
uniform sampler2D uVelocity;
void main () {
  float L = texture(uVelocity, vL).x;
  float R = texture(uVelocity, vR).x;
  float T = texture(uVelocity, vT).y;
  float B = texture(uVelocity, vB).y;
  vec2 C = texture(uVelocity, vUv).xy;
  if (vL.x < 0.0) { L = -C.x; }
  if (vR.x > 1.0) { R = -C.x; }
  if (vT.y > 1.0) { T = -C.y; }
  if (vB.y < 0.0) { B = -C.y; }
  float div = 0.5 * (R - L + T - B);
  outColor = vec4(div, 0.0, 0.0, 1.0);
}`,se=`#version 300 es
precision highp float;
in vec2 vUv;
in vec2 vL;
in vec2 vR;
in vec2 vT;
in vec2 vB;
out vec4 outColor;
uniform sampler2D uVelocity;
void main () {
  float L = texture(uVelocity, vL).y;
  float R = texture(uVelocity, vR).y;
  float T = texture(uVelocity, vT).x;
  float B = texture(uVelocity, vB).x;
  float vorticity = R - L - T + B;
  outColor = vec4(vorticity, 0.0, 0.0, 1.0);
}`,ce=`#version 300 es
precision highp float;
in vec2 vUv;
in vec2 vL;
in vec2 vR;
in vec2 vT;
in vec2 vB;
out vec4 outColor;
uniform sampler2D uVelocity;
uniform sampler2D uCurl;
uniform float uCurlStrength;
uniform float uDt;
void main () {
  float L = texture(uCurl, vL).x;
  float R = texture(uCurl, vR).x;
  float T = texture(uCurl, vT).x;
  float B = texture(uCurl, vB).x;
  float C = texture(uCurl, vUv).x;
  vec2 force = vec2(abs(T) - abs(B), abs(R) - abs(L)) * 0.5;
  force /= length(force) + 1.0;
  force *= uCurlStrength * C;
  force.y *= -1.0;
  vec2 velocity = texture(uVelocity, vUv).xy;
  outColor = vec4(velocity + force * uDt, 0.0, 1.0);
}`,le=`#version 300 es
precision highp float;
in vec2 vUv;
in vec2 vL;
in vec2 vR;
in vec2 vT;
in vec2 vB;
out vec4 outColor;
uniform sampler2D uPressure;
uniform sampler2D uDivergence;
void main () {
  float L = texture(uPressure, vL).x;
  float R = texture(uPressure, vR).x;
  float T = texture(uPressure, vT).x;
  float B = texture(uPressure, vB).x;
  float divergence = texture(uDivergence, vUv).x;
  float pressure = (L + R + B + T - divergence) * 0.25;
  outColor = vec4(pressure, 0.0, 0.0, 1.0);
}`,ue=`#version 300 es
precision highp float;
in vec2 vUv;
in vec2 vL;
in vec2 vR;
in vec2 vT;
in vec2 vB;
out vec4 outColor;
uniform sampler2D uPressure;
uniform sampler2D uVelocity;
void main () {
  float L = texture(uPressure, vL).x;
  float R = texture(uPressure, vR).x;
  float T = texture(uPressure, vT).x;
  float B = texture(uPressure, vB).x;
  vec2 velocity = texture(uVelocity, vUv).xy;
  velocity.xy -= vec2(R - L, T - B);
  outColor = vec4(velocity, 0.0, 1.0);
}`,f=d(c({__name:`ReCanvasUi`,props:{simResolution:{},dyeResolution:{},densityDissipation:{},velocityDissipation:{},pressure:{},pressureIterations:{},curl:{},radius:{},force:{},intensity:{},distortion:{},blend:{},color:{},rainbow:{type:Boolean},style:{}},emits:[`ready`,`error`],setup(c,{expose:d,emit:f}){let de={simResolution:128,dyeResolution:512,densityDissipation:.96,velocityDissipation:1,pressure:.8,pressureIterations:4,curl:1.9,radius:.3,force:1.1,intensity:2,distortion:.4,blend:5,color:[.145,.239,.867],rainbow:!1};function p(e){return e<=.04045?e/12.92:((e+.055)/1.055)**2.4}function m(){if(typeof document>`u`)return!1;let e=document.createElement(`canvas`),t=e.getContext(`2d`);return!!(t&&typeof t.drawElementImage==`function`&&typeof e.requestPaint==`function`)}function fe(e){let t=e.getBoundingClientRect(),n=null;return n=new ResizeObserver(()=>{t=e.getBoundingClientRect()}),n.observe(e),{get current(){return t},destroy(){n?.disconnect()}}}function h(e,t={}){let n={...de,...t},{source:r,content:i,output:a}=e,o=a.getContext(`webgl2`,{alpha:!0,depth:!1,stencil:!1,antialias:!1,premultipliedAlpha:!0});if(!o||o.isContextLost()||!(o.getExtension(`EXT_color_buffer_float`)||o.getExtension(`EXT_color_buffer_half_float`)))return null;let s=r.getContext(`2d`),c=r,l=!!(s&&typeof s.drawElementImage==`function`&&typeof c.requestPaint==`function`),u=!1,d=()=>{};l&&(c.onpaint=()=>{try{s.reset(),s.drawElementImage(i,0,0),u=!0,d()}catch{}});let f=o.getExtension(`OES_texture_float_linear`)?o.LINEAR:o.NEAREST,m=[];function h(e,t){let n=o.createShader(e);return o.shaderSource(n,t),o.compileShader(n),o.getShaderParameter(n,o.COMPILE_STATUS)||console.error(`Liquid shader error:`,o.getShaderInfoLog(n)),m.push(n),n}let g=h(o.VERTEX_SHADER,te),_=[];function v(e){let t=o.createProgram();o.attachShader(t,g),o.attachShader(t,h(o.FRAGMENT_SHADER,e)),o.linkProgram(t),_.push(t);let n={},r=o.getProgramParameter(t,o.ACTIVE_UNIFORMS);for(let e=0;e<r;e++){let r=o.getActiveUniform(t,e);n[r.name]=o.getUniformLocation(t,r.name)}return{program:t,uniforms:n}}let y=v(ne),b=v(re),x=v(ie),S=v(ae),C=v(oe),w=v(se),T=v(ce),E=v(le),D=v(ue),O=o.createBuffer();o.bindBuffer(o.ARRAY_BUFFER,O),o.bufferData(o.ARRAY_BUFFER,new Float32Array([-1,-1,1,-1,-1,1,1,1]),o.STATIC_DRAW),o.enableVertexAttribArray(0),o.vertexAttribPointer(0,2,o.FLOAT,!1,0,0);function k(e,t,n,r){let i=o.createTexture();o.bindTexture(o.TEXTURE_2D,i),o.texParameteri(o.TEXTURE_2D,o.TEXTURE_MIN_FILTER,r),o.texParameteri(o.TEXTURE_2D,o.TEXTURE_MAG_FILTER,r),o.texParameteri(o.TEXTURE_2D,o.TEXTURE_WRAP_S,o.CLAMP_TO_EDGE),o.texParameteri(o.TEXTURE_2D,o.TEXTURE_WRAP_T,o.CLAMP_TO_EDGE),o.texImage2D(o.TEXTURE_2D,0,t,e,e,0,n,o.HALF_FLOAT,null);let a=o.createFramebuffer();return o.bindFramebuffer(o.FRAMEBUFFER,a),o.framebufferTexture2D(o.FRAMEBUFFER,o.COLOR_ATTACHMENT0,o.TEXTURE_2D,i,0),o.viewport(0,0,e,e),o.clearColor(0,0,0,1),o.clear(o.COLOR_BUFFER_BIT),{fbo:a,texture:i,width:e,height:e}}function A(e,t,n,r){let i=k(e,t,n,r),a=k(e,t,n,r);return{get read(){return i},get write(){return a},swap(){let e=i;i=a,a=e}}}function j(e,t){return{velocity:A(e,o.RG16F,o.RG,f),dye:A(t,o.RGBA16F,o.RGBA,f),divergence:k(e,o.R16F,o.RED,o.NEAREST),curl:k(e,o.R16F,o.RED,o.NEAREST),pressure:A(e,o.R16F,o.RED,o.NEAREST)}}let M=j(n.simResolution,n.dyeResolution);function N(e){[e.velocity.read,e.velocity.write,e.dye.read,e.dye.write,e.pressure.read,e.pressure.write,e.divergence,e.curl].forEach(e=>{o.deleteFramebuffer(e.fbo),o.deleteTexture(e.texture)})}function pe(e,t){let n=j(e,t),r=M;M=n,N(r)}let P=0,F=0;function I(){let e=Math.max(a.clientWidth,1),t=Math.max(a.clientHeight,1);P=1/(n.simResolution*(e/(t+400))),F=1/n.simResolution}function L(){let e=Math.min(window.devicePixelRatio||1,2),t=Math.max(1,Math.round(a.clientWidth*e)),n=Math.max(1,Math.round(a.clientHeight*e));if((a.width!==t||a.height!==n)&&(a.width=t,a.height=n),l){let t=Math.max(1,Math.round(r.clientWidth)),n=Math.max(1,Math.round(r.clientHeight));(r.width!==t*e||r.height!==n*e)&&(r.width=t*e,r.height=n*e),c.requestPaint()}I()}L();let R=o.createTexture();o.bindTexture(o.TEXTURE_2D,R),o.texParameteri(o.TEXTURE_2D,o.TEXTURE_MIN_FILTER,o.LINEAR),o.texParameteri(o.TEXTURE_2D,o.TEXTURE_MAG_FILTER,o.LINEAR),o.texParameteri(o.TEXTURE_2D,o.TEXTURE_WRAP_S,o.CLAMP_TO_EDGE),o.texParameteri(o.TEXTURE_2D,o.TEXTURE_WRAP_T,o.CLAMP_TO_EDGE),o.texImage2D(o.TEXTURE_2D,0,o.RGBA,1,1,0,o.RGBA,o.UNSIGNED_BYTE,new Uint8Array([0,0,0,0]));function me(){!l||!u||(u=!1,o.bindTexture(o.TEXTURE_2D,R),o.texImage2D(o.TEXTURE_2D,0,o.RGBA,o.RGBA,o.UNSIGNED_BYTE,r))}function z(e){e?(o.bindFramebuffer(o.FRAMEBUFFER,e.fbo),o.viewport(0,0,e.width,e.height)):(o.bindFramebuffer(o.FRAMEBUFFER,null),o.viewport(0,0,a.width,a.height)),o.drawArrays(o.TRIANGLE_STRIP,0,4)}function B(e,t){return o.activeTexture(o.TEXTURE0+t),o.bindTexture(o.TEXTURE_2D,e),t}function he(e,t,r,i){let s=a.clientWidth/Math.max(a.clientHeight,1),c=n.radius/100;o.useProgram(b.program),o.uniform1i(b.uniforms.uTarget,B(M.velocity.read.texture,0)),o.uniform1f(b.uniforms.uAspect,s),o.uniform2f(b.uniforms.uPoint,e,t),o.uniform3f(b.uniforms.uColor,r,i,10),o.uniform1f(b.uniforms.uRadius,c),z(M.velocity.write),M.velocity.swap(),o.uniform1i(b.uniforms.uTarget,B(M.dye.read.texture,0)),z(M.dye.write),M.dye.swap()}function ge(e){o.disable(o.BLEND),o.useProgram(w.program),o.uniform2f(w.uniforms.texelSize,P,F),o.uniform1i(w.uniforms.uVelocity,B(M.velocity.read.texture,0)),z(M.curl),o.useProgram(T.program),o.uniform2f(T.uniforms.texelSize,P,F),o.uniform1i(T.uniforms.uVelocity,B(M.velocity.read.texture,0)),o.uniform1i(T.uniforms.uCurl,B(M.curl.texture,1)),o.uniform1f(T.uniforms.uCurlStrength,n.curl),o.uniform1f(T.uniforms.uDt,ee),z(M.velocity.write),M.velocity.swap(),o.useProgram(C.program),o.uniform2f(C.uniforms.texelSize,P,F),o.uniform1i(C.uniforms.uVelocity,B(M.velocity.read.texture,0)),z(M.divergence),o.useProgram(S.program),o.uniform1i(S.uniforms.uTexture,B(M.pressure.read.texture,0)),o.uniform1f(S.uniforms.uValue,n.pressure**(e*60)),z(M.pressure.write),M.pressure.swap(),o.useProgram(E.program),o.uniform2f(E.uniforms.texelSize,P,F),o.uniform1i(E.uniforms.uDivergence,B(M.divergence.texture,0));for(let e=0;e<n.pressureIterations;e++)o.uniform1i(E.uniforms.uPressure,B(M.pressure.read.texture,1)),z(M.pressure.write),M.pressure.swap();o.useProgram(D.program),o.uniform2f(D.uniforms.texelSize,P,F),o.uniform1i(D.uniforms.uPressure,B(M.pressure.read.texture,0)),o.uniform1i(D.uniforms.uVelocity,B(M.velocity.read.texture,1)),z(M.velocity.write),M.velocity.swap(),o.useProgram(x.program),o.uniform2f(x.uniforms.texelSize,P,F),o.uniform1i(x.uniforms.uVelocity,B(M.velocity.read.texture,0)),o.uniform1i(x.uniforms.uSource,B(M.velocity.read.texture,0)),o.uniform1f(x.uniforms.uDt,ee),o.uniform1f(x.uniforms.uDissipation,n.velocityDissipation**(e*60)),z(M.velocity.write),M.velocity.swap(),o.uniform1i(x.uniforms.uVelocity,B(M.velocity.read.texture,0)),o.uniform1i(x.uniforms.uSource,B(M.dye.read.texture,1)),o.uniform1f(x.uniforms.uDissipation,n.densityDissipation**(e*60)),z(M.dye.write),M.dye.swap()}function _e(){me(),o.useProgram(y.program),o.uniform1i(y.uniforms.uContent,B(R,0)),o.uniform1i(y.uniforms.uFluid,B(M.dye.read.texture,1)),o.uniform3f(y.uniforms.uColor,p(n.color[0]),p(n.color[1]),p(n.color[2])),o.uniform1f(y.uniforms.uDistortion,n.distortion),o.uniform1f(y.uniforms.uIntensity,n.intensity),o.uniform1f(y.uniforms.uBlend,n.blend),o.uniform1f(y.uniforms.uRainbow,+!!n.rainbow),o.uniform1f(y.uniforms.uHasContent,+!!l),z(null)}let V=[],H=0,U=performance.now(),W=!1,G=!1,K=!0,q=0;function ve(){let e=Math.min(n.densityDissipation,.999);return Math.log(1e-7)/Math.log(e)/60*1e3}function ye(e){if(W)return;if(!K){G=!1;return}let t=Math.min((e-U)/1e3,1/30);if(U=e,V.length>0)for(q=e+ve();V.length>0;){let[e,t,n,r]=V.pop();he(e,t,n,r)}if(ge(t),_e(),e>=q&&!u){G=!1;return}H=requestAnimationFrame(ye)}function J(){W||G||!K||(G=!0,U=performance.now(),H=requestAnimationFrame(ye))}d=J,J();let Y=window.matchMedia(`(prefers-reduced-motion: reduce)`),X=Y.matches;function be(){X=Y.matches,X||J()}Y.addEventListener(`change`,be);let Z=new Map,xe=fe(a);function Se(e){if(X)return;let t=xe.current,r=e.clientX-t.left,i=e.clientY-t.top;if(r<0||r>t.width||i<0||i>t.height){Z.delete(e.pointerId);return}let a=Z.get(e.pointerId);if(Z.set(e.pointerId,{x:r,y:i}),!a)return;let o=(r-a.x)*n.force,s=-(i-a.y)*n.force;V.push([r/t.width,1-i/t.height,o,s]),J()}function Ce(e){if(X)return;let t=a.getBoundingClientRect(),n=e.clientX-t.left,r=e.clientY-t.top;n<0||n>t.width||r<0||r>t.height||(Z.set(e.pointerId,{x:n,y:r}),V.push([n/t.width,1-r/t.height,1,1]),J())}function Q(){Z.clear()}let $=window;$.addEventListener(`pointerdown`,Ce,{passive:!0}),$.addEventListener(`pointermove`,Se,{passive:!0}),$.addEventListener(`pointerup`,Q,{passive:!0}),$.addEventListener(`pointerleave`,Q),$.addEventListener(`pointercancel`,Q);let we=new ResizeObserver(()=>{L(),J()});we.observe(a);let Te=new IntersectionObserver(e=>{K=e[e.length-1]?.isIntersecting??!0,K&&J()});return Te.observe(a),{splat(e,t,n,r){X||(V.push([e,t,n,r]),J())},setOptions(e){if(!Object.entries(e).some(([e,t])=>n[e]!==t))return;let t=e.simResolution??n.simResolution,r=e.dyeResolution??n.dyeResolution;(t!==n.simResolution||r!==n.dyeResolution)&&(pe(t,r),n.simResolution=t,n.dyeResolution=r,I()),Object.assign(n,e),J()},resize(){L(),J()},destroy(){W=!0,xe.destroy(),cancelAnimationFrame(H),we.disconnect(),Te.disconnect(),Y.removeEventListener(`change`,be),N(M),o.deleteTexture(R),_.forEach(e=>o.deleteProgram(e)),m.forEach(e=>o.deleteShader(e)),o.deleteBuffer(O),l&&(c.onpaint=null),$.removeEventListener(`pointerdown`,Ce),$.removeEventListener(`pointermove`,Se),$.removeEventListener(`pointerup`,Q),$.removeEventListener(`pointerleave`,Q),$.removeEventListener(`pointercancel`,Q)}}}let g=c,_=f,v=i(m()),y=i(null),b=i(null),x=i(null),S=i(null),C=null,w=()=>{let e=b.value,t=x.value,n=S.value;if(!(!e||!t||!n)){if(C=h({source:e,content:t,output:n},g),v&&!C){_(`error`);return}_(`ready`,C)}},T=()=>{C?.destroy(),C=null};return o(()=>g,e=>{C?.setOptions(e)},{deep:!0}),u(()=>{v?w():_(`error`)}),n(()=>{T()}),d({getInstance:()=>C,resize:()=>C?.resize()}),(n,i)=>(t(),s(`div`,{ref_key:`wrapperRef`,ref:y,class:`re-canvas-ui`,style:l({position:`relative`,...c.style})},[v.value?(t(),s(`canvas`,{key:0,ref_key:`sourceRef`,ref:b,layoutsubtree:`true`,class:`re-canvas-ui__source`},null,512)):r(``,!0),v.value?(t(),s(`div`,{key:1,ref_key:`contentRef`,ref:x,class:`re-canvas-ui__content`},[e(n.$slots,`default`,{},void 0,!0)],512)):(t(),s(`div`,{key:2,ref_key:`contentRef`,ref:x,class:`re-canvas-ui__content`},[e(n.$slots,`default`,{},void 0,!0)],512)),a(`canvas`,{ref_key:`outputRef`,ref:S,"aria-hidden":`true`,class:`re-canvas-ui__output`},null,512)],4))}}),[[`__scopeId`,`data-v-a86764ae`]]),de={install(e){e.component(`ReCanvasUi`,f)}},p=f;export{p as default,de as install};