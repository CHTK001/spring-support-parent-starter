package com.chua.starter.common.support.configuration.security;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.task.limit.RateLimiterProvider;
import com.chua.spring.support.utils.RequestUtils;
import jakarta.servlet.Filter;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Spring 版本的安全过滤器自动配置（SSRF/路径穿越/上传绕过/DoS）
 */
@Configuration
public class SecurityFiltersAutoConfiguration {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SecurityFiltersAutoConfiguration.class);


	/**
	 * 安全配置 Bean，供过滤器注入使用
	 */
	@Bean
	public SecurityProps securityProps() {
		return new SecurityProps();
	}

	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE + 9)
	public Filter ipAccessFilter(SecurityProps props) {
		final ConcurrentMap<String, RateLimiterProvider> limiters = new ConcurrentHashMap<>();
		final var configFingerprint = new AtomicLong(0);
		return (ServletRequest req, ServletResponse res, jakarta.servlet.FilterChain chain) -> {
			HttpServletRequest request = (HttpServletRequest) req;
			HttpServletResponse response = (HttpServletResponse) res;
			SecurityProps.IpAccess p = props.getIp();
			if (p == null || !p.isEnabled()) { chain.doFilter(req, res); return; }

			long fp = fingerprint(p);
			long prev = configFingerprint.get();
			if (fp != prev && configFingerprint.compareAndSet(prev, fp)) {
				limiters.clear();
			}

			String ip = RequestUtils.getIpAddress(request);
			if (ip == null || ip.isEmpty()) { chain.doFilter(req, res); return; }

			if (p.getWhitelist() != null && p.getWhitelist().contains(ip)) { chain.doFilter(req, res); return; }
			if (p.getBlacklist() != null && p.getBlacklist().contains(ip)) { denyIp(response); return; }

			if (p.isRateLimitEnabled()) {
				double qps = resolveQps(ip, p);
				if (qps > 0) {
                    RateLimiterProvider limiter = limiters.computeIfAbsent(ip, key -> ServiceProvider.of(RateLimiterProvider.class).getNewExtension(p.getLimiterType(), qps, (long) Math.max(1, Math.floor(qps))));
					if (Math.abs(limiter.getRate() - qps) > 1e-9) { limiter.setRate(qps); }
					boolean ok = limiter.tryAcquire(1, java.util.concurrent.TimeUnit.MILLISECONDS);
					if (!ok) { tooMany(response); return; }
				}
			}

			chain.doFilter(req, res);
		};
	}

	private double resolveQps(String ip, SecurityProps.IpAccess p) {
		if (p.getIpQps() != null) {
			Double v = p.getIpQps().get(ip);
			if (v != null) return v;
		}
		return p.getDefaultQps();
	}

	private long fingerprint(SecurityProps.IpAccess p) {
		long h = Double.doubleToLongBits(p.getDefaultQps());
		h = 31 * h + Objects.hashCode(p.getLimiterType());
		h = 31 * h + (p.isRateLimitEnabled() ? 1 : 0);
		h = 31 * h + (p.getWhitelist() == null ? 0 : p.getWhitelist().size());
		h = 31 * h + (p.getBlacklist() == null ? 0 : p.getBlacklist().size());
		h = 31 * h + (p.getIpQps() == null ? 0 : p.getIpQps().size());
		return h;
	}

	private void denyIp(HttpServletResponse response) throws java.io.IOException {
		response.setStatus(403);
		response.setHeader("X-Blocked-Reason", "IP_BLACKLIST");
		response.setContentType("application/json");
		response.getWriter().write("{\"error\":\"IP blocked\"}");
	}

	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE + 10)
	public Filter ssrfFilter(SecurityProps props) {
		return (ServletRequest req, ServletResponse res, jakarta.servlet.FilterChain chain) -> {
			HttpServletRequest request = (HttpServletRequest) req;
			HttpServletResponse response = (HttpServletResponse) res;
			SecurityProps.Ssrf p = props.getSsrf();
			if (!p.isEnabled()) { chain.doFilter(req, res); return; }

			Set<String> keys = Optional.ofNullable(p.getParamKeys()).orElse(Collections.emptySet());
			for (String key : keys) {
				String urlStr = request.getParameter(key);
				if (urlStr == null || urlStr.isEmpty()) continue;
				if (!validateUrl(urlStr, p)) {
					response.setStatus(400);
					response.setHeader("X-Blocked-Reason", "SSRF");
					response.setContentType("application/json");
					response.getWriter().write("{\"error\":\"SSRF blocked\"}");
					return;
				}
			}
			chain.doFilter(req, res);
		};
	}

	private boolean validateUrl(String urlStr, SecurityProps.Ssrf cfg) {
		try {
			URI uri = URI.create(urlStr);
			String scheme = Optional.ofNullable(uri.getScheme()).orElse("").toLowerCase(Locale.ROOT);
			if (!cfg.getAllowedSchemes().contains(scheme)) return false;
			String host = uri.getHost(); if (host == null) return false;
			int port = uri.getPort(); if (port == -1) port = scheme.equals("https") ? 443 : 80;
			if (cfg.getAllowedPorts() != null && !cfg.getAllowedPorts().isEmpty() && !cfg.getAllowedPorts().contains(port)) return false;
			if (cfg.getDenyDomains() != null) for (String d : cfg.getDenyDomains()) if (host.endsWith(d)) return false;
			if (cfg.getAllowDomains() != null && !cfg.getAllowDomains().isEmpty()) { boolean ok=false; for (String d:cfg.getAllowDomains()) if (host.endsWith(d)) {ok=true;break;} if(!ok) return false; }
			InetAddress[] addrs = InetAddress.getAllByName(host);
			for (InetAddress a : addrs) {
				if (cfg.isBlockInternalIp() && (a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||a.isSiteLocalAddress())) return false;
				if (cfg.isBlockIpv6LinkLocal() && (a instanceof Inet6Address) && a.isLinkLocalAddress()) return false;
			}
			return true;
		} catch (Throwable e) { return false; }
	}

	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE + 11)
	public Filter pathTraversalFilter(SecurityProps props) {
		return (ServletRequest req, ServletResponse res, jakarta.servlet.FilterChain chain) -> {
			HttpServletRequest request = (HttpServletRequest) req;
			HttpServletResponse response = (HttpServletResponse) res;
			SecurityProps.PathTraversal p = props.getPathTraversal();
			if (!p.isEnabled()) { chain.doFilter(req, res); return; }
			java.nio.file.Path root = java.nio.file.Paths.get(p.getRootDirectory()).normalize().toAbsolutePath();
			for (String key : Optional.ofNullable(p.getParamKeys()).orElse(Collections.emptySet())) {
				String v = request.getParameter(key); if (v == null || v.isEmpty()) continue;
				try {
					java.nio.file.Path resolved = root.resolve(v).normalize().toAbsolutePath();
					if (!resolved.startsWith(root)) {
						response.setStatus(400);
						response.setHeader("X-Blocked-Reason", "PATH_TRAVERSAL");
						response.setContentType("application/json");
						response.getWriter().write("{\"error\":\"Path traversal blocked\"}");
						return;
					}
				} catch (Exception e) {
					response.setStatus(400);
					response.setHeader("X-Blocked-Reason", "PATH_TRAVERSAL");
					response.setContentType("application/json");
					response.getWriter().write("{\"error\":\"Path traversal blocked\"}");
					return;
				}
			}
			chain.doFilter(req, res);
		};
	}

	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE + 12)
	public Filter uploadBypassFilter(SecurityProps props) {
		return (ServletRequest req, ServletResponse res, jakarta.servlet.FilterChain chain) -> {
			HttpServletRequest request = (HttpServletRequest) req;
			HttpServletResponse response = (HttpServletResponse) res;
			SecurityProps.UploadBypass p = props.getUploadBypass();
			if (!p.isEnabled()) { chain.doFilter(req, res); return; }

			String filename = request.getParameter("filename");
			String contentType = request.getContentType();
			String sizeStr = request.getParameter("filesize");
			long size = 0; try { size = sizeStr==null?0:Long.parseLong(sizeStr); } catch (Exception ignored) {}

			if (filename != null) {
				String lower = filename.toLowerCase(Locale.ROOT);
				String ext = lower.lastIndexOf('.')==-1?"":lower.substring(lower.lastIndexOf('.'));
				if (p.getDeniedExt()!=null && p.getDeniedExt().contains(ext)) { deny(response); return; }
				if (p.getAllowedExt()!=null && !p.getAllowedExt().isEmpty() && !p.getAllowedExt().contains(ext)) { deny(response); return; }
			}
			if (contentType != null && p.getAllowedContentType()!=null && !p.getAllowedContentType().isEmpty()) {
				boolean ok=false; for (String prefix: p.getAllowedContentType()) { if (contentType.startsWith(prefix)) { ok=true; break; } }
				if (!ok) { deny(response); return; }
			}
			if (p.getMaxSizeBytes()>0 && size>p.getMaxSizeBytes()) { deny(response); return; }

			chain.doFilter(req, res);
		};
	}

	private void deny(HttpServletResponse response) throws IOException {
		response.setStatus(400);
		response.setHeader("X-Blocked-Reason", "UPLOAD_BYPASS");
		response.setContentType("application/json");
		response.getWriter().write("{\"error\":\"Upload blocked\"}");
	}

	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE + 13)
	public Filter dosFilter(SecurityProps props) {
		final Map<String, Window> windows = new ConcurrentHashMap<>();
		return (ServletRequest req, ServletResponse res, jakarta.servlet.FilterChain chain) -> {
			HttpServletRequest request = (HttpServletRequest) req;
			HttpServletResponse response = (HttpServletResponse) res;
			SecurityProps.Dos p = props.getDos();
			if (!p.isEnabled()) { chain.doFilter(req, res); return; }

			String ip = RequestUtils.getIpAddress(request);
			long now = System.currentTimeMillis();
			Window w = windows.computeIfAbsent(ip, k -> new Window());
			synchronized (w) {
				if (w.blockUntil > now) { tooMany(response); return; }
				if (now - w.start >= p.getWindowMs()) { w.start = now; w.count = 0; }
				w.count++;
				if (w.count > p.getMaxRequests()) { w.blockUntil = now + p.getBlockMs(); tooMany(response); return; }
			}
			chain.doFilter(req, res);
		};
	}

	private void tooMany(HttpServletResponse response) throws IOException {
		response.setStatus(429);
		response.setHeader("X-Blocked-Reason", "DOS");
		response.setContentType("application/json");
		response.getWriter().write("{\"error\":\"Too Many Requests\"}");
	}

		public static class SecurityProps {
		private IpAccess ip = new IpAccess();
		private Ssrf ssrf = new Ssrf();
		private PathTraversal pathTraversal = new PathTraversal();
		private UploadBypass uploadBypass = new UploadBypass();
		private Dos dos = new Dos();

				public static class IpAccess {
			private boolean enabled = false;
			private boolean rateLimitEnabled = false;
			private String limiterType = "TOKEN_BUCKET";
			private double defaultQps = 5.0;
			private Map<String, Double> ipQps = new HashMap<>();
			private Set<String> whitelist = new HashSet<>();
			private Set<String> blacklist = new HashSet<>();
		}

				public static class Ssrf {
			private boolean enabled = true;
			private Set<String> paramKeys = new HashSet<>(Arrays.asList("url", "target"));
			private Set<String> allowedSchemes = new HashSet<>(Arrays.asList("http", "https"));
			private Set<Integer> allowedPorts = new HashSet<>(Arrays.asList(80, 443));
			private Set<String> allowDomains = new HashSet<>();
			private Set<String> denyDomains = new HashSet<>(Collections.singletonList(".internal.local"));
			private boolean blockInternalIp = true;
			private boolean blockIpv6LinkLocal = true;
		}

				public static class PathTraversal {
			private boolean enabled = true;
			private String rootDirectory = "/var/app/data";
			private Set<String> paramKeys = new HashSet<>(Arrays.asList("path", "file"));
		}

				public static class UploadBypass {
			private boolean enabled = true;
			private Set<String> allowedExt = new HashSet<>(Arrays.asList(".jpg", ".png", ".txt", ".pdf"));
			private Set<String> deniedExt = new HashSet<>(Arrays.asList(".jsp", ".php", ".aspx", ".exe", ".sh", ".bat"));
			private Set<String> allowedContentType = new HashSet<>(Arrays.asList("image/", "text/", "application/pdf"));
			private long maxSizeBytes = 10 * 1024 * 1024;
		}

				public static class Dos {
			private boolean enabled = true;
			private long windowMs = 60_000;
			private int maxRequests = 100;
			private long blockMs = 300_000;
		}
	}

	private static class Window {
		long start = System.currentTimeMillis();
		int count = 0;
		long blockUntil = 0;
	}
    /**
     * 获取 limiters
     *
     * @return limiters
     */
    public ConcurrentMap<String, RateLimiterProvider> getLimiters() {
        return limiters;
    }

    /**
     * 设置 limiters
     *
     * @param limiters limiters
     */
    public void setLimiters(ConcurrentMap<String, RateLimiterProvider> limiters) {
        this.limiters = limiters;
    }

    /**
     * 获取 configFingerprint
     *
     * @return configFingerprint
     */
    public var getConfigFingerprint() {
        return configFingerprint;
    }

    /**
     * 设置 configFingerprint
     *
     * @param configFingerprint configFingerprint
     */
    public void setConfigFingerprint(var configFingerprint) {
        this.configFingerprint = configFingerprint;
    }

    /**
     * 获取 request
     *
     * @return request
     */
    public HttpServletRequest getRequest() {
        return request;
    }

    /**
     * 设置 request
     *
     * @param request request
     */
    public void setRequest(HttpServletRequest request) {
        this.request = request;
    }

    /**
     * 获取 response
     *
     * @return response
     */
    public HttpServletResponse getResponse() {
        return response;
    }

    /**
     * 设置 response
     *
     * @param response response
     */
    public void setResponse(HttpServletResponse response) {
        this.response = response;
    }

    /**
     * 获取 fp
     *
     * @return fp
     */
    public long getFp() {
        return fp;
    }

    /**
     * 设置 fp
     *
     * @param fp fp
     */
    public void setFp(long fp) {
        this.fp = fp;
    }

    /**
     * 获取 prev
     *
     * @return prev
     */
    public long getPrev() {
        return prev;
    }

    /**
     * 设置 prev
     *
     * @param prev prev
     */
    public void setPrev(long prev) {
        this.prev = prev;
    }

    /**
     * 获取 ip
     *
     * @return ip
     */
    public String getIp() {
        return ip;
    }

    /**
     * 设置 ip
     *
     * @param ip ip
     */
    public void setIp(String ip) {
        this.ip = ip;
    }

    /**
     * 获取 qps
     *
     * @return qps
     */
    public double getQps() {
        return qps;
    }

    /**
     * 设置 qps
     *
     * @param qps qps
     */
    public void setQps(double qps) {
        this.qps = qps;
    }

    /**
     * 获取 limiter
     *
     * @return limiter
     */
    public RateLimiterProvider getLimiter() {
        return limiter;
    }

    /**
     * 设置 limiter
     *
     * @param limiter limiter
     */
    public void setLimiter(RateLimiterProvider limiter) {
        this.limiter = limiter;
    }

    /**
     * 获取 ok
     *
     * @return ok
     */
    public boolean getOk() {
        return ok;
    }

    /**
     * 设置 ok
     *
     * @param ok ok
     */
    public void setOk(boolean ok) {
        this.ok = ok;
    }

    /**
     * 获取 v
     *
     * @return v
     */
    public Double getV() {
        return v;
    }

    /**
     * 设置 v
     *
     * @param v v
     */
    public void setV(Double v) {
        this.v = v;
    }

            /**
     * 获取 h
     *
     * @return h
     */
    public long getH() {
        return h;
    }

    /**
     * 设置 h
     *
     * @param h h
     */
    public void setH(long h) {
        this.h = h;
    }

            /**
     * 获取 request
     *
     * @return request
     */
    public HttpServletRequest getRequest() {
        return request;
    }

    /**
     * 设置 request
     *
     * @param request request
     */
    public void setRequest(HttpServletRequest request) {
        this.request = request;
    }

    /**
     * 获取 response
     *
     * @return response
     */
    public HttpServletResponse getResponse() {
        return response;
    }

    /**
     * 设置 response
     *
     * @param response response
     */
    public void setResponse(HttpServletResponse response) {
        this.response = response;
    }

    /**
     * 获取 keys
     *
     * @return keys
     */
    public Set<String> getKeys() {
        return keys;
    }

    /**
     * 设置 keys
     *
     * @param keys keys
     */
    public void setKeys(Set<String> keys) {
        this.keys = keys;
    }

    /**
     * 获取 urlStr
     *
     * @return urlStr
     */
    public String getUrlStr() {
        return urlStr;
    }

    /**
     * 设置 urlStr
     *
     * @param urlStr urlStr
     */
    public void setUrlStr(String urlStr) {
        this.urlStr = urlStr;
    }

    /**
     * 获取 uri
     *
     * @return uri
     */
    public URI getUri() {
        return uri;
    }

    /**
     * 设置 uri
     *
     * @param uri uri
     */
    public void setUri(URI uri) {
        this.uri = uri;
    }

    /**
     * 获取 scheme
     *
     * @return scheme
     */
    public String getScheme() {
        return scheme;
    }

    /**
     * 设置 scheme
     *
     * @param scheme scheme
     */
    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

            /**
     * 获取 host
     *
     * @return host
     */
    public String getHost() {
        return host;
    }

    /**
     * 设置 host
     *
     * @param host host
     */
    public void setHost(String host) {
        this.host = host;
    }

            /**
     * 获取 port
     *
     * @return port
     */
    public int getPort() {
        return port;
    }

    /**
     * 设置 port
     *
     * @param port port
     */
    public void setPort(int port) {
        this.port = port;
    }

                    /**
     * 获取 ok
     *
     * @return ok
     */
    public boolean getOk() {
        return ok;
    }

    /**
     * 设置 ok
     *
     * @param ok ok
     */
    public void setOk(boolean ok) {
        this.ok = ok;
    }

            /**
     * 获取 addrs
     *
     * @return addrs
     */
    public InetAddress[] getAddrs() {
        return addrs;
    }

    /**
     * 设置 addrs
     *
     * @param addrs addrs
     */
    public void setAddrs(InetAddress[] addrs) {
        this.addrs = addrs;
    }

                                    /**
     * 获取 request
     *
     * @return request
     */
    public HttpServletRequest getRequest() {
        return request;
    }

    /**
     * 设置 request
     *
     * @param request request
     */
    public void setRequest(HttpServletRequest request) {
        this.request = request;
    }

    /**
     * 获取 response
     *
     * @return response
     */
    public HttpServletResponse getResponse() {
        return response;
    }

    /**
     * 设置 response
     *
     * @param response response
     */
    public void setResponse(HttpServletResponse response) {
        this.response = response;
    }

    /**
     * 获取 v
     *
     * @return v
     */
    public String getV() {
        return v;
    }

    /**
     * 设置 v
     *
     * @param v v
     */
    public void setV(String v) {
        this.v = v;
    }

    /**
     * 获取 request
     *
     * @return request
     */
    public HttpServletRequest getRequest() {
        return request;
    }

    /**
     * 设置 request
     *
     * @param request request
     */
    public void setRequest(HttpServletRequest request) {
        this.request = request;
    }

    /**
     * 获取 response
     *
     * @return response
     */
    public HttpServletResponse getResponse() {
        return response;
    }

    /**
     * 设置 response
     *
     * @param response response
     */
    public void setResponse(HttpServletResponse response) {
        this.response = response;
    }

    /**
     * 获取 filename
     *
     * @return filename
     */
    public String getFilename() {
        return filename;
    }

    /**
     * 设置 filename
     *
     * @param filename filename
     */
    public void setFilename(String filename) {
        this.filename = filename;
    }

    /**
     * 获取 contentType
     *
     * @return contentType
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * 设置 contentType
     *
     * @param contentType contentType
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    /**
     * 获取 sizeStr
     *
     * @return sizeStr
     */
    public String getSizeStr() {
        return sizeStr;
    }

    /**
     * 设置 sizeStr
     *
     * @param sizeStr sizeStr
     */
    public void setSizeStr(String sizeStr) {
        this.sizeStr = sizeStr;
    }

    /**
     * 获取 size
     *
     * @return size
     */
    public long getSize() {
        return size;
    }

    /**
     * 设置 size
     *
     * @param size size
     */
    public void setSize(long size) {
        this.size = size;
    }

    /**
     * 获取 lower
     *
     * @return lower
     */
    public String getLower() {
        return lower;
    }

    /**
     * 设置 lower
     *
     * @param lower lower
     */
    public void setLower(String lower) {
        this.lower = lower;
    }

    /**
     * 获取 ext
     *
     * @return ext
     */
    public String getExt() {
        return ext;
    }

    /**
     * 设置 ext
     *
     * @param ext ext
     */
    public void setExt(String ext) {
        this.ext = ext;
    }

    /**
     * 获取 ok
     *
     * @return ok
     */
    public boolean getOk() {
        return ok;
    }

    /**
     * 设置 ok
     *
     * @param ok ok
     */
    public void setOk(boolean ok) {
        this.ok = ok;
    }

    /**
     * 获取 windows
     *
     * @return windows
     */
    public Map<String, Window> getWindows() {
        return windows;
    }

    /**
     * 设置 windows
     *
     * @param windows windows
     */
    public void setWindows(Map<String, Window> windows) {
        this.windows = windows;
    }

    /**
     * 获取 request
     *
     * @return request
     */
    public HttpServletRequest getRequest() {
        return request;
    }

    /**
     * 设置 request
     *
     * @param request request
     */
    public void setRequest(HttpServletRequest request) {
        this.request = request;
    }

    /**
     * 获取 response
     *
     * @return response
     */
    public HttpServletResponse getResponse() {
        return response;
    }

    /**
     * 设置 response
     *
     * @param response response
     */
    public void setResponse(HttpServletResponse response) {
        this.response = response;
    }

    /**
     * 获取 ip
     *
     * @return ip
     */
    public String getIp() {
        return ip;
    }

    /**
     * 设置 ip
     *
     * @param ip ip
     */
    public void setIp(String ip) {
        this.ip = ip;
    }

    /**
     * 获取 now
     *
     * @return now
     */
    public long getNow() {
        return now;
    }

    /**
     * 设置 now
     *
     * @param now now
     */
    public void setNow(long now) {
        this.now = now;
    }

    /**
     * 获取 w
     *
     * @return w
     */
    public Window getW() {
        return w;
    }

    /**
     * 设置 w
     *
     * @param w w
     */
    public void setW(Window w) {
        this.w = w;
    }

    /**
     * 获取 ip
     *
     * @return ip
     */
    public IpAccess getIp() {
        return ip;
    }

    /**
     * 设置 ip
     *
     * @param ip ip
     */
    public void setIp(IpAccess ip) {
        this.ip = ip;
    }

    /**
     * 获取 ssrf
     *
     * @return ssrf
     */
    public Ssrf getSsrf() {
        return ssrf;
    }

    /**
     * 设置 ssrf
     *
     * @param ssrf ssrf
     */
    public void setSsrf(Ssrf ssrf) {
        this.ssrf = ssrf;
    }

    /**
     * 获取 pathTraversal
     *
     * @return pathTraversal
     */
    public PathTraversal getPathTraversal() {
        return pathTraversal;
    }

    /**
     * 设置 pathTraversal
     *
     * @param pathTraversal pathTraversal
     */
    public void setPathTraversal(PathTraversal pathTraversal) {
        this.pathTraversal = pathTraversal;
    }

    /**
     * 获取 uploadBypass
     *
     * @return uploadBypass
     */
    public UploadBypass getUploadBypass() {
        return uploadBypass;
    }

    /**
     * 设置 uploadBypass
     *
     * @param uploadBypass uploadBypass
     */
    public void setUploadBypass(UploadBypass uploadBypass) {
        this.uploadBypass = uploadBypass;
    }

    /**
     * 获取 dos
     *
     * @return dos
     */
    public Dos getDos() {
        return dos;
    }

    /**
     * 设置 dos
     *
     * @param dos dos
     */
    public void setDos(Dos dos) {
        this.dos = dos;
    }

    /**
     * 获取 enabled
     *
     * @return enabled
     */
    public boolean getEnabled() {
        return enabled;
    }

    /**
     * 设置 enabled
     *
     * @param enabled enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取 rateLimitEnabled
     *
     * @return rateLimitEnabled
     */
    public boolean getRateLimitEnabled() {
        return rateLimitEnabled;
    }

    /**
     * 设置 rateLimitEnabled
     *
     * @param rateLimitEnabled rateLimitEnabled
     */
    public void setRateLimitEnabled(boolean rateLimitEnabled) {
        this.rateLimitEnabled = rateLimitEnabled;
    }

    /**
     * 获取 limiterType
     *
     * @return limiterType
     */
    public String getLimiterType() {
        return limiterType;
    }

    /**
     * 设置 limiterType
     *
     * @param limiterType limiterType
     */
    public void setLimiterType(String limiterType) {
        this.limiterType = limiterType;
    }

    /**
     * 获取 defaultQps
     *
     * @return defaultQps
     */
    public double getDefaultQps() {
        return defaultQps;
    }

    /**
     * 设置 defaultQps
     *
     * @param defaultQps defaultQps
     */
    public void setDefaultQps(double defaultQps) {
        this.defaultQps = defaultQps;
    }

    /**
     * 获取 ipQps
     *
     * @return ipQps
     */
    public Map<String, Double> getIpQps() {
        return ipQps;
    }

    /**
     * 设置 ipQps
     *
     * @param ipQps ipQps
     */
    public void setIpQps(Map<String, Double> ipQps) {
        this.ipQps = ipQps;
    }

    /**
     * 获取 whitelist
     *
     * @return whitelist
     */
    public Set<String> getWhitelist() {
        return whitelist;
    }

    /**
     * 设置 whitelist
     *
     * @param whitelist whitelist
     */
    public void setWhitelist(Set<String> whitelist) {
        this.whitelist = whitelist;
    }

    /**
     * 获取 blacklist
     *
     * @return blacklist
     */
    public Set<String> getBlacklist() {
        return blacklist;
    }

    /**
     * 设置 blacklist
     *
     * @param blacklist blacklist
     */
    public void setBlacklist(Set<String> blacklist) {
        this.blacklist = blacklist;
    }

    /**
     * 获取 enabled
     *
     * @return enabled
     */
    public boolean getEnabled() {
        return enabled;
    }

    /**
     * 设置 enabled
     *
     * @param enabled enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取 paramKeys
     *
     * @return paramKeys
     */
    public Set<String> getParamKeys() {
        return paramKeys;
    }

    /**
     * 设置 paramKeys
     *
     * @param paramKeys paramKeys
     */
    public void setParamKeys(Set<String> paramKeys) {
        this.paramKeys = paramKeys;
    }

    /**
     * 获取 allowedSchemes
     *
     * @return allowedSchemes
     */
    public Set<String> getAllowedSchemes() {
        return allowedSchemes;
    }

    /**
     * 设置 allowedSchemes
     *
     * @param allowedSchemes allowedSchemes
     */
    public void setAllowedSchemes(Set<String> allowedSchemes) {
        this.allowedSchemes = allowedSchemes;
    }

    /**
     * 获取 allowedPorts
     *
     * @return allowedPorts
     */
    public Set<Integer> getAllowedPorts() {
        return allowedPorts;
    }

    /**
     * 设置 allowedPorts
     *
     * @param allowedPorts allowedPorts
     */
    public void setAllowedPorts(Set<Integer> allowedPorts) {
        this.allowedPorts = allowedPorts;
    }

    /**
     * 获取 allowDomains
     *
     * @return allowDomains
     */
    public Set<String> getAllowDomains() {
        return allowDomains;
    }

    /**
     * 设置 allowDomains
     *
     * @param allowDomains allowDomains
     */
    public void setAllowDomains(Set<String> allowDomains) {
        this.allowDomains = allowDomains;
    }

    /**
     * 获取 denyDomains
     *
     * @return denyDomains
     */
    public Set<String> getDenyDomains() {
        return denyDomains;
    }

    /**
     * 设置 denyDomains
     *
     * @param denyDomains denyDomains
     */
    public void setDenyDomains(Set<String> denyDomains) {
        this.denyDomains = denyDomains;
    }

    /**
     * 获取 blockInternalIp
     *
     * @return blockInternalIp
     */
    public boolean getBlockInternalIp() {
        return blockInternalIp;
    }

    /**
     * 设置 blockInternalIp
     *
     * @param blockInternalIp blockInternalIp
     */
    public void setBlockInternalIp(boolean blockInternalIp) {
        this.blockInternalIp = blockInternalIp;
    }

    /**
     * 获取 blockIpv6LinkLocal
     *
     * @return blockIpv6LinkLocal
     */
    public boolean getBlockIpv6LinkLocal() {
        return blockIpv6LinkLocal;
    }

    /**
     * 设置 blockIpv6LinkLocal
     *
     * @param blockIpv6LinkLocal blockIpv6LinkLocal
     */
    public void setBlockIpv6LinkLocal(boolean blockIpv6LinkLocal) {
        this.blockIpv6LinkLocal = blockIpv6LinkLocal;
    }

    /**
     * 获取 enabled
     *
     * @return enabled
     */
    public boolean getEnabled() {
        return enabled;
    }

    /**
     * 设置 enabled
     *
     * @param enabled enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取 rootDirectory
     *
     * @return rootDirectory
     */
    public String getRootDirectory() {
        return rootDirectory;
    }

    /**
     * 设置 rootDirectory
     *
     * @param rootDirectory rootDirectory
     */
    public void setRootDirectory(String rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    /**
     * 获取 paramKeys
     *
     * @return paramKeys
     */
    public Set<String> getParamKeys() {
        return paramKeys;
    }

    /**
     * 设置 paramKeys
     *
     * @param paramKeys paramKeys
     */
    public void setParamKeys(Set<String> paramKeys) {
        this.paramKeys = paramKeys;
    }

    /**
     * 获取 enabled
     *
     * @return enabled
     */
    public boolean getEnabled() {
        return enabled;
    }

    /**
     * 设置 enabled
     *
     * @param enabled enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取 allowedExt
     *
     * @return allowedExt
     */
    public Set<String> getAllowedExt() {
        return allowedExt;
    }

    /**
     * 设置 allowedExt
     *
     * @param allowedExt allowedExt
     */
    public void setAllowedExt(Set<String> allowedExt) {
        this.allowedExt = allowedExt;
    }

    /**
     * 获取 deniedExt
     *
     * @return deniedExt
     */
    public Set<String> getDeniedExt() {
        return deniedExt;
    }

    /**
     * 设置 deniedExt
     *
     * @param deniedExt deniedExt
     */
    public void setDeniedExt(Set<String> deniedExt) {
        this.deniedExt = deniedExt;
    }

    /**
     * 获取 allowedContentType
     *
     * @return allowedContentType
     */
    public Set<String> getAllowedContentType() {
        return allowedContentType;
    }

    /**
     * 设置 allowedContentType
     *
     * @param allowedContentType allowedContentType
     */
    public void setAllowedContentType(Set<String> allowedContentType) {
        this.allowedContentType = allowedContentType;
    }

    /**
     * 获取 maxSizeBytes
     *
     * @return maxSizeBytes
     */
    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }

    /**
     * 设置 maxSizeBytes
     *
     * @param maxSizeBytes maxSizeBytes
     */
    public void setMaxSizeBytes(long maxSizeBytes) {
        this.maxSizeBytes = maxSizeBytes;
    }

    /**
     * 获取 enabled
     *
     * @return enabled
     */
    public boolean getEnabled() {
        return enabled;
    }

    /**
     * 设置 enabled
     *
     * @param enabled enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取 windowMs
     *
     * @return windowMs
     */
    public long getWindowMs() {
        return windowMs;
    }

    /**
     * 设置 windowMs
     *
     * @param windowMs windowMs
     */
    public void setWindowMs(long windowMs) {
        this.windowMs = windowMs;
    }

    /**
     * 获取 maxRequests
     *
     * @return maxRequests
     */
    public int getMaxRequests() {
        return maxRequests;
    }

    /**
     * 设置 maxRequests
     *
     * @param maxRequests maxRequests
     */
    public void setMaxRequests(int maxRequests) {
        this.maxRequests = maxRequests;
    }

    /**
     * 获取 blockMs
     *
     * @return blockMs
     */
    public long getBlockMs() {
        return blockMs;
    }

    /**
     * 设置 blockMs
     *
     * @param blockMs blockMs
     */
    public void setBlockMs(long blockMs) {
        this.blockMs = blockMs;
    }

    /**
     * 获取 start
     *
     * @return start
     */
    public long getStart() {
        return start;
    }

    /**
     * 设置 start
     *
     * @param start start
     */
    public void setStart(long start) {
        this.start = start;
    }

    /**
     * 获取 count
     *
     * @return count
     */
    public int getCount() {
        return count;
    }

    /**
     * 设置 count
     *
     * @param count count
     */
    public void setCount(int count) {
        this.count = count;
    }

    /**
     * 获取 blockUntil
     *
     * @return blockUntil
     */
    public long getBlockUntil() {
        return blockUntil;
    }

    /**
     * 设置 blockUntil
     *
     * @param blockUntil blockUntil
     */
    public void setBlockUntil(long blockUntil) {
        this.blockUntil = blockUntil;
    }


}


