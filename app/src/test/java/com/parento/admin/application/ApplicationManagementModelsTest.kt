package com.parento.admin.application

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationManagementModelsTest {
    @Test fun validPackageNamesAreAccepted() {
        assertTrue(validAndroidPackageName("com.example.app"))
        assertTrue(validAndroidPackageName("com.example_app.child"))
    }

    @Test fun malformedPackageNamesAreRejected() {
        assertFalse(validAndroidPackageName(""))
        assertFalse(validAndroidPackageName("example"))
        assertFalse(validAndroidPackageName("com..example"))
        assertFalse(validAndroidPackageName("com.example app"))
    }

    @Test fun enforcementStatesDoNotCollapseIntoBlockedBoolean() {
        assertTrue(EnforcementStatus.PENDING != EnforcementStatus.APPLIED)
        assertTrue(EnforcementStatus.FAILED != EnforcementStatus.APPLIED)
        assertTrue(EnforcementStatus.UNSUPPORTED != EnforcementStatus.APPLIED)
        assertTrue(EnforcementStatus.REVOKED != EnforcementStatus.APPLIED)
    }
}
