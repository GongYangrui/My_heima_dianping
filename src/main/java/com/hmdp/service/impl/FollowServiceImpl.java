package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

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

    @Override
    public Result followOrNot(Long id, Boolean isFollowOrNot) {
        Long userId = UserHolder.getUser().getId();
        if (Boolean.TRUE.equals(isFollowOrNot)) {
            // 关注操作
            Follow follow = new Follow();
            follow.setFollowUserId(id);
            follow.setUserId(userId);
            save(follow);
        } else {
            // 取关操作
            remove(
                    new LambdaQueryWrapper<Follow>()
                            .eq(Follow::getUserId, userId)
                            .eq(Follow::getFollowUserId, id)
            );
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
}
