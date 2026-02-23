package com.hmdp.utils;

/**
 * ClassName: ILock
 * Package: com.hmdp.utils
 * Description:
 *
 * @Author GYR
 * @Create 2026/2/23 18:02
 * @Version 1.0
 */
public interface ILock {
    /**
     * 尝试获取锁
     * @param timeoutSeconds 锁持有的超时时间
     * @return true 代表获取锁成功，false 代表获取锁失败
     */
    boolean tryLock(long timeoutSeconds);

    /**
     * 释放锁
     */
    void unlock();
}
