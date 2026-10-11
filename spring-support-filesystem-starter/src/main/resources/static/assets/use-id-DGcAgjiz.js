import{J as e,L as t,On as n}from"./runtime-core.esm-bundler-K64zC-lh.js";import{I as r,_ as i,b as a,o}from"./install-BresTzAU.js";var s={prefix:Math.floor(Math.random()*1e4),current:0},c=Symbol(`elIdInjection`),l=()=>t()?e(c,s):s,u=e=>{let t=l();!a&&t===s&&r(`IdInjection`,`Looks like you are using server rendering, you must provide a id provider to ensure the hydration process to be succeed
usage: app.provide(ID_INJECTION_KEY, {
  prefix: number,
  current: number,
})`);let c=o();return i(()=>n(e)||`${c.value}-id-${t.prefix}-${t.current++}`)};export{l as n,u as t};