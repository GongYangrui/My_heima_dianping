package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Autowired
    private UserMapper userMapper;

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = lambdaQuery().eq(Blog::getId, id).one();
        if (blog == null) {
            return Result.fail("获取博客信息失败");
        }
        User user = userMapper.selectById(blog.getUserId());
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
        return Result.ok(blog);
    }
}
