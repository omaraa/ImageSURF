package imagesurf

import ij.ImagePlus
import imagesurf.util.ProgressListener
import org.junit.Test
import org.scijava.Context
import org.scijava.app.StatusService
import org.scijava.app.event.StatusEvent
import org.scijava.log.StderrLogService
import org.scijava.plugin.PluginInfo
import java.io.File
import java.nio.file.Files
import org.assertj.core.api.Assertions.assertThat

class BatchApplyImageSurfTest {

    @Test
    fun `classifies pixels accurately`() {
        val classifierFile = File(javaClass.getResource("/batch-apply/ImageSURF.classifier").file)
        val imageFolder = File(javaClass.getResource("/batch-apply/input").file)

        val outputFolder = Files.createTempDirectory("imagesurf-" + System.nanoTime()).toFile()

        val expectedOutputFolder = File(javaClass.getResource("/batch-apply/output").file)

        val outputFiles = BatchApplyImageSurf.batchApplyImageSurf(classifierFile, outputFolder, imageFolder, null, 500, DUMMY_PROGRESS_LISTENER, DUMMY_LOG_SERVICE, DUMMY_STATUS_SERVICE)

        outputFiles.forEach {
            val actual = ImagePlus(it.absolutePath).processor.pixels as ByteArray
            val expected = ImagePlus(File(expectedOutputFolder, it.name).absolutePath).processor.pixels as ByteArray

            actual.zip(expected) { actual, expected -> actual to expected}
                    .filter {(actual, expected) -> actual != expected}
                    .let { different ->
                        assertThat(different.size).isEqualTo(0)
                    }
        }
    }

    companion object {

        val DUMMY_PROGRESS_LISTENER = object : ProgressListener {
            override fun onProgress(current: Int, max: Int, message: String) {
                println("$current/$max: $message")
            }
        }

        val DUMMY_LOG_SERVICE = StderrLogService()

        val DUMMY_STATUS_SERVICE = object : StatusService {
            override fun getInfo(): PluginInfo<*> {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun getContext(): Context = context()

            override fun getPriority(): Double = Double.MAX_VALUE

            override fun setInfo(p0: PluginInfo<*>?) {}

            override fun setPriority(p0: Double) {}

            override fun context(): Context {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun warn(p0: String?) {            }

            override fun clearStatus() {            }

            override fun getStatusMessage(p0: String?, p1: StatusEvent?): String = ""

            override fun showStatus(p0: String?) {}

            override fun showStatus(p0: Int, p1: Int, p2: String?) {}

            override fun showStatus(p0: Int, p1: Int, p2: String?, p3: Boolean) {}

            override fun showProgress(p0: Int, p1: Int) {}
        }
    }
}