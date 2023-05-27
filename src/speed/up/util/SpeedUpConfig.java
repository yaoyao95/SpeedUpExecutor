package speed.up.util;

import java.util.concurrent.TimeUnit;

public class SpeedUpConfig {
    private boolean enable = true;
    private boolean enableMonitor = true;
    private int monitorInitDelayInSec = 1;
    private int monitorInitPeriodInSec = 60 * 10;

    private int corePoolSize = 10;
    private int maximumPoolSize = 300;
    private long keepAliveTime = 60;
    /**预留几个 工作线程 ，避免统计活跃的工作线程数量的 不准确而导致有些任务需要当前线程执行，而使任务性能变慢*/
    private int bufferThreadWorkSizeForInaccuracyOfActiveCount = 5;
    /** 废弃.设置了也没用， 因为，因统计工作线程不准确而把子任务塞入到队列里，很影响性能，高并发时，甚至需要30以上大小的队列，才能较大概率避免队列也满了，但队列大了有可能会饿死。导致产生任务反而变慢的副作用 <br>
     * 而采用同步队列的情况（即队列的size是空），预留少量几个worker，就能大概率应对工作线程数量统计的不准确问题而导致任务执行变慢。
     * 线程池阻塞队列的长度设置。本加速工具，采用线程池来自行，当有足有的空闲工作线程时，才会并发执行子任务，否则将降级为调用者的线程来串行执行。<br>
     * 但统计空闲工作线程的数量市预估的，不是100%准确，所以可能有部分子任务没有分配到工作线程，而被放入队列种。<br>
     * 同时为防止饿死，请不要设置得太大，以免统计因预估过于不准确，而饿死。(但一般统计非常精确，可能高并发，小任务的情况会统计不准确)
     * */
    private int blockQueueSize =  25;

    private TimeUnit timeUnit = TimeUnit.SECONDS;
    //单个任务的最大耗时限制，超过则降级，用当前线程顺序查询。认为线程池、并发出了问题，若认为没有问题，可以把这个数字设置很大
    private long maxTimeLimitForOneAggregateExecuteInMillSec = 15_000;


    public int getCorePoolSize() {
        return corePoolSize;
    }

    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public void setMaximumPoolSize(int maximumPoolSize) {
        this.maximumPoolSize = maximumPoolSize;
    }

    public long getKeepAliveTime() {
        return keepAliveTime;
    }

    public void setKeepAliveTime(long keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    public long getMaxTimeLimitForOneAggregateExecuteInMillSec() {
        return maxTimeLimitForOneAggregateExecuteInMillSec;
    }

    public void setMaxTimeLimitForOneAggregateExecuteInMillSec(long maxTimeLimitForOneAggregateExecuteInMillSec) {
        this.maxTimeLimitForOneAggregateExecuteInMillSec = maxTimeLimitForOneAggregateExecuteInMillSec;
    }

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public boolean isEnableMonitor() {
        return enableMonitor;
    }

    public void setEnableMonitor(boolean enableMonitor) {
        this.enableMonitor = enableMonitor;
    }

    public int getMonitorInitDelayInSec() {
        return monitorInitDelayInSec;
    }

    public void setMonitorInitDelayInSec(int monitorInitDelayInSec) {
        this.monitorInitDelayInSec = monitorInitDelayInSec;
    }

    public int getMonitorInitPeriodInSec() {
        return monitorInitPeriodInSec;
    }

    public void setMonitorInitPeriodInSec(int monitorInitPeriodInSec) {
        this.monitorInitPeriodInSec = monitorInitPeriodInSec;
    }

    public int getBlockQueueSize() {
        return blockQueueSize;
    }

    public int getBufferThreadWorkSizeForInaccuracyOfActiveCount() {
        return bufferThreadWorkSizeForInaccuracyOfActiveCount;
    }

    public void setBufferThreadWorkSizeForInaccuracyOfActiveCount(int bufferThreadWorkSizeForInaccuracyOfActiveCount) {
        this.bufferThreadWorkSizeForInaccuracyOfActiveCount = bufferThreadWorkSizeForInaccuracyOfActiveCount;
    }
}
