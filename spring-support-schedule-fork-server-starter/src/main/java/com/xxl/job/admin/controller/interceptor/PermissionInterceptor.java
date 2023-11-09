package com.xxl.job.admin.controller.interceptor;

import com.xxl.job.admin.controller.annotation.PermissionLimit;
import com.xxl.job.admin.core.model.XxlJobUser;
import com.xxl.job.admin.core.util.I18nUtil;
import com.xxl.job.admin.properties.SchedulerProperties;
import com.xxl.job.admin.service.LoginService;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.regex.Pattern;

/**
 * 权限拦截
 *
 * @author xuxueli 2015-12-12 18:09:04
 */
public class PermissionInterceptor implements AsyncHandlerInterceptor {

	@Resource
	private LoginService loginService;
	@Resource
	private SchedulerProperties schedulerProperties;

	private static final Pattern LOCAL_IP_PATTERN = Pattern.compile("127(\\.\\d{1,3}){3}$");
	private static final Pattern LOCAL_IP_PATTERN2 = Pattern.compile("192(\\.\\d{1,3}){3}$");
	public static final String LOCAL_HOST = "127.0.0.1";

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		
		if (!(handler instanceof HandlerMethod)) {
			return true;	// proceed with the next interceptor
		}

		// if need login
		boolean needLogin = true;
		boolean needAdminuser = false;
		HandlerMethod method = (HandlerMethod)handler;
		PermissionLimit permission = method.getMethodAnnotation(PermissionLimit.class);
		if (permission!=null) {
			needLogin = permission.limit();
			needAdminuser = permission.adminuser();
		}

		if(!schedulerProperties.isOpenLocalNetFilter()) {
			if(isLocalhost(request)) {
				if (needLogin) {
					XxlJobUser loginUser = loginService.ifLogin(request, response);
					if (loginUser != null) {
						request.setAttribute(LoginService.LOGIN_IDENTITY_KEY, loginUser);
					}
				}
				return true;
			}

		}

		if (needLogin) {
			XxlJobUser loginUser = loginService.ifLogin(request, response);
			if (loginUser == null) {
				response.setStatus(302);
				response.setHeader("location", request.getContextPath()+"/toLogin");
				return false;
			}
			if (needAdminuser && loginUser.getRole()!=1) {
				throw new RuntimeException(I18nUtil.getString("system_permission_limit"));
			}
			request.setAttribute(LoginService.LOGIN_IDENTITY_KEY, loginUser);
		}

		return true;	// proceed with the next interceptor
	}

	private boolean isLocalhost(HttpServletRequest request) {
		String url = RequestUtils.getIpAddress(request);
		return LOCAL_IP_PATTERN.matcher(url).matches() ||
				LOCAL_IP_PATTERN2.matcher(url).matches() ||
				LOCAL_HOST.equals(url);
	}

}
