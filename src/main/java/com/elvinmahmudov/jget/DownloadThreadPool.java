package com.elvinmahmudov.jget;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class DownloadThreadPool extends ThreadPoolExecutor {

    private ConcurrentHashMap<Future<?>, Runnable> monitorMap = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, ConcurrentLinkedQueue<Future<?>>> tasksMonitor = new ConcurrentHashMap<>();

    public DownloadThreadPool(int corePoolSize, int maximumPoolSize,
                              long keepAliveTime, TimeUnit unit,
                              BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                handler);
    }

    public DownloadThreadPool(int corePoolSize, int maximumPoolSize,
                              long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    public DownloadThreadPool(int corePoolSize, int maximumPoolSize,
                              long keepAliveTime, TimeUnit unit,
                              BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                threadFactory);
    }

    public DownloadThreadPool(int corePoolSize, int maximumPoolSize,
                              long keepAliveTime, TimeUnit unit,
                              BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory,
                              RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                threadFactory, handler);
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        if (t == null) {
            System.out.println(Thread.currentThread().getId()
                    + " has been succeesfully finished!");
        } else {
            System.out.println(Thread.currentThread().getId()
                    + " errroed! Retry");
        }
        for (Future<?> future : monitorMap.keySet()) {
            if (!future.isDone()) {
                DownloadThread runnable = (DownloadThread) monitorMap
                        .get(future);
                DownloadThread newRunnable = runnable.split();
                if (newRunnable != null) {
                    submit(newRunnable);
                    break;
                }
            }
        }
    }

    @Override
    public Future<?> submit(Runnable task) {
        Future<?> future = super.submit(task);
        if (task instanceof DownloadThread) {
            DownloadThread runnable = (DownloadThread) task;

            if (tasksMonitor.containsKey(runnable.taskId)) {
                tasksMonitor.get(runnable.taskId).add(future);
            } else {
                ConcurrentLinkedQueue<Future<?>> queue = new ConcurrentLinkedQueue<Future<?>>();
                queue.add(future);
                tasksMonitor.put(runnable.taskId, queue);
            }

            monitorMap.put(future, task);

        } else {
            throw new RuntimeException(
                    "runnable is not an instance of DownloadThread!");
        }
        return future;
    }

    public boolean isFinished(int taskId) {
        ConcurrentLinkedQueue<Future<?>> futures = tasksMonitor
                .get(taskId);
        if (futures == null)
            return true;

        for (Future<?> future : futures) {
            if (!future.isDone()) {
                return false;
            }
        }
        return true;
    }

    public void pause(int taskId) {
        ConcurrentLinkedQueue<Future<?>> futures = tasksMonitor
                .get(taskId);
        for (Future<?> future : futures) {
            future.cancel(true);
        }
    }

    public void cancel(int taskId) {
        ConcurrentLinkedQueue<Future<?>> futures = tasksMonitor
                .remove(taskId);
        for (Future<?> future : futures) {
            monitorMap.remove(future);
            future.cancel(true);
        }
    }
}
