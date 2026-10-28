import{Ht as e,It as t,Kt as n,Nt as r,On as i,St as a,Tn as o,Wt as s,_t as c,ct as l,gt as u,hn as d,ht as f,in as p,mt as m,nn as h,vt as g,wt as _,xt as v}from"./lib-antv-DPUBrK1v.js";import{yt as y}from"./src-Dl-Rs4v8.js";import{w as b,x}from"./src-b_plcSPt.js";import{t as S}from"./_plugin-vue_export-helper-BDNMzG2s.js";var C=`\\+*?()[]{}|^$.`;function w(e){let t=(e||``).trim();if(!t)return``;let n=Math.max(t.lastIndexOf(`/`),t.lastIndexOf(`\\`)),r=n>=0?t.slice(n+1):t;if(!r)return``;let i=``;for(let e of r)C.includes(e)&&(i+=`\\`),i+=e;let a=i.charAt(0);return/[0-9A-Za-z]/.test(a)?`[${a}]${i.slice(1)}`:i}var T=`if [ -f app.pid ]; then
  pid=$(cat app.pid)
  kill $pid 2>/dev/null
  i=0
  while kill -0 $pid 2>/dev/null && [ $i -lt 10 ]; do
    sleep 1
    i=$((i + 1))
  done
  kill -9 $pid 2>/dev/null
  rm -f app.pid
fi
# >>> ORPHAN_REAPER
# 兜底：app.pid 陈旧/丢失时回收本项目残留进程。模式首字符用字符类以免匹配到脚本自身；
# 并限定「工作目录=本项目目录」，避免误杀主入口同名的其它项目。
if [ -d /proc ] && [ -n '__PATH__' ]; then
  for __p in $(pgrep -f '__SELFPATTERN__' 2>/dev/null); do
    [ "$__p" = "$$" ] && continue
    [ "$(readlink /proc/$__p/cwd 2>/dev/null)" = '__PATH__' ] && kill -9 "$__p" 2>/dev/null
  done
else
  pkill -9 -f '__SELFPATTERN__' 2>/dev/null
fi
true
# <<< ORPHAN_REAPER`,E=`if [ -f app.pid ] && kill -0 $(cat app.pid) 2>/dev/null; then
  echo running
else
  echo stopped
fi`,D=`cd '__PATH__' 2>/dev/null || true;
`,O=[{id:`linux-springboot`,name:`SpringBoot 单体 (java -jar)`,desc:`后台运行 jar，PID 写入 app.pid，含 prod profile`,os:`linux`,types:[`springboot`],needEntry:!0,start:`${D}nohup java __JVMARGS__ -jar __ENTRY__ --spring.profiles.active=prod __ARGS__ __LOGOP__ __LOG__ 2>&1 &
echo $! > app.pid`,stop:T,restart:`${T}
sleep 2
${D}nohup java __JVMARGS__ -jar __ENTRY__ --spring.profiles.active=prod __ARGS__ __LOGOP__ __LOG__ 2>&1 &
echo $! > app.pid`,status:E},{id:`linux-fatjar`,name:`FatJar (java -jar)`,desc:`后台运行通用 jar，PID 写入 app.pid（不带 profile）`,os:`linux`,types:[`fatjar`,`springboot`],needEntry:!0,start:`${D}nohup java __JVMARGS__ -jar __ENTRY__ __ARGS__ __LOGOP__ __LOG__ 2>&1 &
echo $! > app.pid`,stop:T,restart:`${T}
sleep 2
${D}nohup java __JVMARGS__ -jar __ENTRY__ __ARGS__ __LOGOP__ __LOG__ 2>&1 &
echo $! > app.pid`,status:E},{id:`linux-binary`,name:`可执行二进制 (分离式)`,desc:`直接后台运行主入口二进制，PID 写入 app.pid`,os:`linux`,types:[`separated`],needEntry:!0,start:`${D}chmod +x __ENTRY__;
nohup ./__ENTRY__ __ARGS__ __LOGOP__ __LOG__ 2>&1 &
echo $! > app.pid`,stop:T,restart:`${T}
sleep 2
${D}chmod +x __ENTRY__;
nohup ./__ENTRY__ __ARGS__ __LOGOP__ __LOG__ 2>&1 &
echo $! > app.pid`,status:E},{id:`linux-shell`,name:`自带脚本 (start.sh/stop.sh)`,desc:`调用项目自带的 start.sh / stop.sh / restart.sh`,os:`linux`,types:[`separated`,`*`],needEntry:!1,start:`chmod +x *.sh
./start.sh`,stop:`chmod +x *.sh
./stop.sh`,restart:`chmod +x *.sh
./restart.sh`,status:`[ -f app.pid ] && kill -0 $(cat app.pid) 2>/dev/null && echo running || echo stopped`},{id:`linux-node`,name:`Node 应用`,desc:`后台运行 node 主入口（如 app.js），PID 写入 app.pid`,os:`linux`,types:[`separated`,`*`],needEntry:!0,start:`${D}nohup node __ENTRY__ __ARGS__ __LOGOP__ __LOG__ 2>&1 &
echo $! > app.pid`,stop:T,restart:`${T}
sleep 2
${D}nohup node __ENTRY__ __ARGS__ __LOGOP__ __LOG__ 2>&1 &
echo $! > app.pid`,status:E},{id:`linux-static-nginx`,name:`静态页面 (Nginx 托管)`,desc:`使用本机 Nginx 托管静态站点；状态/停止只认本项目 logs/nginx.pid，避免误杀/误报其它项目的 nginx`,os:`linux`,types:[`static`,`nginx`],needEntry:!1,start:`nginx`,stop:`nginx -s stop 2>/dev/null
if [ -f logs/nginx.pid ]; then kill $(cat logs/nginx.pid) 2>/dev/null; rm -f logs/nginx.pid; fi`,restart:`if [ -f logs/nginx.pid ] && kill -0 $(cat logs/nginx.pid) 2>/dev/null; then
  nginx -s reload
else
  nginx
fi`,status:`if [ -f logs/nginx.pid ] && kill -0 $(cat logs/nginx.pid) 2>/dev/null; then
  echo running
else
  echo stopped
fi`},{id:`linux-static-python`,name:`静态页面 (Python http.server)`,desc:`无需 Nginx，python3 内置 HTTP 服务监听 80 端口`,os:`linux`,types:[`static`],needEntry:!1,start:`${D}nohup python3 -m http.server 80 __LOGOP__ __LOG__ 2>&1 &
echo $! > app.pid`,stop:T,restart:`${T}
sleep 2
${D}nohup python3 -m http.server 80 __LOGOP__ __LOG__ 2>&1 &
echo $! > app.pid`,status:E},{id:`linux-python`,name:`Python 应用`,desc:`后台运行 python3 主入口（如 main.py / app.py），PID 写入 app.pid`,os:`linux`,types:[`separated`,`*`],needEntry:!0,start:`${D}nohup python3 __ENTRY__ __ARGS__ __LOGOP__ __LOG__ 2>&1 &
echo $! > app.pid`,stop:T,restart:`${T}
sleep 2
${D}nohup python3 __ENTRY__ __ARGS__ __LOGOP__ __LOG__ 2>&1 &
echo $! > app.pid`,status:`pgrep -f 'python3 __ENTRY__' >/dev/null 2>&1 && echo running || echo stopped`},{id:`linux-docker-compose`,name:`Docker Compose 编排`,desc:`docker compose 管理（up -d / down / restart）`,os:`linux`,types:[`*`],needEntry:!1,start:`docker compose up -d`,stop:`docker compose down`,restart:`docker compose restart`,status:`docker compose ps --status running --quiet | grep -q . && echo running || echo stopped`},{id:`linux-systemd`,name:`systemd 服务`,desc:`通过 systemctl 管理（需已注册同名服务，需要 root）`,os:`linux`,types:[`*`],needEntry:!1,start:`systemctl start __APPNAME__`,stop:`systemctl stop __APPNAME__`,restart:`systemctl restart __APPNAME__`,status:`systemctl is-active __APPNAME__`}],k=`$ErrorActionPreference = 'SilentlyContinue'
if (Test-Path 'app.pid') { Get-Content 'app.pid' | ForEach-Object { & taskkill.exe /PID $_ /T /F | Out-Null }; Remove-Item 'app.pid' -Force }
# >>> ORPHAN_REAPER
# 兜底：app.pid 陈旧/丢失时按命令行回收本项目残留进程（CIM 不可用时静默跳过）
try {
  $re = '__SELFPATTERN__'
  foreach ($op in (Get-CimInstance Win32_Process -ErrorAction Stop |
      Where-Object { $_.CommandLine -and $_.ProcessId -ne $PID -and $_.CommandLine -match $re })) {
    & taskkill.exe /PID $op.ProcessId /T /F | Out-Null
  }
} catch { }
# <<< ORPHAN_REAPER`,A=`${k}
exit 0`,j=`if (Test-Path 'app.pid') { if (Get-Process -Id (Get-Content 'app.pid') -ErrorAction SilentlyContinue) { 'running' } else { 'stopped' } } else { 'stopped' }`,M=(e,t)=>`$all = ${t}
$argLine = $all -join ' '
$inner = '${e} ' + $argLine + ' __LOGOP__ "__LOG__" 2>&1'
$cmdLine = 'cmd /c ' + $inner
$r = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{ CommandLine = $cmdLine; CurrentDirectory = '__PATH__' }
if ($r.ReturnValue -ne 0) {
  $wmiCode = $r.ReturnValue
  $sp = Start-Process -FilePath 'cmd.exe' -ArgumentList ('/c ' + $inner) -WorkingDirectory '__PATH__' -WindowStyle Hidden -PassThru
  $r = [pscustomobject]@{ ReturnValue = 0; ProcessId = $sp.Id }
  Write-Output ('WMI 创建进程被拒(code=' + $wmiCode + ')，已回退 Start-Process pid=' + $sp.Id)
}
if ($r.ReturnValue -ne 0) { throw ('启动失败 Win32_Process.Create code=' + $r.ReturnValue) }
$r.ProcessId | Out-File -FilePath 'app.pid' -Encoding ascii
Write-Output $r.ProcessId`,N=[{id:`win-springboot`,name:`SpringBoot / FatJar (java -jar)`,desc:`隐藏窗口运行 jar，按命令行匹配停止进程`,os:`windows`,types:[`springboot`,`fatjar`],needEntry:!0,start:M(`java`,`@($JvmArgs) + @('-jar','"__ENTRY__"') + @($AppArgs)`),stop:A,restart:`${k}
Start-Sleep -Seconds 2
${M(`java`,`@($JvmArgs) + @('-jar','"__ENTRY__"') + @($AppArgs)`)}`,status:j},{id:`win-ps1`,name:`自带脚本 (start.ps1/stop.ps1)`,desc:`调用项目自带的 PowerShell 脚本`,os:`windows`,types:[`separated`,`*`],needEntry:!1,start:`.start.ps1`,stop:`.stop.ps1`,restart:`.\restart.ps1`,status:`if (Get-Process | Where-Object { $_.ProcessName -like '*__APPNAME__*' }) { 'running' } else { 'stopped' }`},{id:`win-node`,name:`Node 应用`,desc:`隐藏窗口运行 node 主入口，按命令行匹配停止`,os:`windows`,types:[`separated`,`*`],needEntry:!0,start:M(`node`,`@('"__ENTRY__"') + @(__ARGS__)`),stop:A,restart:`${k}
Start-Sleep -Seconds 2
${M(`node`,`@('"__ENTRY__"') + @(__ARGS__)`)}`,status:j},{id:`win-nginx`,name:`静态页面 / Nginx`,desc:`在项目目录下经 WMI 脱离会话启动 nginx，按 app.pid 停止/判状态`,os:`windows`,types:[`static`,`nginx`],needEntry:!1,start:`$cmd = 'cmd /c nginx.exe __LOGOP__ "__LOG__" 2>&1'
$r = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{ CommandLine = $cmd; CurrentDirectory = '__PATH__' }
if ($r.ReturnValue -ne 0) { throw ('启动失败 Win32_Process.Create code=' + $r.ReturnValue) }
$r.ProcessId | Out-File -FilePath 'app.pid' -Encoding ascii
Write-Output $r.ProcessId`,stop:A,restart:`${k}
Start-Sleep -Seconds 2
$cmd = 'cmd /c nginx.exe __LOGOP__ "__LOG__" 2>&1'
$r = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{ CommandLine = $cmd; CurrentDirectory = '__PATH__' }
if ($r.ReturnValue -ne 0) { throw ('启动失败 Win32_Process.Create code=' + $r.ReturnValue) }
$r.ProcessId | Out-File -FilePath 'app.pid' -Encoding ascii
Write-Output $r.ProcessId`,status:j},{id:`win-python`,name:`Python 应用`,desc:`隐藏窗口运行 python 主入口，按命令行匹配停止`,os:`windows`,types:[`separated`,`*`],needEntry:!0,start:M(`python`,`@('"__ENTRY__"') + @(__ARGS__)`),stop:A,restart:`${k}
Start-Sleep -Seconds 2
${M(`python`,`@('"__ENTRY__"') + @(__ARGS__)`)}`,status:j},{id:`win-docker-compose`,name:`Docker Compose 编排`,desc:`docker compose 管理（up -d / down / restart）`,os:`windows`,types:[`*`],needEntry:!1,start:`docker compose up -d`,stop:`docker compose down`,restart:`docker compose restart`,status:`if (docker compose ps --status running --quiet) { 'running' } else { 'stopped' }`},{id:`win-svc`,name:`Windows 服务`,desc:`通过服务名管理（需已注册同名服务）`,os:`windows`,types:[`*`],needEntry:!1,start:`Start-Service -Name '__APPNAME__'`,stop:`Stop-Service -Name '__APPNAME__'`,restart:`Restart-Service -Name '__APPNAME__' -Force`,status:`(Get-Service -Name '__APPNAME__').Status`}],P=[...O,...N];function ee(e,t){return P.filter(n=>n.os===e?n.types.includes(t)||n.types.includes(`*`):!1)}function F(e){let t=(e||``).trim();if(!t)return`app.log`;let n=/[\\/]$/.test(t)||!/\.[a-zA-Z0-9]+$/.test(t),r=t.includes(`\\`)&&!t.includes(`/`)?`\\`:`/`;return n?`${t.replace(/[\\/]+$/,``)}${r}app.log`:t}function te(e,t){let n=(t.entry||``).trim();if(e.needEntry&&!n)throw Error(`请先填写「主入口名称」（如 user-service.jar）`);let r={__ENTRY__:n,__LOG__:F(t.log),__PATH__:(t.path||``).trim(),__APPNAME__:(t.appName||`app`).trim(),__LOGOP__:t.logOp||`>`,__SELFPATTERN__:w(n)},i=e=>{let t=e.replace(/__ENTRY__|__LOG__|__PATH__|__APPNAME__|__LOGOP__|__SELFPATTERN__/g,e=>r[e]??e).replace(/^# (>>>|<<<) ORPHAN_REAPER$\n?/gm,``);return r.__SELFPATTERN__?t:t.replace(/[^\n]*# >>> ORPHAN_REAPER\n[\s\S]*?# <<< ORPHAN_REAPER\n?/g,``)};return{start:i(e.start),stop:i(e.stop),restart:i(e.restart),status:e.status?i(e.status):``}}var ne={class:`rc-toolbar`},re={class:`rc-label`},I={class:`rc-tools`},L={class:`rc-tpl-pop`},R={class:`rc-tpl-head`},z={key:0,class:`rc-tpl-sub`},B={key:1,class:`rc-tpl-list`},V=[`onClick`],H={class:`rc-tpl-name`},U={class:`rc-tpl-desc`},W={class:`rc-tok-pop`},ie=[`onClick`],ae=[`disabled`,`placeholder`,`rows`,`value`],G=S(_({__name:`index`,props:{modelValue:{default:``},os:{default:`linux`},projectType:{default:``},scriptKind:{default:`start`},context:{default:()=>({})},label:{},rows:{default:5},placeholder:{default:`请输入命令，或使用模板 / AI 生成`},ai:{type:Boolean,default:!0},disabled:{type:Boolean,default:!1},capability:{default:`chat`},noExpand:{type:Boolean,default:!1}},emits:[`update:modelValue`],setup(_,{emit:S}){let C=_,w=S,T=m(()=>Q.value===`windows`?`PowerShell 命令`:`bash 命令`),E=m(()=>({start:`VideoPlay`,stop:`VideoPause`,restart:`RefreshRight`,status:`Aim`})[C.scriptKind]);function D(e){w(`update:modelValue`,e.target.value)}function O(e){w(`update:modelValue`,e)}let k=d(!1),A=d(null),j=[{t:`__JVMARGS__`,d:`变量设置中的 JVM 参数（自动放在 -jar 之前）`},{t:`__ARGS__`,d:`变量设置中的应用 / 命令参数`},{t:`__ENTRY__`,d:`主入口名称（套用模板时替换）`},{t:`__PATH__`,d:`项目路径（套用模板时替换）`},{t:`__LOG__`,d:`日志文件路径（套用模板时替换）`},{t:`__APPNAME__`,d:`项目 / 应用名称（套用模板时替换）`}];function M(e){let t=A.value,n=C.modelValue||``;if(!t){O(n+e);return}let i=t.selectionStart??n.length,a=t.selectionEnd??n.length,o=n.slice(0,i)+e+n.slice(a);w(`update:modelValue`,o),r(()=>{t.focus();let n=i+e.length;t.setSelectionRange(n,n)})}let N=d(!1),P=m(()=>ee(C.os,C.projectType));function F(e){try{O(te(e,{entry:C.context.entry,log:C.context.log,path:C.context.path,appName:C.context.appName})[C.scriptKind]),N.value=!1,y.success(`已套用内置模板`)}catch(e){y.error(e?.message||`模板生成失败`)}}async function G(){let e=C.modelValue||``;if(!e){y.warning(`暂无内容可复制`);return}try{await navigator.clipboard.writeText(e),y.success(`已复制到剪贴板`)}catch{y.error(`复制失败`)}}function K(){C.modelValue&&O(``)}let q=d([]),J=d(``);async function Y(){if(C.ai)try{let e=await x().get(`/v2/command/models`).query({capability:C.capability}),t=b(e)||[];q.value=t,t.some(e=>(e.code||e.name)===J.value)||(J.value=t.length?t[0].code||t[0].name:``)}catch{q.value=[],J.value=``}}h(()=>C.capability,Y);let X=d(!1),Z=null,oe=m(()=>{let e=Q.value===`windows`?`PowerShell`:`bash`,t=C.context.entry?`主入口「${C.context.entry}」`:``,n=[t,C.context.path?`项目路径「${C.context.path}」`:``].filter(Boolean).join(`，`),r=n?`，${n}`:``;switch(C.scriptKind){case`start`:return`请生成${e}「启动」命令${r}：后台启动应用、日志重定向到日志文件、写入 pid，命令可重复执行。`;case`stop`:return`请生成${e}「停止」命令${t?`，针对${t}`:``}：优先根据 pid 文件停止，无 pid 时按进程名 / 命令行匹配停止，避免误杀其它进程。`;case`status`:return`请生成${e}「状态检测」命令${t?`，针对${t}`:``}：进程存在则输出 running，否则输出 stopped，只输出这一个词。`;default:return`请生成${e}「重启」命令${r}：先优雅停止再启动，pid 与服务名保持一致。`}}),Q=m(()=>C.os);function $(){Z=null,X.value=!1}function se(){if(X.value){Z?.close(),$();return}if(!q.value.length){y.warning(`暂无可用模型，请先在 AI 设置中配置并启用模型`);return}X.value=!0;let e={os:C.os,projectType:C.projectType,mainEntry:C.context.entry||``,projectPath:C.context.path||``,logPath:C.context.log||``,appName:C.context.appName||``,model:J.value,multi:!1,requirement:oe.value},t=``;try{Z=x().post(`/v2/command/generate`).data(e).sse({reconnect:!1,onMessage:e=>{let n=e.data;!n||typeof n!=`object`||(n.type===`chunk`&&n.content?(t+=n.content,O(t)):n.type===`done`?(n.full&&O(n.full),$(),y.success(`AI 已生成命令，请确认后使用`)):n.type===`error`&&(y.error(n.message||`生成失败`),$()))},onError:()=>{y.error(`AI 连接失败`),$()},onClose:()=>$()})}catch{y.error(`AI 调用失败`),$()}}return t(()=>{Z?.close()}),Y(),(t,r)=>{let d=n(`ReIcon`),m=n(`ReButton`),h=n(`ReEmpty`),y=n(`el-popover`),b=n(`ReOption`),x=n(`ReSelect`),S=n(`ReCommand`),C=n(`ReDialog`);return e(),g(`div`,{class:o([`re-command`,{"is-disabled":_.disabled}])},[f(`div`,ne,[f(`span`,re,[a(d,{name:E.value,class:`rc-label-icon`},null,8,[`name`]),v(` `+i(_.label||T.value),1)]),r[6]||=f(`span`,{class:`rc-spacer`},null,-1),f(`div`,I,[a(y,{visible:N.value,"onUpdate:visible":r[0]||=e=>N.value=e,width:330,placement:`bottom-end`,trigger:`click`},{reference:p(()=>[a(m,{icon:`Files`,layout:`square`,title:`内置模板`})]),default:p(()=>[f(`div`,L,[f(`div`,R,[v(` 内置模板 · `+i(Q.value===`windows`?`PowerShell`:`bash`)+` `,1),_.projectType?c(``,!0):(e(),g(`span`,z,`未选类型，仅通用模板`))]),P.value.length?(e(),g(`div`,B,[(e(!0),g(l,null,s(P.value,t=>(e(),g(`div`,{key:t.id,class:`rc-tpl-item`,onClick:e=>F(t)},[f(`div`,H,i(t.name),1),f(`div`,U,i(t.desc),1)],8,V))),128))])):(e(),u(h,{key:0,description:`当前条件暂无模板`,"image-size":56,style:{padding:`20px 0`}}))])]),_:1},8,[`visible`]),a(y,{width:350,placement:`bottom-end`,trigger:`click`},{reference:p(()=>[a(m,{icon:`Histogram`,layout:`square`,title:`插入占位符 / 内置变量`})]),default:p(()=>[f(`div`,W,[r[5]||=f(`div`,{class:`rc-tok-head`},`占位符 · 点击插入到光标处`,-1),(e(),g(l,null,s(j,e=>f(`div`,{key:e.t,class:`rc-tok-item`,onClick:t=>M(e.t)},[f(`code`,null,i(e.t),1),f(`span`,null,i(e.d),1)],8,ie)),64))])]),_:1}),_.ai&&q.value.length?(e(),g(l,{key:0},[a(x,{modelValue:J.value,"onUpdate:modelValue":r[1]||=e=>J.value=e,class:`rc-model`,style:{width:`150px`},placeholder:`选择模型`},{default:p(()=>[(e(!0),g(l,null,s(q.value,t=>(e(),u(b,{key:t.id,label:t.name,value:t.code||t.name},null,8,[`label`,`value`]))),128))]),_:1},8,[`modelValue`]),a(m,{icon:X.value?`Loading`:`ChatDotSquare`,loading:X.value,layout:`square`,title:X.value?`停止生成`:`AI 生成`,onClick:se},null,8,[`icon`,`loading`,`title`])],64)):c(``,!0),a(m,{icon:`CopyDocument`,layout:`square`,title:`复制`,onClick:G}),a(m,{icon:`Delete`,layout:`square`,title:`清空`,onClick:K}),_.noExpand?c(``,!0):(e(),u(m,{key:1,icon:`FullScreen`,layout:`square`,title:`放大编辑`,onClick:r[2]||=e=>k.value=!0}))])]),f(`textarea`,{ref_key:`taRef`,ref:A,class:`rc-editor`,disabled:_.disabled,placeholder:_.placeholder,rows:_.rows,spellcheck:`false`,value:_.modelValue,onInput:D},null,40,ae),_.noExpand?c(``,!0):(e(),u(C,{key:0,modelValue:k.value,"onUpdate:modelValue":r[4]||=e=>k.value=e,title:_.label||T.value,width:`92%`,"append-to-body":``,"close-on-click-modal":!1},{default:p(()=>[a(S,{"model-value":_.modelValue,os:Q.value,"project-type":_.projectType,"script-kind":_.scriptKind,context:_.context,label:_.label,rows:18,ai:_.ai,capability:_.capability,"no-expand":``,"onUpdate:modelValue":r[3]||=e=>w(`update:modelValue`,e)},null,8,[`model-value`,`os`,`project-type`,`script-kind`,`context`,`label`,`ai`,`capability`])]),_:1},8,[`modelValue`,`title`]))],2)}}}),[[`__scopeId`,`data-v-cedd9c0b`]]),K={install(e){e.component(`ReCommand`,G)}},q=G;export{q as default,K as install};