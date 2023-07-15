package com.example.service;

import com.example.dto.Result;
import com.example.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    // 根据商铺的id查询商铺信息
    Result queryShopById(Long id);

    // 根据商铺id更新商铺信息
    Result update(Shop shop);
}
