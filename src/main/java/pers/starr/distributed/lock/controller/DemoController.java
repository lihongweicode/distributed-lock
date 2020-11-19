package pers.starr.distributed.lock.controller;

import org.apache.tomcat.util.json.JSONParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pers.starr.distributed.lock.common.lock.redis.AbstractRedisLock;
import pers.starr.distributed.lock.common.lock.redis.SpinRedisLock;
import pers.starr.distributed.lock.dto.DemoDTO;

/**
 * @author lhw
 * @description redsi锁操作案例
 * @date 2020/6/29
 */
@RestController
public class DemoController {
    @Autowired
    private RedisTemplate redisTemplate;

    @GetMapping(value = "/redisLock")
    public String lock(DemoDTO demoDTO) {
        System.out.println(Thread.currentThread().getId() + " 进入方法：" + demoDTO.toString());
        AbstractRedisLock redisLock = new SpinRedisLock(redisTemplate, "RedisLockKey", demoDTO.getExpire(), demoDTO.getTimeOut());
        try {
            // 获得锁
            if (redisLock.lock()) {
                // 业务逻辑
                System.out.println(Thread.currentThread().getId() +" 业务逻辑--begin");
                Thread.sleep(demoDTO.getSleepTime());
                System.out.println(Thread.currentThread().getId() +" 业务逻辑--end");
            } else {
                //获取锁失败
                System.err.println(Thread.currentThread().getId() +" 获取锁失败");
            }
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            // 解锁
            redisLock.unlock();
            System.out.println(Thread.currentThread().getId() + " 释放锁");
        }
        return "true";
    }
}
