package com.example.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.dto.LoginFormDTO;
import com.example.dto.Result;
import com.example.entity.User;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    /**
     * 发送验证码功能的业务层接口中的抽象方法
     * @param phone
     * @return
     */
    Result sendCode(String phone);

    Result login(LoginFormDTO loginForm);
}
