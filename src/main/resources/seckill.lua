-- 1.参数列表
-- 1.1.优惠券id
local voucherId = ARGV[1]
-- 1.2.用户id
local userId = ARGV[2]
-- 1.3.订单id
local orderId = ARGV[3]

-- 2.数据key
-- 2.1.库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 2.2.订单key
local orderKey = 'seckill:order:' .. voucherId
-- 2.3.订单流key
local streamKey = 'stream.orders'

-- 3.脚本业务
-- 3.1.判断库存是否充足 get stockKey
local stock = redis.call('get', stockKey)
-- 检查库存是否存在且大于0
if(stock == false or stock == nil or tonumber(stock) == nil or tonumber(stock) <= 0) then
    -- 3.2.库存不足，返回1
    return 1
end
-- 3.2.判断用户是否下单 SISMEMBER orderKey userId
if(redis.call('sismember', orderKey, userId) == 1) then
    -- 3.3.存在，说明是重复下单，返回2
    return 2
end
-- 3.4.扣库存，使用decr原子操作并检查结果
local result = redis.call('decr', stockKey)
if(tonumber(result) < 0) then
    -- 如果扣减后库存小于0，恢复库存并返回1（库存不足）
    redis.call('incr', stockKey)
    return 1
end
-- 3.5.下单（保存用户）sadd orderKey userId
redis.call('sadd', orderKey, userId)
-- 3.6.发送消息到队列中， XADD stream.orders * k1 v1 k2 v2 ...
local ok, err = pcall(redis.call, 'xadd', streamKey, '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)
if not ok then
    -- 如果发送消息失败，需要回滚前面的操作
    redis.call('incr', stockKey)  -- 恢复库存
    redis.call('srem', orderKey, userId)  -- 移除已添加的用户
    return -1  -- 返回特殊错误码表示发送消息失败
end
return 0