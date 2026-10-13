package com.spendsense.app.ui.navigation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationGuardTest {

    private class FakeOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun runIfResumed_ignoresClicksWhileDestinationIsLeavingOrNotYetShown() {
        val owner = FakeOwner()
        var navigations = 0

        owner.registry.currentState = Lifecycle.State.STARTED // entering or leaving (animating)
        owner.runIfResumed { navigations++ }
        assertEquals(0, navigations)

        owner.registry.currentState = Lifecycle.State.RESUMED
        owner.runIfResumed { navigations++ }
        assertEquals(1, navigations)
    }
}
