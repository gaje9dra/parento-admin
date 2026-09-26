package com.parento.admin.ui

import com.parento.admin.domain.AdminError
import com.parento.admin.domain.DeviceLocation
import com.parento.admin.domain.LocationAvailability
import com.parento.admin.domain.LocationFreshness
import com.parento.admin.domain.OperationResult
import com.parento.admin.location.DeviceLocationRepository
import com.parento.admin.location.DeviceLocationUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun freshLocationMapsToAvailableState() = runTest {
        val vm = LocationViewModel("device-1", DeviceLocationUseCase(FakeRepository(
            OperationResult.Success(location(LocationFreshness.FRESH)),
        )))
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value is LocationUiState.Available)
    }

    @Test
    fun staleLocationMapsToStaleState() = runTest {
        val vm = LocationViewModel("device-1", DeviceLocationUseCase(FakeRepository(
            OperationResult.Success(location(LocationFreshness.STALE)),
        )))
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value is LocationUiState.Stale)
    }

    @Test
    fun veryStaleLocationMapsToVeryStaleState() = runTest {
        val vm = LocationViewModel("device-1", DeviceLocationUseCase(FakeRepository(
            OperationResult.Success(location(LocationFreshness.VERY_STALE)),
        )))
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value is LocationUiState.VeryStale)
    }

    @Test
    fun missingLocationMapsToNeverReported() = runTest {
        val vm = LocationViewModel("device-1", DeviceLocationUseCase(FakeRepository(
            OperationResult.Success(null),
        )))
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value is LocationUiState.NeverReported)
    }

    @Test
    fun authorizationFailureMapsToUnauthorized() = runTest {
        val vm = LocationViewModel("device-1", DeviceLocationUseCase(FakeRepository(
            OperationResult.Failure(AdminError.Authorization),
        )))
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value is LocationUiState.Unauthorized)
    }

    private fun location(freshness: LocationFreshness) = DeviceLocation(
        deviceId = "device-1",
        latitude = 26.9124,
        longitude = 75.7873,
        accuracyMeters = 25.0,
        altitudeMeters = null,
        bearingDegrees = null,
        speedMetersPerSecond = null,
        observedAtEpochMillis = 1_000L,
        receivedAtEpochMillis = 2_000L,
        availability = LocationAvailability.AVAILABLE,
        freshness = freshness,
    )

    private class FakeRepository(
        private val result: OperationResult<DeviceLocation?>,
    ) : DeviceLocationRepository {
        override suspend fun getLatest(deviceId: String): OperationResult<DeviceLocation?> = result
    }
}