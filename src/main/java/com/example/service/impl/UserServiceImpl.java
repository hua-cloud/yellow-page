package com.example.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.dto.LoginFormDTO;
import com.example.dto.Result;
import com.example.dto.UserDTO;
import com.example.entity.User;
import com.example.mapper.UserMapper;
import com.example.service.IUserService;
import com.example.utils.RegexUtils;
import com.example.utils.SystemConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.example.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone) {
        // 1.校验手机号,返回值为true表明手机号无效
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.如果不符合返回错误信息
            return Result.fail("手机号无效");
        }
        // 3.符合生成验证码,使用工具类中的方法生成一个6位的随机验证码
        String code = RandomUtil.randomNumbers(6);

        // 4.保存验证码到session,以键值对的形式存入到session域对象中,
        // 更改为将验证码存储到Redis中 set key value ex 120
        // session.setAttribute("code",code);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 5.模拟发送验证码
        log.debug("发送短信验证码成功，验证码{}",code);

        // 返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm) {
        // 1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 1.1 如果不符合返回错误信息
            return Result.fail("手机号无效");
        }

        // 2.校验验证码,从Redis中获取
        // Object cacheCode = session.getAttribute("code");
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            // 3.不一致，报错
            return Result.fail("验证码错误！");
        }

        // 4.一致，根据手机号查询用户 select * from user where phone=?
        User user = query().eq("phone", phone).one();

        // 5.判断用户是否存在
        if (user == null) {
            // 6.不存在，创建用户并保存到数据库
            user = createUserByPhone(phone);
        }

        // 7.保存部分用户信息到session/保存用户信息到Redis中
        // 7.1 生成随机的token,作为登录的令牌
        String token = UUID.randomUUID().toString(true);

        // 7.2 将User对象转换为HashMap存储,注意userDTO对象中的id属性类型为Long,需要先转换为String类型
        UserDTO userDTO = BeanUtil.copyProperties(user,UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,fieldValue) -> fieldValue.toString()));


        // 7.3 存储用户信息到Redis中
        // session.setAttribute("user",userDTO);
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token,userMap);
        // 7.4 设置token的有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY + token,LOGIN_USER_TTL,TimeUnit.MINUTES);

        // 8.返回token
        return Result.ok(token);
    }

    private User createUserByPhone(String phone) {
        // 1.创建用户
        User user = new User();
        // 2.补全相关信息
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 3.保存用户信息到数据库
        save(user);
        // 4.返回用户信息
        return user;
    }
}
