import java.io.File;

public class SyncedParameters
{
	volatile public static File classifierPath;
	volatile public static String featuresSuffix = TrainImageSurf.DEFAULT_FEATURES_SUFFIX;
	volatile public static File featuresInputPath;
	volatile public static File featuresOutputPath;
}
