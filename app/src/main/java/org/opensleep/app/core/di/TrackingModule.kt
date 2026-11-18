package org.opensleep.app.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.opensleep.app.service.tracking.CompositeSleepSensorSource
import org.opensleep.app.service.tracking.SleepSensorSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TrackingModule {

    @Binds
    @Singleton
    abstract fun bindSleepSensorSource(
        impl: CompositeSleepSensorSource
    ): SleepSensorSource
}
