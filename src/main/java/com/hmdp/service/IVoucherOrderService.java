package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {
    /**
     * 秒杀券功能，并且避免超售，实行一人一单
     * @param voucherId
     * @return
     */
    Result seckillVoucher(Long voucherId);

    Result createVoucherOrder(Long voucherId);
}
