-- 比较线程标示与锁中的线程标示是否相同
if (redis.call('get',KEYS[1]) == ARGV[1]) then
    -- 释放锁 del key
    return redis.call('del',KEYS[1])

end
return 0