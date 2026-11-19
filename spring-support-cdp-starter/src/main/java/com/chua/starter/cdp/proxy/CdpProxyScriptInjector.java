package com.chua.starter.cdp.proxy;

import org.springframework.stereotype.Component;

import java.nio.charset.Charset;

/**
 * 实时页面脚本注入器
 *
 * <p>向上游返回的 HTML 注入两段内容：</p>
 * <ul>
 *   <li>{@code <base href="代理文档地址">}：让页面里的<b>文档相对地址</b>（{@code page2}、{@code ../x}）
 *       自然解析到代理路径空间，无需脚本参与；</li>
 *   <li>一段钩子脚本：把<b>根相对地址</b>（{@code /api/x}）与<b>绝对地址</b>（{@code https://...}）
 *       的 fetch / XHR / 点击 / 表单提交 / 动态属性改写到代理形式，防止绕过代理丢失共享会话。</li>
 * </ul>
 *
 * <p>代理地址形态：{@code /{sessionId}/{上游绝对地址，scheme 后单斜杠}}。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Component
public class CdpProxyScriptInjector {

    /**
     * 页面钩子脚本模板；{@code __SID__} / {@code __UPSTREAM__} 在注入时替换
     */
    private static final String HOOK_TEMPLATE = """
            (function(){
              var SID = "__SID__";
              var UP = "__UPSTREAM__";
              function abs(u){
                if(u.indexOf("//") === 0){ u = "https:" + u; }
                return u;
              }
              function upstreamForm(u){
                return u.replace(/^(https?:)\\/\\//i, "$1/");
              }
              function toProxy(u){
                try{
                  if(u == null){ return u; }
                  u = String(u);
                  if(u.indexOf("/" + SID + "/") === 0){ return u; }
                  var low = u.toLowerCase();
                  if(low.indexOf("data:") === 0 || low.indexOf("blob:") === 0 || low.indexOf("about:") === 0
                    || low.indexOf("javascript:") === 0 || low.indexOf("mailto:") === 0 || low.indexOf("tel:") === 0
                    || low.indexOf("ws:") === 0 || low.indexOf("wss:") === 0){ return u; }
                  if(u.charAt(0) === "#"){ return u; }
                  if(/^(https?:)\\/\\//i.test(u)){
                    return "/" + SID + "/" + upstreamForm(u);
                  }
                  if(u.charAt(0) === "/"){
                    return "/" + SID + "/" + upstreamForm(UP) + u;
                  }
                  return u;
                }catch(e){ return u; }
              }
              var _fetch = window.fetch;
              if(_fetch){
                window.fetch = function(input, init){
                  try{
                    if(typeof input === "string" || input instanceof URL){
                      input = toProxy(String(input));
                    } else if(input && input.url){
                      var nu = toProxy(input.url);
                      if(nu !== input.url){ input = new Request(nu, input); }
                    }
                  }catch(e){ /* 忽略：fetch 入参改写失败时保持原请求继续 */ }
                  return _fetch.call(window, input, init);
                };
              }
              var _open = XMLHttpRequest.prototype.open;
              XMLHttpRequest.prototype.open = function(){
                var args = Array.prototype.slice.call(arguments);
                try{ args[1] = toProxy(args[1]); }catch(e){ /* 忽略：XHR 地址改写失败时保持原地址 */ }
                return _open.apply(this, args);
              };
              var ATTRS = ["src","href","action","formaction","data-src","data-url","poster"];
              function fix(el, attr){
                try{
                  var v = el.getAttribute(attr);
                  if(!v){ return; }
                  var p = toProxy(v);
                  if(p !== v){ el.setAttribute(attr, p); }
                }catch(e){ /* 忽略：单个属性修正失败不影响其余属性 */ }
              }
              function fixAll(el){
                if(!el || el.nodeType !== 1){ return; }
                for(var k=0;k<ATTRS.length;k++){ fix(el, ATTRS[k]); }
                var list = el.querySelectorAll("[src],[href],[action],[formaction],[data-src],[data-url],[poster]");
                for(var i=0;i<list.length;i++){
                  for(var j=0;j<ATTRS.length;j++){ fix(list[i], ATTRS[j]); }
                }
              }
              try{
                new MutationObserver(function(muts){
                  for(var i=0;i<muts.length;i++){
                    var m = muts[i];
                    if(m.type === "attributes"){ fix(m.target, m.attributeName); }
                    else { for(var j=0;j<m.addedNodes.length;j++){ fixAll(m.addedNodes[j]); } }
                  }
                }).observe(document.documentElement || document,
                  {childList:true, subtree:true, attributes:true, attributeFilter:ATTRS});
              }catch(e){ /* 忽略：浏览器不支持 MutationObserver 时跳过 DOM 监听 */ }
              document.addEventListener("click", function(e){
                try{
                  if(e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey){ return; }
                  var n = e.target;
                  while(n && n !== document){
                    if(n.tagName === "A" && n.getAttribute("href")){
                      var p = toProxy(n.getAttribute("href"));
                      if(p !== n.getAttribute("href")){
                        e.preventDefault();
                        window.location.href = p;
                      }
                      return;
                    }
                    n = n.parentNode;
                  }
                }catch(e){ /* 忽略：点击链接改写失败时不阻断默认跳转 */ }
              }, true);
              document.addEventListener("submit", function(e){
                try{
                  var f = e.target;
                  if(f && f.getAttribute){
                    var p = toProxy(f.getAttribute("action") || "");
                    if(p){ f.setAttribute("action", p); }
                  }
                }catch(e){ /* 忽略：表单提交地址改写失败时不阻断默认提交 */ }
              }, true);
            })();
            """;

    /**
     * 向 HTML 注入 {@code <base>} 与钩子脚本
     *
     * @param html           上游 HTML 文本
     * @param baseHref       代理视角的当前文档地址（作 {@code <base>}）
     * @param sessionId      会话标识
     * @param upstreamOrigin 上游源（{@code scheme://host[:port]}）
     * @return 注入后的 HTML
     */
    public String inject(String html, String baseHref, String sessionId, String upstreamOrigin) {
        String base = "<base href=\"" + escapeAttr(baseHref) + "\">";
        String script = "<script>" + HOOK_TEMPLATE
                .replace("__SID__", sessionId)
                .replace("__UPSTREAM__", upstreamOrigin)
                .replace("</", "<\\/") + "</script>";
        String block = base + script;
        String lower = html.toLowerCase();
        int head = lower.indexOf("<head");
        if (head >= 0) {
            int insert = html.indexOf('>', head);
            if (insert >= 0) {
                return html.substring(0, insert + 1) + block + html.substring(insert + 1);
            }
        }
        int htmlTag = lower.indexOf("<html");
        if (htmlTag >= 0) {
            int insert = html.indexOf('>', htmlTag);
            if (insert >= 0) {
                return html.substring(0, insert + 1) + "<head>" + block + "</head>" + html.substring(insert + 1);
            }
        }
        return block + html;
    }

    /**
     * 从 Content-Type 解析字符集（缺省 UTF-8）
     *
     * @param contentType 上游 Content-Type
     * @return 字符集名称
     */
    public String charsetOf(String contentType) {
        if (contentType != null) {
            for (String part : contentType.split(";")) {
                String item = part.trim().toLowerCase();
                if (item.startsWith("charset=")) {
                    String charset = item.substring("charset=".length()).replace("\"", "").trim();
                    if (!charset.isEmpty()) {
                        try {
                            Charset.forName(charset);
                            return charset;
                        } catch (Exception ignored) {
                            // 未知字符集回落 UTF-8
                        }
                    }
                }
            }
        }
        return "UTF-8";
    }

    /**
     * HTML 属性转义
     */
    private String escapeAttr(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }
}
