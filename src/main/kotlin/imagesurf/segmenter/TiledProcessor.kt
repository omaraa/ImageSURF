package imagesurf.segmenter

import ij.ImageStack
import imagesurf.feature.PixelType
import imagesurf.feature.SurfImage

typealias CurrentIndex = Int
typealias Total = Int

class TiledProcessor(
        private val roiSize: Int,
        private val buffer: Int = 0
) {

    fun process(surfImage: SurfImage, progressCallback: ((CurrentIndex, Total) -> Unit)?, process: (SurfImage) -> List<Any>): ImageStack {

        val nCols = (surfImage.width / roiSize).let { if(surfImage.width%roiSize > 0) it + 1 else it }
        val nRows = (surfImage.height / roiSize).let { if(surfImage.height%roiSize > 0) it + 1 else it }

        val tiles: List<Tile> =
                (0 until nRows).flatMap { row ->
                    (0 until nCols).map { col ->
                        Tile(
                                row = row,
                                col = col,
                                SurfImage = surfImage,
                                roiX = col * roiSize,
                                roiY = row * roiSize,
                                roiTargetWidth = roiSize,
                                roiTargetHeight = roiSize,
                                buffer = buffer
                        )
                    }
                }

        val segmentedStack = (0 until surfImage.totalMergedSlices).map {
            when(surfImage.pixelType) {
                PixelType.GRAY_8_BIT -> ByteArray(surfImage.width * surfImage.height)
                PixelType.GRAY_16_BIT -> ShortArray(surfImage.width * surfImage.height)
            }
        }

        tiles.mapIndexed { index, tile ->

            surfImage.getSubImage(
                    tile.bufferedXStart,
                    tile.bufferedYStart,
                    tile.bufferedWidth,
                    tile.bufferedHeight)
            .let (process)
            .let {tile to it}
            .also { progressCallback?.invoke(index, tiles.size) }
        }.forEach { (tile, segmented) ->
            segmented.forEachIndexed { sliceIndex, pixels ->
                (0 until tile.roiHeight).forEach { tileRowIndex ->
                    val sourceIndex = (tile.bufferedWidth * (tileRowIndex+tile.bufferTop)) + tile.bufferLeft
                    val destinationIndex = ((tileRowIndex + tile.roiY) * surfImage.width) + (tile.col * tile.roiTargetWidth)

                    System.arraycopy(
                            pixels,
                            sourceIndex,
                            segmentedStack[sliceIndex],
                            destinationIndex,
                            tile.roiWidth
                    )
                }
            }
        }

        return segmentedStack.fold(ImageStack(surfImage.width, surfImage.height)) { stack, bytes -> stack.apply { addSlice("", bytes) } }
    }

    data class Tile(
            val row: Int,
            val col: Int,
            val SurfImage: SurfImage,
            val roiX: Int,
            val roiY: Int,
            val roiTargetWidth: Int,
            val roiTargetHeight: Int,
            val buffer: Int
    ) {
        val bufferedXStart = Integer.max(roiX - buffer, 0)
        val bufferedYStart = Integer.max(roiY - buffer, 0)
        private val bufferedXEndExclusive = Integer.min(roiX + roiTargetWidth + buffer, SurfImage.width)
        private val bufferedYEndExclusive = Integer.min(roiY + roiTargetHeight + buffer, SurfImage.height)

        val bufferedWidth = bufferedXEndExclusive - bufferedXStart
        val bufferedHeight = bufferedYEndExclusive - bufferedYStart

        private val roiXEndExclusive = Integer.min(roiX + roiTargetWidth, SurfImage.width)
        val roiYEndExclusive = Integer.min(roiY + roiTargetHeight, SurfImage.height)

        val roiWidth = roiXEndExclusive - roiX
        val roiHeight = roiYEndExclusive - roiY

        val bufferLeft = roiX - bufferedXStart
        val bufferTop = roiY - bufferedYStart


        override fun toString(): String =
                "($bufferedXStart to $bufferedXEndExclusive, $bufferedYStart to $bufferedYEndExclusive) Tile $bufferedXStart,$bufferedYStart $bufferedWidth x $bufferedHeight"
    }
}