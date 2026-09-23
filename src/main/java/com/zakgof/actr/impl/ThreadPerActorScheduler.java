package com.zakgof.actr.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.zakgof.actr.IActorScheduler;
import com.zakgof.actr.ILogger;

/**
 * Scheduler that creates a single-thread executor for each actor.
 */
public class ThreadPerActorScheduler implements IActorScheduler {

    private final Map<Object, ThreadPoolExecutor> executors = new ConcurrentHashMap<>();
	private final Timer timer;

    public ThreadPerActorScheduler(ILogger logger, Consumer<Map<String, Integer>> metricConsumer) {
		timer = new Timer("SchedulerStats", true);
		timer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
                // Decide if metrics are collected
                Map<String, Integer> metrics = null;
                if (metricConsumer != null)
                    metrics = new HashMap<>();

				for (Entry<Object, ThreadPoolExecutor> entry : executors.entrySet()) {
                    int queueSize = entry.getValue().getQueue().size();
                    if (queueSize > 0) {
                        if (metrics != null)
                            metrics.put(entry.getKey().toString(), queueSize);
                        if (queueSize > 100)
                            logger.warn("Actor " + entry.getKey() + " - current: " + queueSize + ", total: " + entry.getValue().getCompletedTaskCount());
                        else
                            logger.info("Actor " + entry.getKey() + " - current: " + entry.getValue().getQueue().size() + ", total: " + entry.getValue().getCompletedTaskCount());
                    }
                }

                if (metricConsumer != null)
                    metricConsumer.accept(metrics);
			}
		}, 1000, 1000);
	}

	@Override
    public void actorCreated(Object actorId) {
		ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), runnable -> new Thread(runnable, "actr:" + actorId));
        executors.put(actorId, executor);
    }

    @Override
    public void actorDisposed(Object actorId) {
        ExecutorService service = executors.remove(actorId);
        service.shutdown();
    }

    @Override
    public void schedule(Runnable task, Object actorId) {
        ExecutorService executor = executors.get(actorId);
        if (executor != null && !executor.isShutdown()) {
            executor.execute(task);
        }
    }

    @Override
    public void close() {
    	timer.cancel();
        executors.values().forEach(ExecutorService::shutdown);
    }

    public int getQueueSize(Object actorId) {
        ThreadPoolExecutor executor = executors.get(actorId);
        if (executor != null && executor.getQueue() != null)
            return executor.getQueue().size();
        return 0;
    }
}
