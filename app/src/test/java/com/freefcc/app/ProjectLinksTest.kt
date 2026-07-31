package com.freefcc.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProjectLinksTest {

    @Test
    fun updaterTargetsForkReleases() {
        assertEquals("NobodySan97/FreeFCC-Custom", ProjectLinks.REPOSITORY)
        assertEquals(
            "https://api.github.com/repos/NobodySan97/FreeFCC-Custom/releases/latest",
            ProjectLinks.LATEST_RELEASE_API
        )
        assertFalse(ProjectLinks.LATEST_RELEASE_API.contains("doesthings/FreeFCC"))
    }
}
