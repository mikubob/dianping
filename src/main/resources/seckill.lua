-- 秒杀脚本

-- 1.参数列表
-- 1.1.优惠卷id
local voucherId = ARGV[1]
-- 1.2.用户id
local userId = ARGV[2]
-- 1.3.订单id
local orderId = ARGV[3]

-- 2.数据key
-- 2.1.优惠券库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 2.2.订单队列key
local orderKey = 'seckill:order:' .. voucherId

-- 3.业务逻辑
-- 3.1.判断库存是否充足
if (tonumber(redis.call('GET', stockKey) <= 0)) then
    -- 库存不足,返回1
    return 1
end

-- 3.2.库存充足,判断用户是否重复抢购
if (redis.call('SISMEMBER', orderKey, userId) == 1) then
    -- 重复抢购,返回2
    return 2
end

-- 3.3.扣减库存
redis.call('INCRBY', stockKey, 'count', -1)

-- 3.4.下单(保存用户)
redis.call('SADD', orderKey, userId)

-- 3.5.发送消息队列中
redis.call('XADD', 'stream.orders', '*', 'voucherId', voucherId, 'userId', userId, 'orderId', orderId)