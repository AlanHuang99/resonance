package com.resonance.music.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueEditsTest {

    @Test fun insertAt_inserts_in_the_middle() {
        assertEquals(listOf("a", "x", "b", "c"), QueueEdits.insertAt(listOf("a", "b", "c"), 1, listOf("x")))
    }

    @Test fun insertAt_clamps_index_past_end_to_append() {
        assertEquals(listOf("a", "b", "x"), QueueEdits.insertAt(listOf("a", "b"), 99, listOf("x")))
    }

    @Test fun insertAt_clamps_negative_index_to_zero() {
        assertEquals(listOf("x", "a", "b"), QueueEdits.insertAt(listOf("a", "b"), -5, listOf("x")))
    }

    @Test fun insertAt_into_empty_list() {
        assertEquals(listOf("x", "y"), QueueEdits.insertAt(emptyList(), 0, listOf("x", "y")))
    }

    @Test fun move_forward() {
        assertEquals(listOf("b", "c", "a", "d"), QueueEdits.move(listOf("a", "b", "c", "d"), 0, 2))
    }

    @Test fun move_backward() {
        assertEquals(listOf("a", "d", "b", "c"), QueueEdits.move(listOf("a", "b", "c", "d"), 3, 1))
    }

    @Test fun move_out_of_range_returns_unchanged() {
        assertEquals(listOf("a", "b"), QueueEdits.move(listOf("a", "b"), 0, 5))
        assertEquals(listOf("a", "b"), QueueEdits.move(listOf("a", "b"), -1, 1))
    }

    @Test fun removeAt_middle() {
        assertEquals(listOf("a", "c"), QueueEdits.removeAt(listOf("a", "b", "c"), 1))
    }

    @Test fun removeAt_out_of_range_returns_unchanged() {
        assertEquals(listOf("a", "b"), QueueEdits.removeAt(listOf("a", "b"), 7))
    }
}
