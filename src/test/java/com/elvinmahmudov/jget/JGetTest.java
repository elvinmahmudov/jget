package com.elvinmahmudov.jget;

import java.io.IOException;

public class JGetTest {

    public static void main(String[] args) {
        JGet jget = JGet.getInstance();
        String url1 = "https://file-examples.com/wp-content/uploads/2017/04/file_example_MP4_480_1_5MG.mp4";

        String saveDirectory = "download";
        try {
            JTask task = new JTask(url1,
                    saveDirectory, "test.mp4");
            jget.addTask(task);
            jget.start();
            while (true) {
                System.out.println("Downloader information Speed:"
                        + jget.getReadableTotalSpeed()
                        + " Down Size:"
                        + jget.getReadableDownloadSize());
                Thread.sleep(1000);
            }
        } catch (IOException | InterruptedException e1) {
            e1.printStackTrace();
        }
    }
}
