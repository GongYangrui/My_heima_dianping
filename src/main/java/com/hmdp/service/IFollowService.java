package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注或者取关某博主
     * @param id
     * @param isFollowOrNot
     * @return
     */
    Result followOrNot(Long id, Boolean isFollowOrNot);

    /**
     * 查看是否关注 userId 为 id 的博主
     * @param id
     * @return
     */
    Result isFollowOrNot(Long id);

    /**
     * 求 id对应用户和当前用户的共同关注
     * @param id
     * @return
     */
    Result followCommon(Long id);
}
