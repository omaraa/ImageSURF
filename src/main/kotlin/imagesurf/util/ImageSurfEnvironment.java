package imagesurf.util;

import ij.Prefs;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

public class ImageSurfEnvironment {

    static int numThreads = Prefs.getThreads();
    static ExecutorService featureExecutor = Executors.newFixedThreadPool(numThreads);

    public static void setNumThreads(int numThreads) {
        featureExecutor.shutdown();

        ImageSurfEnvironment.numThreads = numThreads;
        featureExecutor = Executors.newFixedThreadPool(numThreads);
    }

    public static ExecutorService getFeatureExecutor() {
        return featureExecutor;
    }
}
