package com.chua.shell.support.spring;

import com.chua.shell.support.shell.Shell;
import com.chua.starter.common.support.model.ModelView;
import com.chua.starter.common.support.utils.RequestUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * @author Administrator
 */
@Controller
@RequestMapping("${plugin.shell.server.context-path:/shell}")
public class ShellController {

    private Shell shell;

    @Resource
    private ShellProperties shellProperties;


    @GetMapping("index")
    public ModelView<String> index(HttpServletRequest request) {
        String ipAddr = RequestUtils.getIpAddress(request);
        if (shellProperties.getIpPass().contains(ipAddr)) {
            return new ModelView<>("shell", MediaType.TEXT_HTML);
        }

        return ModelView.create("无权限访问");
    }
}
