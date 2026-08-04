package dev.repcounter.core.dsp

/**
 * Fixed-capacity circular buffer of primitive floats. Backing store for the running windows
 * used by [PeakDetector], [CadenceEstimator] and [StreamingFilter] implementations, so those
 * stay allocation-free per sample.
 */
class FloatRingBuffer(
    val capacity: Int,
) {
    init {
        require(capacity > 0) { "capacity must be positive, was $capacity" }
    }

    private val data = FloatArray(capacity)
    private var head = 0
    var size: Int = 0
        private set

    val isFull: Boolean get() = size == capacity

    /** Appends [value], evicting and returning the oldest value once the buffer is full. */
    fun push(value: Float): Float? {
        val evicted = if (isFull) data[head] else null
        data[head] = value
        head = (head + 1) % capacity
        if (size < capacity) size++
        return evicted
    }

    /** Returns the value at [index], `0` being the oldest sample currently held. */
    operator fun get(index: Int): Float {
        require(index in 0 until size) { "index $index out of bounds for size $size" }
        val start = if (isFull) head else 0
        return data[(start + index) % capacity]
    }

    fun toFloatArray(): FloatArray = FloatArray(size) { get(it) }

    fun clear() {
        head = 0
        size = 0
    }
}
