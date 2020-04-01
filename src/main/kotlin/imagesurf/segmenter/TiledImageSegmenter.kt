package imagesurf.segmenter

import ij.ImageStack
import imagesurf.classifier.ImageSurfClassifier
import imagesurf.feature.SurfImage
import org.scijava.app.StatusService
import java.util.concurrent.ExecutionException

class TiledImageSegmenter(
        val tileSize: Int
) : ImageSegmenter {

    private val simpleImageSegmenter = ImageSegmenter.SimpleImageSegmenter()

    @Throws(ExecutionException::class, InterruptedException::class)
    override fun segmentImage(imageSurfClassifier: ImageSurfClassifier, surfImage: SurfImage, statusService: StatusService): ImageStack {

        val buffer = imageSurfClassifier.features.map { it.radius }.max()!!
        val roiSize = tileSize - (buffer * 2)

        val tiledStatus: ( CurrentIndex, Total) -> Unit = { currentIndex, total ->
            statusService.showStatus(currentIndex, total, "Tile ${currentIndex+1}/$total") }

        val processor = TiledProcessor(roiSize, buffer)

        return processor.process(surfImage, tiledStatus) {
            simpleImageSegmenter.segmentImage(imageSurfClassifier, it, statusService).toPixels()
        }
    }

    private fun ImageStack.toPixels() = (0 until this.size).map { index ->
        this.getPixels(index + 1)
    }
}