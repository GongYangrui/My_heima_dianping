package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IShopService shopService;
    @Test
    void loadShopData() {
        List<Shop> shopList = shopService.list();
        Map<Long, List<Shop>> shopMap = shopList.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        for (Map.Entry<Long, List<Shop>> entry : shopMap.entrySet()) {
            Long shopTypeId = entry.getKey();
            List<Shop> shops = entry.getValue();
            String key = RedisConstants.SHOP_GEO_KEY + shopTypeId;
            List<RedisGeoCommands.GeoLocation<String>> geoLocations = shops.stream().map(shop -> {
                return new RedisGeoCommands.GeoLocation<>(shop.getId().toString(), new Point(shop.getX(), shop.getY()));
            }).collect(Collectors.toList());
            stringRedisTemplate.opsForGeo().add(key, geoLocations);
        }
    }

}
