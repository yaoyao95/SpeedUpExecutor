import java.util.concurrent.atomic.AtomicBoolean;

public class TestSync {
    public static void main(String[] args) throws InterruptedException {
        Object lock = new Object();
        AtomicBoolean aBoo = new AtomicBoolean(false);
        for (int i = 0; i < 100000; i++) {
            new Thread(() -> {
                synchronized (lock) {
                    boolean b = aBoo.get();
                try {
                    Thread.sleep((long) (Math.random() * 5));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if (b != aBoo.get()) {
                        throw new RuntimeException("并发问题，没锁住");
                    }
                    aBoo.set(Math.random() > 0.5);
                }
            }).start();
        }
        Thread.sleep(10000);
    }
}
