package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryShopById(Long id) {
        //从 Redis 中查询店铺信息
        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
        String shop = stringRedisTemplate.opsForValue().get(shopKey);
        if (shop != null && shop.isEmpty()) {
            //如果存在就直接返回
            Shop shopEntity = JSONUtil.toBean(shop, Shop.class);
            return Result.ok(shopEntity);
        }
        //如果不存在，就查询数据库
        Shop shopEntity = getById(id);
        //如果数据库中不存在，就报错
        if (shopEntity == null) {
            return Result.fail("未查询到店铺数据");
        }
        //如果数据库中存在，就将数据存储到 Redis 中并且返回
        shop = JSONUtil.toJsonStr(shopEntity);
        stringRedisTemplate.opsForValue().set(shopKey, shop, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shopEntity);
    }

    @Override
    public Result update(Shop shop) {
        //首先更新数据库信息
        Long id = shop.getId();
        if (id == null) {
           return Result.fail("商铺信息更新失败");
        }
        updateById(shop);
        //其次删除 Redis 对应缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
