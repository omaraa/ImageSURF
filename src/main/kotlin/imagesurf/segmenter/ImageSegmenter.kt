package imagesurf.segmenter

import ij.ImageStack
import ij.Prefs
import imagesurf.classifier.ImageSurfClassifier
import imagesurf.feature.SurfImage
import imagesurf.util.Utility
import org.scijava.app.StatusService
import java.util.concurrent.ExecutionException

interface ImageSegmenter {
    fun segmentImage(imageSurfClassifier: ImageSurfClassifier, image: SurfImage, statusService: StatusService): ImageStack

    class SimpleImageSegmenter : ImageSegmenter {
        @Throws(ExecutionException::class, InterruptedException::class)
        override fun segmentImage(imageSurfClassifier: ImageSurfClassifier, image: SurfImage, statusService: StatusService): ImageStack {
            if (imageSurfClassifier.pixelType != image.pixelType)
                throw RuntimeException("Classifier pixel type (" +
                        imageSurfClassifier.pixelType + ") does not match image pixel type (" + image.pixelType + ")")

            if (imageSurfClassifier.numChannels != image.numChannels)
                throw RuntimeException("Classifier trained for " + imageSurfClassifier.numChannels + " channels. Image has " + image.numChannels + " - cannot segment.")

            val randomForest = imageSurfClassifier.randomForest.apply { numThreads = Prefs.getThreads() }
            val classColors = Utility.getClassColors(randomForest.numClasses)

            val featuresProgress = Utility.MessageProgress(statusService)
            image.addProgressListener(featuresProgress)

            val segmentProgress = Utility.MessageProgress(statusService)
            randomForest.addProgressListener(segmentProgress)

            return image.getCalculations(imageSurfClassifier.features)
                    .mapIndexed { currentSlice, calculation ->

                        featuresProgress.message = "Calculating features for plane " +
                                "$currentSlice/${image.numChannels * image.numSlices * image.numFrames}"
                        segmentProgress.message = "Segmenting plane " +
                                "$currentSlice/${image.numChannels * image.numSlices * image.numFrames}"

                        calculation.calculate()
                                .let { randomForest.classForInstances(it) }
                                .map(classColors::get).toByteArray()
                    }.fold(ImageStack(image.width, image.height)) { stack, bytes -> stack.apply { addSlice("", bytes) } }
        }
    }
}