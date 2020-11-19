package pers.starr.distributed.lock.common.lock.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import pers.starr.distributed.lock.common.exception.LockException;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.params.SetParams;

import java.nio.charset.Charset;
import java.util.Random;

/**
 * http://www.luyixian.cn/news_show_400988.aspx
 * <p>
 * Redis分布式锁 使用 SET resource-name anystring NX EX max-lock-time 实现
 * <p>
 * 该方案在 Redis 官方 SET 命令页有详细介绍。 http://doc.redisfans.com/string/set.html
 * <p>
 * 在介绍该分布式锁设计之前，我们先来看一下在从 Redis 2.6.12 开始 SET 提供的新特性， 命令 SET key value [EX seconds] [PX milliseconds] [NX|XX]，其中：
 * <p>
 * EX seconds — 以秒为单位设置 key 的过期时间； PX milliseconds — 以毫秒为单位设置 key 的过期时间； NX — 将key 的值设为value ，当且仅当key 不存在，等效于 SETNX。 XX
 * — 将key 的值设为value ，当且仅当key 存在，等效于 SETEX。
 * <p>
 * 命令 SET resource-name anystring NX EX max-lock-time 是一种在 Redis 中实现锁的简单方法。
 * <p>
 * 客户端执行以上的命令：
 * <p>
 * 如果服务器返回 OK ，那么这个客户端获得锁。 如果服务器返回 NIL ，那么客户端获取锁失败，可以在稍后再重试。
 */
public abstract class AbstractRedisLock implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractRedisLock.class);

    protected RedisTemplate redisTemplate;

    /**
     * 将key 的值设为value ，当且仅当key 不存在，等效于 SETNX。
     */
    public static final String NX = "NX";

    /**
     * seconds — 以秒为单位设置 key 的过期时间，等效于EXPIRE key seconds
     */
    public static final String EX = "EX";

    /**
     * 调用set后的返回值
     */
    public static final String OK = "OK";

    /**
     * 默认请求锁的超时时间(ms 毫秒)
     */
    protected static final long TIME_OUT = 100;

    /**
     * 默认锁的有效时间(s)
     */
    public static final int EXPIRE = 30;

    /**
     * 解锁的lua脚本
     */
    public static final String UNLOCK_LUA;

    static {
        StringBuilder sb = new StringBuilder();
        sb.append("if redis.call(\"get\",KEYS[1]) == ARGV[1] ");
        sb.append("then ");
        sb.append("    return redis.call(\"del\",KEYS[1]) ");
        sb.append("else ");
        sb.append("    return 0 ");
        sb.append("end ");
        UNLOCK_LUA = sb.toString();
    }

    /**
     * 锁标志对应的key
     */
    protected String lockKey;

    /**
     * 锁对应的值
     */
    protected String lockValue;

    /**
     * 锁的有效时间(s)
     */
    protected int expireTime = EXPIRE;

    /**
     * 锁标记
     */
    protected boolean locked = false;

    public boolean isLocked() {
        return locked;
    }

    final Random random = new Random();

    public int getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(int expireTime) {
        this.expireTime = expireTime;
    }

    /**
     * 使用默认的锁过期时间和请求锁的超时时间
     *
     * @param redisTemplate
     * @param lockKey       锁的key（Redis的Key）
     */
    public AbstractRedisLock(RedisTemplate redisTemplate, String lockKey) {
        this.redisTemplate = redisTemplate;
        this.lockKey = lockKey + "_lock";
    }

    /**
     * 使用默认的请求锁的超时时间，指定锁的过期时间
     *
     * @param redisTemplate
     * @param lockKey       锁的key（Redis的Key）
     * @param expireTime    锁的过期时间(单位：秒)
     */
    public AbstractRedisLock(RedisTemplate redisTemplate, String lockKey, int expireTime) {
        this(redisTemplate, lockKey);
        this.expireTime = expireTime;
    }

    /**
     * 尝试获取锁 立即返回
     *
     * @return 是否成功获得锁
     */
    public abstract boolean lock();

    /**
     * 解锁
     * <p>
     * 可以通过以下修改，让这个锁实现更健壮：
     * <p>
     * 不使用固定的字符串作为键的值，而是设置一个不可猜测（non-guessable）的长随机字符串，作为口令串（token）。 不使用 DEL 命令来释放锁，而是发送一个 Lua
     * 脚本，这个脚本只在客户端传入的值和键的口令串相匹配时，才对键进行删除。 这两个改动可以防止持有过期锁的客户端误删现有锁的情况出现。
     */
    public boolean unlock() {
        // 只有加锁成功并且锁还有效才去释放锁
        if (locked) {
            return (boolean) redisTemplate.execute((RedisCallback<Boolean>) connection ->
                    connection.eval(UNLOCK_LUA.getBytes(), ReturnType.BOOLEAN, 1,
                            lockKey.getBytes(Charset.forName("UTF-8")), lockValue.getBytes(Charset.forName("UTF-8"))));
        }
        return true;
    }

    /**
     * 重写redisTemplate的set方法
     * <p>
     * 命令 SET resource-name anystring NX EX max-lock-time 是一种在 Redis 中实现锁的简单方法。
     * <p>
     * 客户端执行以上的命令：
     * <p>
     * 如果服务器返回 OK ，那么这个客户端获得锁。 如果服务器返回 NIL ，那么客户端获取锁失败，可以在稍后再重试。
     *
     * @param key     锁的Key
     * @param value   锁里面的值
     * @param seconds 过去时间（秒）
     */
    protected boolean set(final String key, final String value, final long seconds) {
        Assert.isTrue(!StringUtils.isEmpty(key), "key不能为空");
        return (Boolean) redisTemplate.execute((RedisCallback<Boolean>) connection ->
                connection.set(lockKey.getBytes(Charset.forName("UTF-8")), lockValue.getBytes(Charset.forName("UTF-8")),
                        Expiration.seconds(seconds), RedisStringCommands.SetOption.SET_IF_ABSENT));
    }

    /**
     * 线程等待时间
     *
     * @param millis 毫秒
     * @param nanos  纳秒
     */
    protected void sleep(long millis, int nanos) {
        try {
            Thread.sleep(millis, random.nextInt(nanos));
        } catch (InterruptedException e) {
            LOGGER.info("获取分布式锁休眠被中断：", e);
        }
    }

    @Override
    public void close() throws Exception {
        if (this.isLocked() && !this.unlock()) {
            throw new LockException("释放分布式锁失败, key=" + lockKey.toString());
        }
    }

    public static void main(String[] args) {
        AbstractRedisLock redisLock = new SpinRedisLock(new RedisTemplate(), "RedisLockKey", 10, 500L);
        try {
            // 获得锁
            if (redisLock.lock()) {
                // 业务逻辑
            }
        } finally {
            // 解锁
            redisLock.unlock();
        }
    }
}