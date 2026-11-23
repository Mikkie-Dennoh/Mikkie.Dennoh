package co.tawipay.topupdesk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UssdSequenceTest {
    @Test
    fun expandsSafaricomTemplateIntoInitialCodeAndMenuReplies() {
        val sequence = requireNotNull(buildUssdSequence(
            template = "*180*6*5*Pn*Am*1*Pin#",
            recipient = "0712345678",
            amount = "100",
            pin = "4321",
        ))
        assertEquals("*180*6*5*0712345678*100*1*4321#", sequence.fullCode)
        assertEquals("*180#", sequence.firstCode)
        assertEquals(listOf("6", "5", "0712345678", "100", "1", "4321"), sequence.replies)
    }

    @Test
    fun rejectsTemplatesThatCouldNotBeSentAsUssd() {
        assertNull(buildUssdSequence("*180*Pn*Am*Pin#;bad", "0712345678", "100", "4321"))
        assertNull(buildUssdSequence("*180**Am*Pin#", "0712345678", "100", "4321"))
    }
}
