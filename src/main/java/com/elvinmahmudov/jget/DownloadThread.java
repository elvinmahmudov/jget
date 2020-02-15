package com.elvinmahmudov.jget;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URL;
import java.net.URLConnection;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "Downloading")
@XmlAccessorType(XmlAccessType.NONE)
public class DownloadThread implements Runnable {

    private static final int BUFFER_SIZE = 1024;

    public int taskId = 1;
    private String fileUrl;
    private String saveDirectory;
    private String saveFileName;
    @XmlElement(name = "StartPosition")
    private int startPosition;
    @XmlElement(name = "EndPosition")
    private int endPosition;
    @XmlElement(name = "CurrentPosition")
    private int currentPosition;

    private JTask.TaskMonitor downloadMonitor;

    private DownloadThread() {
    }

    public DownloadThread(JTask.TaskMonitor monitor, String fileUrl,
                          String saveDirectory, String saveFileName, int startPosition,
                          int endPosition) {
        super();
        this.fileUrl = fileUrl;
        this.saveDirectory = saveDirectory;
        this.saveFileName = saveFileName;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        this.downloadMonitor = monitor;
        this.currentPosition = this.startPosition;
        taskId = monitor.hostTask.taskId;
    }

    public DownloadThread(JTask.TaskMonitor monitor, String fileUrl,
                          String saveDirectory, String saveFileName, int startPosition,
                          int currentPosition, int endPosition) {
        this(monitor, fileUrl, saveDirectory, saveFileName, startPosition,
                endPosition);
        this.currentPosition = currentPosition;
    }

    @Override
    public void run() {
        File targetFile;
        synchronized (this) {
            File dir = new File(saveDirectory + File.pathSeparator);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            targetFile = new File(saveDirectory + File.separator
                    + saveFileName);
            if (!targetFile.exists()) {
                try {
                    targetFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        System.out.println("Download Task ID:" + Thread.currentThread().getId()
                + " has been started! Range From " + currentPosition + " To "
                + endPosition);
        BufferedInputStream bufferedInputStream;
        RandomAccessFile randomAccessFile;
        byte[] buf = new byte[BUFFER_SIZE];
        URLConnection urlConnection;
        try {
            URL url = new URL(fileUrl);
            urlConnection = url.openConnection();
            urlConnection.setRequestProperty("Range", "bytes="
                    + currentPosition + "-" + endPosition);
            randomAccessFile = new RandomAccessFile(targetFile, "rw");
            randomAccessFile.seek(currentPosition);
            bufferedInputStream = new BufferedInputStream(
                    urlConnection.getInputStream());
            while (currentPosition < endPosition) {
                if (Thread.currentThread().isInterrupted()) {
                    System.out.println("Download TaskID:"
                            + Thread.currentThread().getId()
                            + " was interrupted, Start:" + startPosition
                            + " Current:" + currentPosition + " End:"
                            + endPosition);
                    break;
                }
                int len = bufferedInputStream.read(buf, 0, BUFFER_SIZE);
                if (len == -1)
                    break;
                else {
                    randomAccessFile.write(buf, 0, len);
                    currentPosition += len;
                    downloadMonitor.down(len);
                }
            }
            bufferedInputStream.close();
            randomAccessFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public DownloadThread split() {
        int end = endPosition;
        int remaining = endPosition - currentPosition;
        int remainingCenter = remaining / 2;
        System.out.print("CurrentPosition:" + currentPosition
                + " EndPosition:" + endPosition + "Rmaining:" + remaining
                + " ");
        if (remainingCenter > 1048576) {
            int centerPosition = remainingCenter + currentPosition;
            System.out.print(" Center position:" + centerPosition);
            endPosition = centerPosition;

            DownloadThread newSplitedRunnable = new DownloadThread(
                    downloadMonitor, fileUrl, saveDirectory, saveFileName,
                    centerPosition + 1, end);
            downloadMonitor.hostTask.addPartedTask(newSplitedRunnable);
            return newSplitedRunnable;
        } else {
            System.out
                    .println(toString() + " can not be splited ,less than 1M");
            return null;
        }
    }

    public boolean isFinished() {
        return currentPosition >= endPosition;
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    public int getEndPosition() {
        return endPosition;
    }

    public int getStartPosition() {
        return startPosition;
    }

}
