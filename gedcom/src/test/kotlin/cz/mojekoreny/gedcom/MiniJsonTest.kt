package cz.mojekoreny.gedcom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MiniJsonTest {
    @Test
    fun `cte a pise`() {
        val src = """{"a":1,"b":[true,false,null,"x\nyé"],"c":{"d":-2.5},"e":""}"""
        val v = MiniJson.parseObject(src)
        assertEquals(1L, v["a"])
        assertEquals(listOf(true, false, null, "x\nyé"), v["b"])
        assertEquals(-2.5, (v["c"] as Map<*, *>)["d"])
        assertEquals(v, MiniJson.parse(MiniJson.write(v)))
        assertEquals(v, MiniJson.parse(MiniJson.write(v, pretty = true)))
    }

    @Test
    fun `znaky v retezcich`() {
        val s = "uvozovky \" zpětné \\ tabulátor \t řídicí \u0001 emoji 😀 čeština ěščřžýáíé"
        assertEquals(s, MiniJson.parse(MiniJson.write(s)))
    }

    @Test
    fun `chyby`() {
        assertFailsWith<MiniJson.JsonException> { MiniJson.parse("{") }
        assertFailsWith<MiniJson.JsonException> { MiniJson.parse("[1,]") }
        assertFailsWith<MiniJson.JsonException> { MiniJson.parse("{\"a\" 1}") }
        assertFailsWith<MiniJson.JsonException> { MiniJson.parse("1 2") }
        assertFailsWith<MiniJson.JsonException> { MiniJson.parse("\"abc") }
    }
}
