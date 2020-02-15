package com.elvinmahmudov.jget;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(namespace = "com.zhan_dui.downloader")
@XmlAccessorType(XmlAccessType.NONE)
public class JTask {

    public static final int READY = 1;
    public static final int DOWNLOADING = 2;
    public static final int PAUSED = 3;
    public static final int FINISHED = 4;

    public static int DEFAULT_THREAD_COUNT = 4;
    private static int taskCounter = 0;
    @XmlElement(name = "URL")
    protected String url;
    @XmlElement(name = "SaveDirectory")
    protected String saveDirectory;
    @XmlElement(name = "SaveName")
    protected String saveName;
    protected int taskId = taskCounter++;
    @XmlElement(name = "TaskMonitor")
    protected TaskMonitor monitor = new TaskMonitor(this);
    @XmlElement(name = "SpeedMonitor")
    protected SpeedMonitor speedMonitor = new SpeedMonitor(this);
    protected StoreMonitor storeMonitor = new StoreMonitor();
    protected Timer speedTimer = new Timer();
    protected Timer storeTimer = new Timer();
    protected DownloadThreadPool threadPoolRef;
    @XmlElementWrapper(name = "Downloadings")
    @XmlElement(name = "Downloading")
    private ArrayList<DownloadThread> downloadParts = new ArrayList<>();
    private ArrayList<RecoveryRunnableInfo> recoveryRunnableInfos = new ArrayList<>();
    @XmlElement(name = "TaskStatus")
    private int taskStatus = READY;
    private String progressDir;
    private String progressFileName;
    @XmlElement(name = "FileSize")
    private int fileSize;
    private int threadCount = DEFAULT_THREAD_COUNT;
    private boolean isFinished = false;

    private JTask() {
        // just for annotation
    }

    public JTask(String url, String saveDirectory, String saveName)
            throws IOException {
        this.url = url;

        setTargetFile(saveDirectory, saveName);

        setProgessFile(this.saveDirectory, this.saveName);
    }

    public static JTask recoverTaskFromProgressFile(
            String progressDirectory, String progressFileName)
            throws IOException {
        try {
            File progressFile = new File(
                    FileHelper.getSafeDirPath(progressDirectory)
                            + File.separator + progressFileName);
            if (!progressFile.exists()) {
                throw new IOException("Progress File does not exsist");
            }

            JAXBContext context = JAXBContext.newInstance(JTask.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            JTask taskId = (JTask) unmarshaller
                    .unmarshal(progressFile);
            File targetSaveFile = new File(
                    FileHelper.getSafeDirPath(taskId.saveDirectory
                            + File.separator + taskId.saveName));
            if (!targetSaveFile.exists()) {
                throw new IOException(
                        "Try to continue download file , but target file does not exist");
            }
            taskId.setProgessFile(progressDirectory, progressFileName);
            taskId.taskId = taskCounter++;
            ArrayList<RecoveryRunnableInfo> recoveryRunnableInfos = taskId
                    .getDownloadProgress();
            for (DownloadThread runnable : taskId.downloadParts) {
                recoveryRunnableInfos.add(new RecoveryRunnableInfo(runnable
                        .getStartPosition(), runnable.getCurrentPosition(),
                        runnable.getEndPosition()));
            }
            taskId.downloadParts.clear();
            return taskId;
        } catch (JAXBException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Boolean setTargetFile(String saveDir, String saveName)
            throws IOException {
        if (saveDir.lastIndexOf(File.separator) == saveDir.length() - 1) {
            saveDir = saveDir.substring(0, saveDir.length() - 1);
        }
        saveDirectory = saveDir;
        File dirFile = new File(saveDir);
        if (!dirFile.exists()) {
            if (!dirFile.mkdirs()) {
                throw new RuntimeException("Error to create directory");
            }
        }

        File file = new File(dirFile.getPath() + File.separator + saveName);
        if (!file.exists()) {
            file.createNewFile();
        }
        this.saveName = saveName;
        return true;
    }

    public int getTaskId() {
        return taskId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String Url) {
        this.url = Url;
    }

    public String getSaveDirectory() {
        return saveDirectory;
    }

    public void setSaveDirectory(String SaveDirectory) {
        this.saveDirectory = SaveDirectory;
    }

    public String getSaveName() {
        return saveName;
    }

    public void setSaveName(String SaveName) {
        this.saveName = SaveName;
    }

    public int getTaskThreadCount() {
        return threadCount;
    }

    public void setTaskThreadCount(int thread_count) {
        threadCount = thread_count;
    }

    public int getDefaultThreadCount() {
        return DEFAULT_THREAD_COUNT;
    }

    public void setDefaultThreadCount(int default_thread_count) {
        if (default_thread_count > 0)
            DEFAULT_THREAD_COUNT = default_thread_count;
    }

    private ArrayList<DownloadThread> splitDownload(int thread_count) {
        ArrayList<DownloadThread> runnables = new ArrayList<DownloadThread>();
        try {
            int size = getContentLength(url);
            fileSize = size;
            int sublen = size / thread_count;
            for (int i = 0; i < thread_count; i++) {
                int startPos = sublen * i;
                int endPos = (i == thread_count - 1) ? size
                        : (sublen * (i + 1) - 1);
                DownloadThread runnable = new DownloadThread(this.monitor,
                        url, saveDirectory, saveName, startPos, endPos);
                runnables.add(runnable);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return runnables;
    }

    private void resumeTask() throws IOException {
        try {
            File progressFile = new File(FileHelper.getSafeDirPath(progressDir)
                    + File.separator + progressFileName);
            if (!progressFile.exists()) {
                throw new IOException("Progress File does not exsist");
            }

            JAXBContext context = JAXBContext
                    .newInstance(JTask.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            JTask taskId = (JTask) unmarshaller.unmarshal(progressFile);
            File targetSaveFile = new File(
                    FileHelper.getSafeDirPath(taskId.saveDirectory
                            + File.separator + taskId.saveName));
            if (!targetSaveFile.exists()) {
                throw new IOException(
                        "Try to continue download file , but target file does not exist");
            }
            ArrayList<RecoveryRunnableInfo> recoveryRunnableInfos = getDownloadProgress();
            recoveryRunnableInfos.clear();
            for (DownloadThread runnable : taskId.downloadParts) {
                recoveryRunnableInfos.add(new RecoveryRunnableInfo(runnable
                        .getStartPosition(), runnable.getCurrentPosition(),
                        runnable.getEndPosition()));
            }
            speedMonitor = new SpeedMonitor(this);
            storeMonitor = new StoreMonitor();
            System.out.println("Resume finished");
            downloadParts.clear();
        } catch (JAXBException e) {
            e.printStackTrace();
        }
    }

    public void startTask(DownloadThreadPool threadPool) {
        setDownloadStatus(DOWNLOADING);
        try {
            resumeTask();
        } catch (IOException e) {
            e.printStackTrace();
        }
        threadPoolRef = threadPool;
        if (recoveryRunnableInfos.size() != 0) {
            for (RecoveryRunnableInfo runnableInfo : recoveryRunnableInfos) {
                if (!runnableInfo.isFinished) {
                    DownloadThread runnable = new DownloadThread(monitor,
                            url, saveDirectory, saveName,
                            runnableInfo.getStartPosition(),
                            runnableInfo.getCurrentPosition(),
                            runnableInfo.getEndPosition());
                    downloadParts.add(runnable);
                    threadPool.submit(runnable);
                }
            }
        } else {
            for (DownloadThread runnable : splitDownload(threadCount)) {
                downloadParts.add(runnable);
                threadPool.submit(runnable);
            }
        }
        speedTimer.scheduleAtFixedRate(speedMonitor, 0, 1000);
        storeTimer.scheduleAtFixedRate(storeMonitor, 0, 5000);
    }

    public boolean isFinished() {
        return isFinished;
    }

    public void addPartedTask(DownloadThread runnable) {
        downloadParts.add(runnable);
    }

    private int getContentLength(String fileUrl) throws IOException {
        URL url = new URL(fileUrl);
        URLConnection connection = url.openConnection();
        return connection.getContentLength();
    }

    private Boolean setProgessFile(String dir, String filename)
            throws IOException {
        if (dir.lastIndexOf(File.separator) == dir.length() - 1) {
            dir = dir.substring(0, dir.length() - 1);
        }
        File dirFile = new File(dir);
        if (!dirFile.exists()) {
            if (!dirFile.mkdirs()) {
                throw new RuntimeException("Error to create directory");
            }
        }
        progressDir = dirFile.getPath();
        File file = new File(dirFile.getPath() + File.separator + filename
                + ".tmp");
        if (!file.exists()) {
            file.createNewFile();
        }
        progressFileName = file.getName();
        return true;
    }

    public File getProgressFile() {
        return new File(progressDir + File.separator + progressFileName);
    }

    public File getDownloadFile() {
        return new File(saveDirectory + File.separator + saveName);
    }

    public String getProgressDir() {
        return progressDir;
    }

    public String getProgressFileName() {
        return progressFileName;
    }

    public int getDownloadedSize() {
        return monitor.getDownloadedSize();
    }

    public String getReadableSize() {
        return DownloadHelper.getReadableSize(getDownloadedSize());
    }

    public int getSpeed() {
        return speedMonitor.getSpeed();
    }

    public String getReadableSpeed() {
        return DownloadHelper.getReadableSpeed(getSpeed());
    }

    public int getMaxSpeed() {
        return speedMonitor.getMaxSpeed();
    }

    public String getReadableMaxSpeed() {
        return DownloadHelper.getReadableSpeed(getMaxSpeed());
    }

    public int getAverageSpeed() {
        return speedMonitor.getAverageSpeed();
    }

    public String getReadableAverageSpeed() {
        return DownloadHelper.getReadableSpeed(speedMonitor.getAverageSpeed());
    }

    public int getTimePassed() {
        return speedMonitor.getDownloadedTime();
    }

    public int getActiveTheadCount() {
        return threadPoolRef.getActiveCount();
    }

    public int getFileSize() {
        return fileSize;
    }

    public void pause() {
        setDownloadStatus(PAUSED);
        storeProgress();
        threadPoolRef.pause(taskId);
    }

    private void setDownloadStatus(int status) {
        if (status == FINISHED) {
            isFinished = true;
            speedTimer.cancel();
        }
        taskStatus = status;
    }

    public void storeProgress() {
        try {
            JAXBContext context = JAXBContext.newInstance(JTask.class);
            Marshaller m = context.createMarshaller();
            m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            m.marshal(this, getProgressFile());
        } catch (JAXBException e) {
            e.printStackTrace();
        }
    }

    private void deleteProgressFile() {
        getProgressFile().delete();
    }

    public ArrayList<RecoveryRunnableInfo> getDownloadProgress() {
        return recoveryRunnableInfos;
    }

    public void cancel() {
        deleteProgressFile();
        speedTimer.cancel();
        downloadParts.clear();
        threadPoolRef.cancel(taskId);
    }

    static class RecoveryRunnableInfo {

        private int startPosition;
        private int endPosition;
        private int currentPosition;
        private boolean isFinished = false;

        public RecoveryRunnableInfo(int start, int current, int end) {
            if (end > start && current > start) {
                startPosition = start;
                endPosition = end;
                currentPosition = current;
            } else {
                throw new RuntimeException("position logical error");
            }
            if (currentPosition >= endPosition) {
                isFinished = true;
            }
        }

        public int getStartPosition() {
            return startPosition;
        }

        public int getEndPosition() {
            return endPosition;
        }

        public int getCurrentPosition() {
            return currentPosition;
        }

        public boolean isFinished() {
            return isFinished;
        }
    }

    @XmlRootElement(name = "TaskMonitor")
    @XmlAccessorType(XmlAccessType.NONE)
    static class TaskMonitor {

        public final JTask hostTask;
        @XmlElement(name = "DownloadedSize")
        private AtomicInteger downloadedSize = new AtomicInteger();

        public TaskMonitor() {
            hostTask = null;
        }

        public TaskMonitor(JTask monitorBelongsTo) {
            hostTask = monitorBelongsTo;
        }

        public void down(int size) {
            downloadedSize.addAndGet(size);
            assert hostTask != null;
            if (downloadedSize.intValue() == hostTask.getFileSize()) {
                hostTask.setDownloadStatus(FINISHED);
            }
        }

        public int getDownloadedSize() {
            return downloadedSize.get();
        }

    }

    @XmlRootElement
    @XmlAccessorType(XmlAccessType.NONE)
    private static class SpeedMonitor extends TimerTask {

        @XmlElement(name = "LastSecondSize")
        private int lastSecondSize = 0;
        @XmlElement(name = "CurrentSecondSize")
        private int currentSecondSize = 0;
        @XmlElement(name = "Speed")
        private int speed;
        @XmlElement(name = "MaxSpeed")
        private int maxSpeed;
        @XmlElement(name = "AverageSpeed")
        private int averageSpeed;
        @XmlElement(name = "TimePassed")
        private int counter;

        private JTask hostTask;

        private SpeedMonitor() {
            // never use , for annotation
        }

        public SpeedMonitor(JTask taskIdBelongTo) {
            hostTask = taskIdBelongTo;
        }

        public int getMaxSpeed() {
            return maxSpeed;
        }

        @Override
        public void run() {
            counter++;
            currentSecondSize = hostTask.getDownloadedSize();
            speed = currentSecondSize - lastSecondSize;
            lastSecondSize = currentSecondSize;
            if (speed > maxSpeed) {
                maxSpeed = speed;
            }

            averageSpeed = currentSecondSize / counter;
        }

        public int getDownloadedTime() {
            return counter;
        }

        public int getSpeed() {
            return speed;
        }

        public int getAverageSpeed() {
            return averageSpeed;
        }
    }

    private class StoreMonitor extends TimerTask {
        @Override
        public void run() {
            storeProgress();
        }
    }
}
