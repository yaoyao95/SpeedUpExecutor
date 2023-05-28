package speed.up.util;

import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.ThreadPoolExecutor;

public class SpeedUpStatisticWrapper {
    static class OneStatistic {
        private int activeThreadCount;
        private int notActiveThreadCount;
        private int allThreadCount;
        private long osThreadCount;
        private int queueSize;
        private double qpsOfSubTask;

        //private BlockingQueue queueDetail;

        private long totalMemoryInMB;
        private long freeMemoryInMB;
        private long maxMemoryInMB;

        private MemoryUsage heapMemoryUsage;
        private MemoryUsage nonHeapMemoryUsage;

        @Override
        public String toString() {
            return "OneStatistic{" +
                    "activeThreadCount=" + activeThreadCount +
                    ", notActiveThreadCount=" + notActiveThreadCount +
                    ", allThreadCount=" + allThreadCount +
                    ", osThreadCount=" + osThreadCount +
                    ", queueSize=" + queueSize +
                    ", qpsOfSubTask=" + qpsOfSubTask +
                    ", totalMemoryInMB=" + totalMemoryInMB +
                    ", freeMemoryInMB=" + freeMemoryInMB +
                    ", maxMemoryInMB=" + maxMemoryInMB +
                    ", heapMemoryUsage=" + heapMemoryUsage +
                    ", nonHeapMemoryUsage=" + nonHeapMemoryUsage +
                    '}';
        }
    }
    CircularFifoQueue<OneStatistic> statistics = new CircularFifoQueue<>(100);
    OneStatistic maxStatistic = new OneStatistic();

    public CircularFifoQueue<OneStatistic> getStatistics() {
        return statistics;
    }
    public OneStatistic getMaxStatistic(){
        return maxStatistic;
    }
    private long monitorTime = 0;
    private long lastTimeCompleteSubTaskCount = 0;
    private long lastTimeCompleteSubTaskCountTime = System.currentTimeMillis();

    @Override
    public String toString() {
        return "SpeedUpStatisticWrapper{" +
                "statistics=" + statistics +
                ", maxStatistic=" + maxStatistic +
                ", monitorTime=" + monitorTime +
                ", lastTimeCompleteSubTaskCount=" + lastTimeCompleteSubTaskCount +
                ", lastTimeCompleteSubTaskCountTime=" + lastTimeCompleteSubTaskCountTime +
                '}';
    }

    // 保存平台线程的创建的最大总数
    public static void monitor(SpeedUpStatisticWrapper statisticWrapper, SpeedUpExecutor speedUpExecutor) {
        statisticWrapper.monitorTime = statisticWrapper.monitorTime + 1;
        int osThreadCount = ManagementFactory.getThreadMXBean().getThreadCount();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapMemoryUsage = memoryMXBean.getNonHeapMemoryUsage();
        long maxMemoryInMB = Runtime.getRuntime().maxMemory() / 1024 / 1024;
        long totalMemoryInMB = Runtime.getRuntime().totalMemory() / 1024 / 1024;
        long freeMemoryInMB = Runtime.getRuntime().freeMemory() / 1024 / 1024;

        OneStatistic oneStatistic = new OneStatistic();
        oneStatistic.osThreadCount = osThreadCount;
        oneStatistic.maxMemoryInMB = maxMemoryInMB;
        oneStatistic.totalMemoryInMB = totalMemoryInMB;
        oneStatistic.freeMemoryInMB = freeMemoryInMB;
        oneStatistic.heapMemoryUsage = heapMemoryUsage;
        oneStatistic.nonHeapMemoryUsage = nonHeapMemoryUsage;

        ThreadPoolExecutor mySpeedUpPoolExecutor = speedUpExecutor.mySpeedUpPoolExecutor;
        oneStatistic.activeThreadCount = mySpeedUpPoolExecutor.getActiveCount();
        oneStatistic.allThreadCount = mySpeedUpPoolExecutor.getMaximumPoolSize();
        oneStatistic.notActiveThreadCount = mySpeedUpPoolExecutor.getMaximumPoolSize() - mySpeedUpPoolExecutor.getActiveCount();
        oneStatistic.queueSize = mySpeedUpPoolExecutor.getQueue().size();
        long completedTaskCount = speedUpExecutor.completeTaskCount.get();
        long currentTimeMillis = System.currentTimeMillis();
        oneStatistic.qpsOfSubTask = (completedTaskCount - statisticWrapper.lastTimeCompleteSubTaskCount) / ((currentTimeMillis - statisticWrapper.lastTimeCompleteSubTaskCountTime) / 1000.0);
        statisticWrapper.lastTimeCompleteSubTaskCount = completedTaskCount;
        statisticWrapper.lastTimeCompleteSubTaskCountTime = currentTimeMillis;
        statisticWrapper.getStatistics().add(oneStatistic);

        statisticWrapper.maxStatistic.osThreadCount = Math.max(statisticWrapper.maxStatistic.osThreadCount, osThreadCount);
        statisticWrapper.maxStatistic.maxMemoryInMB = Math.max(statisticWrapper.maxStatistic.maxMemoryInMB, maxMemoryInMB);
        statisticWrapper.maxStatistic.totalMemoryInMB = Math.max(statisticWrapper.maxStatistic.totalMemoryInMB, totalMemoryInMB);
        statisticWrapper.maxStatistic.freeMemoryInMB = Math.max(statisticWrapper.maxStatistic.freeMemoryInMB, freeMemoryInMB);
        statisticWrapper.maxStatistic.activeThreadCount = Math.max(statisticWrapper.maxStatistic.activeThreadCount, mySpeedUpPoolExecutor.getLargestPoolSize());
        statisticWrapper.maxStatistic.notActiveThreadCount = Math.max(statisticWrapper.maxStatistic.notActiveThreadCount, oneStatistic.notActiveThreadCount);
        statisticWrapper.maxStatistic.allThreadCount = Math.max(statisticWrapper.maxStatistic.allThreadCount, oneStatistic.allThreadCount);
        statisticWrapper.maxStatistic.queueSize =  Math.max(statisticWrapper.maxStatistic.queueSize, oneStatistic.queueSize);
        statisticWrapper.maxStatistic.qpsOfSubTask = Math.max(statisticWrapper.maxStatistic.qpsOfSubTask, oneStatistic.qpsOfSubTask);
        System.out.println("current max statistic: " + statisticWrapper.maxStatistic);
        System.out.println("current statistic: " + oneStatistic);

        if (statisticWrapper.monitorTime % 30 == 0) {
            System.out.println("all statistic :" + statisticWrapper);
        }
    }
}
