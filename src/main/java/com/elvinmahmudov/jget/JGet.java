package com.elvinmahmudov.jget;

import java.util.Hashtable;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

public class JGet {

    private static final int DEFAULT_CORE_POOL_SIZE = 10;
    private static final int DEFAULT_MAX_POOL_SIZE = Integer.MAX_VALUE;
    private static final int DEFAULT_KEEP_ALIVE_TIME = 0;
    private static JGet instance;
    private static DownloadThreadPool threadPool;
    private static int ID = 0;

    private Hashtable<Integer, JTask> tasks = new Hashtable<>();

    private JGet() {
        threadPool = new DownloadThreadPool(DEFAULT_CORE_POOL_SIZE,
                DEFAULT_MAX_POOL_SIZE, DEFAULT_KEEP_ALIVE_TIME,
                TimeUnit.SECONDS, new LinkedBlockingDeque<>());
    }

    public static JGet getInstance() {
        if (instance == null) {
            instance = new JGet();
        }
        if (threadPool.isShutdown()) {
            threadPool = new DownloadThreadPool(DEFAULT_CORE_POOL_SIZE,
                    DEFAULT_MAX_POOL_SIZE, DEFAULT_KEEP_ALIVE_TIME,
                    TimeUnit.SECONDS, new LinkedBlockingDeque<>());
        }
        return instance;
    }

    public void addTask(JTask downloadTask) {
        tasks.put(ID++, downloadTask);
    }

    public JTask getTask(int taskId) {
        return tasks.get(taskId);
    }

    public void start() {
        for (JTask task : tasks.values()) {
            task.startTask(threadPool);
        }
    }

    public Boolean isAllTasksFinished() {
        for (Integer task : tasks.keySet()) {
            if (!isTaskFinished(task)) {
                return false;
            }
        }
        return true;
    }

    public Boolean isTaskFinished(int taskId) {
        JTask task = tasks.get(taskId);
        return task.isFinished();
    }

    public void pauseAllTasks() {
        for (Integer taskId : tasks.keySet()) {
            pauseTask(taskId);
        }
    }

    public void pauseTask(int taskId) {
        if (tasks.contains(taskId)) {
            JTask task = tasks.get(taskId);
            task.pause();
        }
    }

    public void cancelAllTasks() {
        for (Integer taskId : tasks.keySet()) {
            cancelTask(taskId);
        }
    }

    public void cancelTask(int taskId) {
        if (tasks.contains(taskId)) {
            JTask task = tasks.remove(taskId);
            task.cancel();
        }
    }

    public void shutdownSafely() {
        for (Integer task : tasks.keySet()) {
            tasks.get(task).pause();
        }
        threadPool.shutdown();
    }

    public int getTotalDownloadedSize() {
        int size = 0;
        for (JTask task : tasks.values()) {
            size += task.getDownloadedSize();
        }
        return size;
    }

    public String getReadableDownloadSize() {
        return DownloadHelper.getReadableSize(getTotalDownloadedSize());
    }

    public int getTotalSpeed() {
        int speed = 0;
        for (JTask task : tasks.values()) {
            speed += task.getSpeed();
        }
        return speed;
    }

    public String getReadableTotalSpeed() {
        return DownloadHelper.getReadableSpeed(getTotalSpeed());
    }

    public void shutdDownloadRudely() {
        threadPool.shutdownNow();
    }
}
