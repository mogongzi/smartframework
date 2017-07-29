package org.smart4j.framework;

import org.apache.commons.lang3.StringUtils;
import org.smart4j.framework.bean.Data;
import org.smart4j.framework.bean.Handler;
import org.smart4j.framework.bean.Param;
import org.smart4j.framework.bean.View;
import org.smart4j.framework.helper.BeanHelper;
import org.smart4j.framework.helper.ControllerHelper;
import org.smart4j.framework.helper.RequestHelper;
import org.smart4j.framework.helper.UploadHelper;
import org.smart4j.framework.util.JsonUtil;
import org.smart4j.framework.util.ReflectionUtil;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Map;

@WebServlet(urlPatterns = "/*", loadOnStartup = 0)
public class DispatcherServlet extends HttpServlet {
    @Override
    public void init(ServletConfig config) throws ServletException {
        HelperLoader.init();
        ServletContext servletContext = config.getServletContext();
        ServletRegistration jspServlet = servletContext.getServletRegistration("jsp");
        jspServlet.addMapping(ConfigHelper.getAppJspPath() + "*");
        ServletRegistration defaultServlet = servletContext.getServletRegistration("default");
        defaultServlet.addMapping(ConfigHelper.getAppAssetPath() + "*");

        UploadHelper.init(servletContext);
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String requestMethod = req.getMethod().toLowerCase();
        String requestPath = req.getPathInfo();

        if (requestPath.equals("/favicon.ico")) {
            return;
        }

        Handler handler = ControllerHelper.getHandler(requestMethod, requestPath);
        if (handler != null) {
            Class<?> controllerClass = handler.getControllerClass();
            Object controllerBean = BeanHelper.getBean(controllerClass);

            Param param;
            if (UploadHelper.isMultipart(req)) {
                param = UploadHelper.createParam(req);
            } else {
                param = RequestHelper.createParam(req);
            }

            Object result;
            Method actionMethod = handler.getActionMethod();
            if (param.isEmpty()) {
                result = ReflectionUtil.invokeMethod(controllerBean, actionMethod);
            } else {
                result = ReflectionUtil.invokeMethod(controllerBean, actionMethod, param);
            }

            if (result instanceof View) {
                handleViewResult((View) result, req, resp);
            } else if (result instanceof Data) {
                handleDataResult((Data) result, req, resp);
            }
        }
    }

    private void handleViewResult(View view, HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        String path = view.getPath();
        if (StringUtils.isNoneEmpty(path)) {
            if (path.startsWith("/")) {
                resp.sendRedirect(req.getContextPath() + path);
            } else {
                Map<String, Object> model = view.getModel();
                for (Map.Entry<String, Object> entry : model.entrySet()) {
                    req.setAttribute(entry.getKey(), entry.getValue());
                }
                req.getRequestDispatcher(ConfigHelper.getAppJspPath() + path).forward(req, resp);
            }
        }
    }

    private void handleDataResult(Data data, HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        Object model = data.getModel();
        if (model != null) {
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            PrintWriter writer = resp.getWriter();
            String json = JsonUtil.toJson(model);
            writer.write(json);
            writer.flush();
            writer.close();
        }
    }
}
