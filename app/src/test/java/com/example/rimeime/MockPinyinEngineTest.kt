package com.example.rimeime

import com.example.rimeime.engine.MockPinyinEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockPinyinEngineTest {

    private fun type(engine: MockPinyinEngine, text: String): EngineOutput {
        var last = EngineOutput()
        text.forEach { char ->
            engine.process(KeyAction.Letter(char)) { last = it }
        }
        return last
    }

    @Test
    fun typingNihao_producesComposingAndCandidates() {
        val engine = MockPinyinEngine()
        val output = type(engine, "nihao")
        assertEquals("nihao", output.composing)
        assertEquals("你好", output.candidates.first())
    }

    @Test
    fun space_commitsFirstCandidateAndClearsComposing() {
        val engine = MockPinyinEngine()
        type(engine, "nihao")
        var output = EngineOutput()
        engine.process(KeyAction.Space) { output = it }
        assertEquals("你好", output.commit)
        assertEquals("", output.composing)
    }

    @Test
    fun backspace_shortensComposing() {
        val engine = MockPinyinEngine()
        type(engine, "nihao")
        var output = EngineOutput()
        engine.process(KeyAction.Backspace) { output = it }
        assertEquals("niha", output.composing)
    }

    /** 用户词频：选过的候选下次要往前排。 */
    @Test
    fun selectingCandidate_promotesItNextTime() {
        val engine = MockPinyinEngine()
        type(engine, "shi")
        engine.process(KeyAction.SelectCandidate(2)) { }
        val output = type(engine, "shi")
        assertEquals("事", output.candidates.first())
    }

    @Test
    fun reset_clearsComposing() {
        val engine = MockPinyinEngine()
        type(engine, "nihao")
        engine.reset()
        var output = EngineOutput()
        engine.process(KeyAction.Space) { output = it }
        assertEquals("", output.composing)
        assertEquals(null, output.commit)
    }
}
