package com.example.controller;


import com.example.dto.LoginFormDTO;
import com.example.dto.Result;
import com.example.dto.UserDTO;
import com.example.entity.UserInfo;
import com.example.service.IUserInfoService;
import com.example.service.IUserService;
import com.example.utils.UserHolder;
import io.swagger.v3.oas.annotations.Operation;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/user")
@Tag(name = "用户信息相关接口")
public class UserController {

    @Autowired
    private IUserService userService;

    @Autowired
    private IUserInfoService userInfoService;

    /**
     * 发送验证码功能(弃用session，改用Redis存储验证码)
     * @param phone 用户手机号
     * @return
     */
    @PostMapping("/code")
    @Operation(summary = "用户发送验证码")
    public Result sendCode(@RequestParam("phone") String phone) {
        // TODO 发送短信验证码并保存验证码
        return userService.sendCode(phone);
    }

    /**
     * 登录功能(弃用session，改用Redis存储已登录用户信息)
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public Result login(@RequestBody LoginFormDTO loginForm){
        // 实现登录功能
        return userService.login(loginForm);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(){
        // TODO 实现登出功能
        return Result.fail("功能未完成");
    }

    /**
     * 用户权限校验
     * @return
     */
    @GetMapping("/me")
    @Operation(summary = "用户权限校验")
    public Result me(){
        // 获取当前登录的用户并返回,从ThreadLocal中取出用户信息
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }
}
