package dev.repcounter.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LandmarkSchemaTest {
    @Test
    fun `blazepose exposes all fifteen semantic points`() {
        LandmarkName.entries.forEach { name ->
            assertThat(LandmarkSchema.BLAZEPOSE_33.indexOf(name)).isNotNull()
        }
    }

    @Test
    fun `movenet has no foot-index points`() {
        assertThat(LandmarkSchema.MOVENET_17.indexOf(LandmarkName.LEFT_FOOT_INDEX)).isNull()
        assertThat(LandmarkSchema.MOVENET_17.indexOf(LandmarkName.RIGHT_FOOT_INDEX)).isNull()
    }

    @Test
    fun `movenet and blazepose agree on hip index semantics being schema-local`() {
        // Same semantic name, different raw index - callers must never hardcode indices.
        assertThat(LandmarkSchema.BLAZEPOSE_33.indexOf(LandmarkName.LEFT_HIP)).isEqualTo(23)
        assertThat(LandmarkSchema.MOVENET_17.indexOf(LandmarkName.LEFT_HIP)).isEqualTo(11)
    }
}
