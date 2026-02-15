package com.unoassistant.overlay.persist

import com.unoassistant.overlay.model.Opponent
import com.unoassistant.overlay.model.OverlayState
import com.unoassistant.overlay.model.UnoColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayStateJsonTest {

    @Test
    fun decodeOrDefault_returnsDefaultWhenRawIsNull() {
        val state = OverlayStateJson.decodeOrDefault(null)
        assertEquals(3, state.maxOpponents)
        assertTrue(state.locked)
        assertEquals(1.0f, state.alpha, 0.0001f)
        assertTrue(state.opponents.isEmpty())
    }

    @Test
    fun encode_thenDecodeOrDefault_keepsCoreFieldsAndEscapedNames() {
        val source = OverlayState(
            overlayX = 120,
            overlayY = 360,
            alpha = 0.72f,
            locked = false,
            controlCollapsed = true,
            maxOpponents = 7,
            opponents = listOf(
                Opponent(
                    id = "id|1",
                    name = "对手;A,测试",
                    excluded = mapOf(
                        UnoColor.Red to true,
                        UnoColor.Yellow to false,
                        UnoColor.Blue to true,
                        UnoColor.Green to false
                    ),
                    offsetX = 520,
                    offsetY = 880
                )
            )
        )

        val encoded = OverlayStateJson.encode(source)
        val decoded = OverlayStateJson.decodeOrDefault(encoded)

        assertEquals(120, decoded.overlayX)
        assertEquals(360, decoded.overlayY)
        assertEquals(0.72f, decoded.alpha, 0.0001f)
        assertEquals(false, decoded.locked)
        assertEquals(true, decoded.controlCollapsed)
        assertEquals(7, decoded.maxOpponents)
        assertEquals(1, decoded.opponents.size)
        assertEquals("id|1", decoded.opponents[0].id)
        assertEquals("对手;A,测试", decoded.opponents[0].name)
        assertEquals(true, decoded.opponents[0].excluded[UnoColor.Red])
        assertEquals(false, decoded.opponents[0].excluded[UnoColor.Yellow])
        assertEquals(true, decoded.opponents[0].excluded[UnoColor.Blue])
        assertEquals(false, decoded.opponents[0].excluded[UnoColor.Green])
        assertEquals(520, decoded.opponents[0].offsetX)
        assertEquals(880, decoded.opponents[0].offsetY)
    }

    @Test
    fun decodeOrDefault_clampsAlphaAndMaxOpponents() {
        val raw = "x=1;y=2;alpha=9.9;locked=1;collapsed=0;max=999;opponents=[]"
        val state = OverlayStateJson.decodeOrDefault(raw)
        assertEquals(1.0f, state.alpha, 0.0001f)
        assertEquals(12, state.maxOpponents)
    }
}
