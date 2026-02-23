package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.RedisIDWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private SeckillVoucherMapper seckillVoucherMapper;
    @Autowired
    private VoucherMapper voucherMapper;
    @Autowired
    private RedisIDWorker redisIDWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 首先判断这个秒杀卷是否还在有效期内
        SeckillVoucher seckillVoucher = seckillVoucherMapper.selectById(voucherId);
        LocalDateTime now = LocalDateTime.now();
        if (seckillVoucher.getBeginTime().isAfter(now) || seckillVoucher.getEndTime().isBefore(now)) {
            // 说明秒杀券要么超时了，要么过期了
            return Result.fail("秒杀卷已经失效了");
        }
        // 判断券的库存是否充足
        if (seckillVoucher.getStock() < 1) {
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "order" + userId);
        boolean isLock = lock.tryLock(10);
        if (!isLock) {
            return Result.fail("不允许重复下单");
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy(); //拿到代理对象
            return proxy.createVoucherOrder(voucherId);
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        //由于这里的读取秒杀券订单数据和修改数据不是原子完成的，并且数据也是共享的，所以会出现并发问题，这里需要用到锁来解决
        // userId.toString()每次都会创建新的对象，因此锁的对象不同，不会互斥
        // intern()会把字符串放到JVM字符串常量池中，如果池中有相同的字符串，就返回池中那个对象，如果没有就会加入并且返回
        long count = lambdaQuery()
                .eq(VoucherOrder::getVoucherId, voucherId)
                .eq(VoucherOrder::getUserId, userId)
                .count();
        if (count > 0) {
            // 如果已经购买，返回提示信息
            return Result.fail("用户已经购买过一次");
        }
        // 如果没有购买，那么就需要完成购买流程,
        LambdaUpdateWrapper<SeckillVoucher> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper
                .eq(SeckillVoucher::getVoucherId, voucherId)
                .gt(SeckillVoucher::getStock, 0)
                .setSql("stock = stock - 1");
        boolean success = (seckillVoucherMapper.update(null, updateWrapper) > 0);
        if (!success) {
            return Result.fail("库存不足");
        }
        //如果更新成功，进行下单流程
        VoucherOrder voucherOrder = new VoucherOrder();
        Long orderId = redisIDWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        this.save(voucherOrder);
        return Result.ok(orderId);
    }
}
