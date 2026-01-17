package com.chua.starter.common.support.configuration.security;
import lombok.Data;
import com.chua.common.support.core.spi.ServiceProvider;
import com.chua.common.support.resilience.rate.RateLimiterProvider;
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

import lombok.extern.slf4j.Slf4j;

/**
 * Spring 版本的安全过滤器自动配置（SSRF@Slf4j
/路径穿越/上传绕过/DoS）
 */
@Configuration
public class SecurityFiltersAutoConfiguration {
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

	@Data
		public static class SecurityProps {
		private IpAccess ip = new IpAccess();
		private Ssrf ssrf = new Ssrf();
		private PathTraversal pathTraversal = new PathTraversal();
		private UploadBypass uploadBypass = new UploadBypass();
		private Dos dos = new Dos();

		@Data
				public static class IpAccess {
			private boolean enabled = false;
			private boolean rateLimitEnabled = false;
			private String limiterType = "TOKEN_BUCKET";
			private double defaultQps = 5.0;
			private Map<String, Double> ipQps = new HashMap<>();
			private Set<String> whitelist = new HashSet<>();
			private Set<String> blacklist = new HashSet<>();
		}

		@Data
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

		@Data
				public static class PathTraversal {
			private boolean enabled = true;
			private String rootDirectory = "/var/app/data";
			private Set<String> paramKeys = new HashSet<>(Arrays.asList("path", "file"));
		}

		@Data
				public static class UploadBypass {
			private boolean enabled = true;
			private Set<String> allowedExt = new HashSet<>(Arrays.asList(".jpg", ".png", ".txt", ".pdf"));
			private Set<String> deniedExt = new HashSet<>(Arrays.asList(".jsp", ".php", ".aspx", ".exe", ".sh", ".bat"));
			private Set<String> allowedContentType = new HashSet<>(Arrays.asList("image/", "text/", "application/pdf"));
			private long maxSizeBytes = 10 * 1024 * 1024;
		}

		@Data
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
}
