local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]
local voucherKey = "seckill:stock:" .. voucherId
local orderKey = "seckill:order:" .. voucherId
if (tonumber(redis.call("get", voucherKey)) <= 0) then
    return 1
end
if (redis.call("sismember", orderKey, userId) == 1) then
    return 2
end
--扣减库存
redis.call("incrby", voucherKey, -1)
--将 userId 存入 order 集合
redis.call("sadd", orderKey, userId)
--将订单数据加入到消息队列中
redis.call("xadd", "stream:orders", "*", "userId", userId, "voucherId", voucherId, "orderId", orderId)
--返回 0
return 0