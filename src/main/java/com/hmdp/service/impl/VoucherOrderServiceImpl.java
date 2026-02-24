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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    @Autowired
    private RedissonClient redissonClient;
    private static final DefaultRedisScript<Long> seckillScript;
    static {
        seckillScript = new DefaultRedisScript<>();
        seckillScript.setLocation(new ClassPathResource("seckill.lua"));
        seckillScript.setResultType(Long.class);
    }

    // 异步线程来处理 Redis 消息队列
    private final ExecutorService executor = Executors.newSingleThreadExecutor(); // 创建一个单线程来处理

    @PostConstruct // 在创建 Bean 实例并且注入依赖之后执行
    private void init() { // 让异步线程运行起来
        executor.submit(new VoucherOrderHandler());
    }
    private class VoucherOrderHandler implements Runnable {
        private final String queueName = "stream:orders";
        /**
         * 线程内部，读取 Redis 消息队列并且处理订单
         */
        @Override
        public void run() {
            while (true) {
                try {
                    List<MapRecord<String, Object, Object>> mapRecords = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    // 判断是否获取成功
                    if (mapRecords == null || mapRecords.isEmpty()) {
                        // 代表没有消息，继续循环
                        continue;
                    }
                    // 获取成功之后，需要转换为我们可以处理的对象
                    MapRecord<String, Object, Object> mapRecord = mapRecords.get(0);
                    Map<Object, Object> value = mapRecord.getValue();
                    Long userId = Long.valueOf(value.get("userId").toString());
                    Long orderId = Long.valueOf(value.get("orderId").toString());
                    Long voucherId = Long.valueOf(value.get("voucherId").toString());
                    VoucherOrder voucherOrder = new VoucherOrder();
                    voucherOrder.setUserId(userId);
                    voucherOrder.setId(orderId);
                    voucherOrder.setVoucherId(voucherId);
                    // 将订单信息存入数据库中
                    handlerVoucherOrder(voucherOrder);
                    // 处理成功，就需要将对应的消息从消息队列中移除
                    stringRedisTemplate.opsForStream().acknowledge(
                            queueName, "g1", mapRecord.getId()
                    );
                } catch (Exception e) {
                    // 如果出现异常，就需要从 pending list 中拿出对应的消息，继续处理
                    log.error("处理订单异常", e);
                    handlePendingList();
                }
            }
        }

        /**
         * 这里的 while true 会保证异常订单一定被处理
         */
        private void handlePendingList() {
            while (true) {
                try {
                    List<MapRecord<String, Object, Object>> mapRecords = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.from("0")) // 从 pending list 中取消息
                    );
                    if (mapRecords == null || mapRecords.isEmpty()) {
                        // 没有异常就跳出循环
                        break;
                    }
                    MapRecord<String, Object, Object> mapRecord = mapRecords.get(0);
                    Map<Object, Object> value = mapRecord.getValue();
                    Long userId = Long.valueOf(value.get("userId").toString());
                    Long orderId = Long.valueOf(value.get("orderId").toString());
                    Long voucherId = Long.valueOf(value.get("voucherId").toString());
                    VoucherOrder voucherOrder = new VoucherOrder();
                    voucherOrder.setUserId(userId);
                    voucherOrder.setId(orderId);
                    voucherOrder.setVoucherId(voucherId);
                    handlerVoucherOrder(voucherOrder);
                    // 处理成功，就需要将对应的消息从消息队列中移除
                    stringRedisTemplate.opsForStream().acknowledge(
                            queueName, "g1", mapRecord.getId()
                    );
                } catch (Exception e) {
                    log.error("处理 pending-list订单异常", e);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * 下订单
     * @param voucherOrder
     */
    private void handlerVoucherOrder(VoucherOrder voucherOrder) {
            save(voucherOrder);
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIDWorker.nextId("order");
        // 执行 lua 脚本，会检查库存是否充足，并且是否满足一人一张券
        Long result = stringRedisTemplate.execute(
                seckillScript,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        int r = result.intValue();
        if (r != 0) {
            // 如果返回结果不是 0，代表交易失败
            return Result.fail(r == 1 ? "库存不足" : "不可重复下单");
        }
        // 返回结果为 0，交易成功，并且将订单信息存到了消息队列中
        // 异步处理消息队列
        return Result.ok(orderId);
    }

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        // 首先判断这个秒杀卷是否还在有效期内
//        SeckillVoucher seckillVoucher = seckillVoucherMapper.selectById(voucherId);
//        LocalDateTime now = LocalDateTime.now();
//        if (seckillVoucher.getBeginTime().isAfter(now) || seckillVoucher.getEndTime().isBefore(now)) {
//            // 说明秒杀券要么超时了，要么过期了
//            return Result.fail("秒杀卷已经失效了");
//        }
//        // 判断券的库存是否充足
//        if (seckillVoucher.getStock() < 1) {
//            return Result.fail("库存不足");
//        }
//        Long userId = UserHolder.getUser().getId();
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        boolean isLock = lock.tryLock();
//        if (!isLock) {
//            return Result.fail("不允许重复下单");
//        }
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy(); //拿到代理对象
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            lock.unlock();
//        }
//    }

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
