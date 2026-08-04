package dev.repcounter.core.dsp

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class FloatRingBufferTest {
    @Test
    fun `holds values up to capacity without eviction`() {
        val buffer = FloatRingBuffer(3)
        assertThat(buffer.push(1f)).isNull()
        assertThat(buffer.push(2f)).isNull()
        assertThat(buffer.push(3f)).isNull()
        assertThat(buffer.size).isEqualTo(3)
        assertThat(buffer.toFloatArray()).isEqualTo(floatArrayOf(1f, 2f, 3f))
    }

    @Test
    fun `evicts oldest value once full`() {
        val buffer = FloatRingBuffer(3)
        listOf(1f, 2f, 3f).forEach(buffer::push)
        val evicted = buffer.push(4f)
        assertThat(evicted).isEqualTo(1f)
        assertThat(buffer.toFloatArray()).isEqualTo(floatArrayOf(2f, 3f, 4f))
    }

    @Test
    fun `clear resets to empty`() {
        val buffer = FloatRingBuffer(2)
        buffer.push(1f)
        buffer.clear()
        assertThat(buffer.size).isEqualTo(0)
        assertThat(buffer.isFull).isFalse()
    }

    @Test
    fun `rejects non-positive capacity`() {
        org.junit.jupiter.api
            .assertThrows<IllegalArgumentException> { FloatRingBuffer(0) }
    }
}
