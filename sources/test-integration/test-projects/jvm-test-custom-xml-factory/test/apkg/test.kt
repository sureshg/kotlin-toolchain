package apkg

import javax.xml.stream.XMLOutputFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ATest {
    @Test
    fun smoke() {
        assertEquals(
            expected = "apkg.OutputFactoryImpl",
            actual = XMLOutputFactory.newFactory().javaClass.canonicalName,
            message = "The Kotlin Toolchain's test infra should respect our custom XMLOutputFactory implementq"
        )
    }
}
