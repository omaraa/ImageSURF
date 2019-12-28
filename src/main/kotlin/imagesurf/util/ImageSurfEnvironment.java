package imagesurf.util;

import ij.Prefs;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

public class ImageSurfEnvironment {

    static int numThreads = Prefs.getThreads();
    static ExecutorService featureExecutor = new ForkJoinPool(numThreads);

    public static void setNumThreads(int numThreads) {
        featureExecutor.shutdown();

        ImageSurfEnvironment.numThreads = numThreads;
        featureExecutor = new ForkJoinPool(numThreads);
    }

    public static ExecutorService getFeatureExecutor() {
        return featureExecutor;
    }

    public static int getNumThreads() {
        return numThreads;
    }
}
