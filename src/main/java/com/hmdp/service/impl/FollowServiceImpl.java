package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
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
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IUserService userService;

    @Override
    public Result followOrNot(Long id, Boolean isFollowOrNot) {
        Long userId = UserHolder.getUser().getId();
        if (Boolean.TRUE.equals(isFollowOrNot)) {
            // 关注操作
            Follow follow = new Follow();
            follow.setFollowUserId(id);
            follow.setUserId(userId);
            boolean isSuccess = save(follow);
            if (isSuccess) {
                stringRedisTemplate.opsForSet().add(RedisConstants.FOLLOW_USER_KEY + userId, id.toString());
            }
        } else {
            // 取关操作
            boolean isSuccess = remove(
                    new LambdaQueryWrapper<Follow>()
                            .eq(Follow::getUserId, userId)
                            .eq(Follow::getFollowUserId, id)
            );
            if (isSuccess) {
                stringRedisTemplate.opsForSet().remove(RedisConstants.FOLLOW_USER_KEY + userId, id.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollowOrNot(Long id) {
        Long userId = UserHolder.getUser().getId();
        Long count = lambdaQuery().eq(Follow::getUserId, userId)
                .eq(Follow::getFollowUserId, id)
                .count();
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommon(Long id) {
        String key1 = RedisConstants.FOLLOW_USER_KEY + id;
        Long userId = UserHolder.getUser().getId();
        String key2 = RedisConstants.FOLLOW_USER_KEY + userId;
        Set<String> intersectUserId = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (intersectUserId == null || intersectUserId.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> userIdList = intersectUserId.stream().map(Long::valueOf).collect(Collectors.toList());
        List<User> userList = userService.listByIds(userIdList);
        List<UserDTO> userDTOList = userList.stream().map((user -> {
            return BeanUtil.toBean(user, UserDTO.class);
        })).collect(Collectors.toList());
        return Result.ok(userDTOList);
    }
}
