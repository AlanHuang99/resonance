package com.resonance.music.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryPagingTest {
    @Test fun pagesDoNotSkipOrRepeatItems() {
        assertEquals(listOf(2, 3), libraryPage((0..4).toList(), 1, 2))
        assertEquals(listOf(4), libraryPage((0..4).toList(), 2, 2))
        assertEquals(emptyList<Int>(), libraryPage((0..4).toList(), 3, 2))
    }
    @Test fun legacyUnboundedPageAndOverflowAreSafe() {
        assertEquals(listOf(1, 2), libraryPage(listOf(1, 2), 0, Int.MAX_VALUE))
        assertEquals(emptyList<Int>(), libraryPage(listOf(1, 2), Int.MAX_VALUE, Int.MAX_VALUE))
        assertEquals(emptyList<Int>(), libraryPage(listOf(1, 2), -1, 2))
        assertEquals(emptyList<Int>(), libraryPage(listOf(1, 2), 0, 0))
    }
}
