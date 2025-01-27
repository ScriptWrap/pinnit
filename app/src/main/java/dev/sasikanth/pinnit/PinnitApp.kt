package dev.sasikanth.pinnit

import android.app.Application
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.core.DataStore
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.sasikanth.pinnit.data.preferences.AppPreferences
import dev.sasikanth.pinnit.utils.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject


@HiltAndroidApp
class PinnitApp : Application(), Configuration.Provider {

  @Inject
  lateinit var appPreferencesStore: DataStore<AppPreferences>

  @Inject
  lateinit var configuration: Configuration

  @Inject
  lateinit var dispatcherProvider: DispatcherProvider

  private val mainScope by lazy {
    CoroutineScope(dispatcherProvider.main)
  }

  override val workManagerConfiguration: Configuration
    get() = configuration

  override fun onCreate() {
    if (BuildConfig.DEBUG) {
      StrictMode.setThreadPolicy(
        ThreadPolicy.Builder()
          .detectDiskReads()
          .detectDiskWrites()
          .penaltyLog()
          .build()
      )
      StrictMode.setVmPolicy(
        VmPolicy.Builder()
          .detectLeakedSqlLiteObjects()
          .detectLeakedClosableObjects()
          .penaltyLog()
          .penaltyDeath()
          .build()
      )
    }

    super.onCreate()

    appPreferencesStore
      .data
      .map { it.theme }
      .onEach(::setAppTheme)
      .launchIn(mainScope)
  }

  private fun setAppTheme(theme: AppPreferences.Theme) = when (theme) {
    AppPreferences.Theme.DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    AppPreferences.Theme.LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    AppPreferences.Theme.AUTO -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
  }
}
