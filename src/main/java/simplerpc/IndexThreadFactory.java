package simplerpc;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author huangli
 */
class IndexThreadFactory implements ThreadFactory {

    private final AtomicInteger threadIndex = new AtomicInteger(0);
    private final String name;

    public IndexThreadFactory(String name) {
        this.name = name;
    }

    @Override
    public Thread newThread(Runnable r) {
        return new Thread(r, name + "_" + this.threadIndex.incrementAndGet());
    }
}
