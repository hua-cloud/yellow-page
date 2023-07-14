package com.example.utils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;


/**
 * 定义登录拦截器类
 * 实现 HandlerInterceptor接口
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.判断是否需要拦截(即ThreadLocal中是否包含用户信息)
        if (UserHolder.getUser() == null) {
            // 若为空则表明用户尚未登录或登录信息已过期，则需要拦截
            response.setStatus(401);
            return false;
        }
        // 由用户信息则放行
        return true;
    }
}
