# JGet - Download files from anywhere

<br/>

[![Build Status](https://travis-ci.org/elvinmahmudov/jget.svg?branch=master)](https://travis-ci.org/elvinmahmudov/jget)


### Getting Started
This is basic java downloader library, analog to wget

### Installing
Step 1: Add to maven dependencies

```
        <dependency>
            <groupId>com.github.elvinmahmudov</groupId>
            <artifactId>jget</artifactId>
            <version>1.0</version>
        </dependency>
```

Step 2: Create JGet instance and add task to it

```
public class Main {

    private static String MODEL_NAME = "com.elvinmahmudov.dynocom.MathTeacher";

    public static void main(String[] args) {
		JGet jget = JGet.getInstance();
		String url = "https://file-examples.com/wp-content/uploads/2017/04/file_example_MP4_480_1_5MG.mp4";
		String saveDirectory = "download";

        JTask task = new JTask(url, saveDirectory, "test.mp4");
        jget.addTask(task);
        jget.start();
    }

}
```