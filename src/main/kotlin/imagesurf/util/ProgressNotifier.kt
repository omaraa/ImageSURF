package imagesurf.util

import java.util.HashSet

interface ProgressNotifier {
    fun addProgressListener(listener: ProgressListener)
    fun addProgressListeners(listeners: Collection<ProgressListener>)
            = listeners.forEach(this::addProgressListener)
    fun removeProgressListener(listener: ProgressListener)
    fun removeProgressListeners(listeners: Collection<ProgressListener>)
            = listeners.forEach(this::removeProgressListener)

    fun onProgress(current: Int, max: Int, message: String)
}

interface ProgressListener {
    fun onProgress(current: Int, max: Int, message: String)
}

class BasicProgressNotifier : ProgressNotifier{
    private val progressListeners: HashSet<ProgressListener> = HashSet()

    override fun addProgressListener(listener: ProgressListener) {
        progressListeners.add(listener)
    }

    override fun addProgressListeners(listeners: Collection<ProgressListener>) {
        progressListeners.addAll(listeners)
    }

    override fun removeProgressListener(listener: ProgressListener) {
        progressListeners.remove(listener)
    }

    override fun removeProgressListeners(listeners: Collection<ProgressListener>) {
        progressListeners.removeAll(listeners)
    }

    override fun onProgress(current: Int, max: Int, message: String) {
        for (p in progressListeners)
            p.onProgress(current, max, message)
    }
}