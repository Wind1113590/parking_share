-- KEYS[1]: Redis Hash key, e.g. "slice:slot:1001:2025-06-22"
-- ARGV: list of start minutes (as strings), e.g. "480" "495" ...
local key = KEYS[1]

-- 检查所有时间片是否空闲
for i = 1, #ARGV do
    local minute = ARGV[i]
    local cur = redis.call('hget', key, minute)
    if cur == nil or tostring(cur) ~= "0" then
        return 0
    end
end

-- 全部空闲，锁定它们
for i = 1, #ARGV do
    local minute = ARGV[i]
    redis.call('hset', key, minute, "1")
    
end

return 1