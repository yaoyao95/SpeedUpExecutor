package speed.up.util.mock;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SpeedUpMockConfig {
    public int taskCount = 100_000;
    //平均多少毫秒，发送一个task请求
    public int avgIntervalPerTaskMillSec = 1000;

    //每个任务多少个子查询 1000
    public int queryCountPerTask = 25;
    //每个子查询平均耗时, 用于假定普通情况下，一个查询，耗时范围为[0.25,1.25)之间
    public  int avgPerQueryTimeConsumingMillSec = 500;
    //毛刺子查询最大查询耗时， 用于发生毛刺的情况下，假定毛刺耗时范围为[0.25Max,Max)
    public  int maxVeiningQueryTimeConsuming = 4000;
    //每个子查询，平均毛刺概率
    public double avgRateForVein = 0.10;
    //监控间隔
    public final CountDownLatch latchForAllTask = new CountDownLatch(taskCount);
    //模拟web容器的线程池
    private ThreadPoolExecutor webContainerThreadPoolExecutor;
    public ThreadPoolExecutor webContainerThreadPoolExecutor(){
        if (null == webContainerThreadPoolExecutor) {
            webContainerThreadPoolExecutor = new ThreadPoolExecutor(1000, 1000, 30000, TimeUnit.SECONDS,
//                new LinkedBlockingQueue<>(1),
//                    new SynchronousQueue<>(),
                    new ArrayBlockingQueue<>(1000),
                    new ThreadPoolExecutor.CallerRunsPolicy());
            webContainerThreadPoolExecutor.prestartAllCoreThreads();
            webContainerThreadPoolExecutor.allowCoreThreadTimeOut(true);
        }
        return webContainerThreadPoolExecutor;
    }


    public long calcExecuteTimeForOneQuery() {
        //单个查询
        //                    TimeUnit.MILLISECONDS.sleep((long) (Math.random() * Config.avgPerQueryTimeConsumingMillSec * 2));
        if (Math.random() - this.avgRateForVein < 0) {
            //发生毛刺
            long queryTimeForOneVein = calcQueryTimeForOneVein();
            return queryTimeForOneVein;
        } else {
            //非毛刺，普通耗时
            long queryTimeForNonVein = calcQueryTimeForNonVein();
            return queryTimeForNonVein;
        }
    }

    public long calcQueryTimeForOneTaskSerially() {
        long sleepTime = 0;
        for (int i = 0; i < this.queryCountPerTask; i++) {
            if (Math.random() - this.avgRateForVein < 0) {
                sleepTime += calcQueryTimeForOneVein();
            } else {
                sleepTime += calcQueryTimeForNonVein();
            }
        }
        return sleepTime;
    }

    public long calcQueryTimeForOneTaskCompleteConcurrently() {
        //完全并发的情况下（同时执行），主要看最慢的那个子任务的耗时，考虑到线程切换的耗时，+5毫秒
        long maxExecuteTime = 0;
        for (int i = 0; i < this.queryCountPerTask; i++) {
            if (Math.random() - this.avgRateForVein < 0) {
                maxExecuteTime = Math.max(maxExecuteTime, calcQueryTimeForOneVein());
            } else {
                maxExecuteTime = Math.max(maxExecuteTime,calcQueryTimeForNonVein());
            }
        }
        return maxExecuteTime + 5;
    }

    public long calcQueryTimeForNonVein() {
        return (long) (Math.random() * this.avgPerQueryTimeConsumingMillSec * (1.25 - 0.25) + this.avgPerQueryTimeConsumingMillSec * 0.25);
    }

    public long calcQueryTimeForOneVein() {
        return (long) ((Math.random() * this.maxVeiningQueryTimeConsuming * (1 - 0.25)) + this.maxVeiningQueryTimeConsuming * 0.25);
    }

    public static void sleep(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
