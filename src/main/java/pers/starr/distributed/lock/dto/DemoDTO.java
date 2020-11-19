package pers.starr.distributed.lock.dto;

/**
 * @author lhw
 * @description dto
 * @date 2020/6/29
 */
public class DemoDTO {
    @Override
    public String toString() {
        return "DemoDTO{" +
                "timeOut=" + timeOut +
                ", expire=" + expire +
                ", lockKey='" + lockKey + '\'' +
                ", sleepTime=" + sleepTime +
                '}';
    }

    /**
     * 默认请求锁的超时时间(ms 毫秒)
     */
    private Integer timeOut = 100;

    /**
     * 默认锁的有效时间(s)
     */
    private Integer expire = 30;

    private String lockKey;

    private Long sleepTime;

    public Long getSleepTime() {
        return sleepTime;
    }

    public void setSleepTime(Long sleepTime) {
        this.sleepTime = sleepTime;
    }

    public Integer getTimeOut() {
        return timeOut;
    }

    public void setTimeOut(Integer timeOut) {
        this.timeOut = timeOut;
    }

    public Integer getExpire() {
        return expire;
    }

    public void setExpire(Integer expire) {
        this.expire = expire;
    }

    public String getLockKey() {
        return lockKey;
    }

    public void setLockKey(String lockKey) {
        this.lockKey = lockKey;
    }
}
