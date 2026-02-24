package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.BeanUtils;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = lambdaQuery().eq(Blog::getId, id).one();
        if (blog == null) {
            return Result.fail("获取博客信息失败");
        }
        User user = userMapper.selectById(blog.getUserId());
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
        Double score = stringRedisTemplate.opsForZSet().score(RedisConstants.BLOG_LIKED_KEY + id, blog.getUserId().toString());
        blog.setIsLike(score != null);
        return Result.ok(blog);
    }

    @Override
    public Result likeBlog(Long id) {
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Long userId = UserHolder.getUser().getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score != null) {
            // 如果是其中的一部分，就相当于是取消点赞
            boolean update = lambdaUpdate().setSql("liked = liked - 1").eq(Blog::getId, id).update();
            if (update) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        } else {
            // 如果不是其中的一部分，就相当于是点赞了
            boolean update = lambdaUpdate().setSql("liked = liked + 1").eq(Blog::getId, id).update();
            if (update) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        }
        return Result.ok();
    }

    @Override
    public Result getBlogLikes(Long id) {
        // 首先拿到该博客的点赞列表
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(RedisConstants.BLOG_LIKED_KEY + id, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> top5Ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> userDTOList = top5Ids.stream().map((userId) -> {
            User user = userMapper.selectById(userId);
            UserDTO userDTO = new UserDTO();
            BeanUtils.copyProperties(user, userDTO);
            return userDTO;
        }).collect(Collectors.toList());
        return Result.ok(userDTOList);
    }
}
