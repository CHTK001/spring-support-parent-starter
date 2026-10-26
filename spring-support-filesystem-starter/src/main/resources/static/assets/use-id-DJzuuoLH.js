import{Ot as e,k as t,w as n}from"./runtime-core.esm-bundler-Cy8JpWZa.js";import{a as r,n as i,s as a,w as o}from"./use-namespace-CeGq7iJQ.js";var s={prefix:Math.floor(Math.random()*1e4),current:0},c=Symbol(`elIdInjection`),l=()=>n()?t(c,s):s,u=t=>{let n=l();!a&&n===s&&o(`IdInjection`,`Looks like you are using server rendering, you must provide a id provider to ensure the hydration process to be succeed
usage: app.provide(ID_INJECTION_KEY, {
  prefix: number,
  current: number,
})`);let c=i();return r(()=>e(t)||`${c.value}-id-${n.prefix}-${n.current++}`)};export{l as n,u as t};