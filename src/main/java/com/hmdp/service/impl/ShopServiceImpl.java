package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClientUtils;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    @Autowired
    private CacheClientUtils cacheClientUtils;

    @Override
    public Result queryShopById(Long id) {
        Shop shop = cacheClientUtils.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("商铺数据查询失败");
        }
        return Result.ok(shop);
    }

    @Override
    @Transactional
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

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        if (x == null || y == null) {
            // 不需要坐标查询
            Page<Shop> shopPage = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(shopPage.getRecords());
        }
        // 查询 Redis 数据，(x,y)附近商铺
        int begin = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> geoLocationGeoResults = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x, y), // 指定(x,y)坐标
                new Distance(5000), // 指定距离
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end) // 带上距离
        );
        if (geoLocationGeoResults == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> geoLocationGeoResultsContent = geoLocationGeoResults.getContent();
        if (geoLocationGeoResultsContent.size() <= begin) {
            return Result.ok(Collections.emptyList());
        }
        // 这时候代表返回了数据
        List<Shop> shops = geoLocationGeoResultsContent.stream().skip(begin).map((result) -> {
            Distance distance = result.getDistance();
            RedisGeoCommands.GeoLocation<String> content = result.getContent();
            String name = content.getName(); // shop 的 id
            Shop shop = this.getById(Long.valueOf(name));
            shop.setDistance(distance.getValue());
            return shop;
        }).collect(Collectors.toList());
        return Result.ok(shops);
    }
}
