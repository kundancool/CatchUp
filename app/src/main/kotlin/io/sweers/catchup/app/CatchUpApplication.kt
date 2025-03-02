/*
 * Copyright (C) 2019. Zac Sweers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sweers.catchup.app

import android.app.Application
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import dagger.hilt.android.HiltAndroidApp
import dev.zacsweers.catchup.appconfig.AppConfig
import dev.zacsweers.ticktock.runtime.EagerZoneRulesLoading
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.sweers.catchup.CatchUpPreferences
import io.sweers.catchup.app.ApplicationModule.AsyncInitializers
import io.sweers.catchup.app.ApplicationModule.Initializers
import io.sweers.catchup.flowFor
import io.sweers.catchup.injection.DaggerSet
import io.sweers.catchup.util.d
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import rxdogtag2.RxDogTag
import rxdogtag2.autodispose2.AutoDisposeConfigurer
import timber.log.Timber
import javax.inject.Inject

private typealias InitializerFunction = () -> @JvmSuppressWildcards Unit

@HiltAndroidApp
class CatchUpApplication : Application() {

  companion object {

    init {
      RxAndroidPlugins.setInitMainThreadSchedulerHandler {
        AndroidSchedulers.from(Looper.getMainLooper(), true)
      }
      RxDogTag.builder()
        .configureWith(AutoDisposeConfigurer::configure)
        .install()
    }
  }

  @Inject
  internal lateinit var catchUpPreferences: CatchUpPreferences
  @Inject
  internal lateinit var appConfig: AppConfig

  @Inject
  internal fun plantTimberTrees(trees: DaggerSet<Timber.Tree>) {
    Timber.plant(*trees.toTypedArray())
  }

  @OptIn(DelicateCoroutinesApi::class)
  @Inject
  internal fun asyncInits(@AsyncInitializers asyncInitializers: DaggerSet<InitializerFunction>) {
    GlobalScope.launch(Dispatchers.IO) {
      // TODO - run these in parallel?
      asyncInitializers.forEach { it() }
    }
  }

  @Inject
  internal fun inits(@Initializers initializers: DaggerSet<InitializerFunction>) {
    initializers.forEach { it() }
  }

  @OptIn(DelicateCoroutinesApi::class)
  override fun onCreate() {
    super.onCreate()
    GlobalScope.launch {
      // Initialize TZ data
      try {
        EagerZoneRulesLoading.cacheZones()
      } catch (ignored: NoSuchMethodError) {
        // If targeting a newer device or minSdk 26, this will fail because ZoneRulesProvider is a
        // strangely hidden API: https://issuetracker.google.com/issues/159421054
      }
    }

    GlobalScope.launch {
      catchUpPreferences.flowFor { ::daynightAuto }
        .collect { autoEnabled ->
          d { "Updating daynight" }
          // Someday would like to add activity lifecycle callbacks to automatically call recreate
          // when resumed since this was updated
          var nightMode = AppCompatDelegate.MODE_NIGHT_NO
          if (autoEnabled) {
            nightMode = AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
          } else if (catchUpPreferences.dayNight) {
            nightMode = AppCompatDelegate.MODE_NIGHT_YES
          }
          AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }
  }

  override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    when (level) {
      TRIM_MEMORY_MODERATE,
      TRIM_MEMORY_RUNNING_LOW,
      TRIM_MEMORY_RUNNING_MODERATE,
      TRIM_MEMORY_BACKGROUND,
      TRIM_MEMORY_UI_HIDDEN,
      TRIM_MEMORY_COMPLETE,
      TRIM_MEMORY_RUNNING_CRITICAL -> d { "OnTrimMemory" }
    }
  }
}
