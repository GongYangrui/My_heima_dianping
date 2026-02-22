package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public List<ShopType> queryTypeList() {
        String typeListString = stringRedisTemplate.opsForValue().get(RedisConstants.SHOP_TYPE_KEY);
        if (typeListString != null) {
            List<ShopType> shopTypeList = JSONUtil.toList(typeListString, ShopType.class);
            return shopTypeList;
        }
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
        if (shopTypeList == null || shopTypeList.isEmpty()) {
            return null;
        }
        typeListString = JSONUtil.toJsonStr(shopTypeList);
        stringRedisTemplate.opsForValue().set(RedisConstants.SHOP_TYPE_KEY, typeListString);
        return shopTypeList;
    }
}
