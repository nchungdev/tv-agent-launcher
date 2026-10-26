package com.gathin.tvhome.feature.main.ui

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.feature.runningapps.data.AppLocalDataSource
import com.gathin.tvhome.feature.runningapps.data.AppRepositoryImpl
import com.gathin.tvhome.feature.notification.data.NotificationRepositoryImpl
import com.gathin.tvhome.feature.main.adapter.AppAdapter
import com.gathin.tvhome.feature.main.adapter.Entry
import com.gathin.tvhome.feature.system.coordinator.HomeKeyHandler
import com.gathin.tvhome.feature.system.coordinator.HomeMetricsMonitor
import com.gathin.tvhome.feature.system.coordinator.HomeNetworkManager
import com.gathin.tvhome.feature.settings.ui.LauncherSettingsSheet
import com.gathin.tvhome.feature.notification.ui.NotificationSheet
import com.gathin.tvhome.feature.settings.ui.QuickSettingsSheet
import com.gathin.tvhome.feature.runningapps.ui.RunningAppsSheet
import com.gathin.tvhome.feature.smarthome.ui.SmartHomeSheet
import com.gathin.tvhome.core.design.Fonts
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.feature.main.coordinator.HomeAgentCoordinator
import com.gathin.tvhome.feature.main.coordinator.HomeAppCoordinator
import com.gathin.tvhome.feature.main.coordinator.HomeCardsCoordinator
import com.gathin.tvhome.feature.main.coordinator.HomeGridCoordinator
import com.gathin.tvhome.feature.main.coordinator.HomeHeaderCoordinator
import com.gathin.tvhome.feature.main.coordinator.HomeHeroCoordinator
import com.gathin.tvhome.feature.main.coordinator.HomeThemeCoordinator
import com.gathin.tvhome.feature.main.coordinator.HomeTvInputCoordinator
import com.gathin.tvhome.feature.main.presenter.MainEffect
import com.gathin.tvhome.feature.main.presenter.MainIntent
import com.gathin.tvhome.feature.main.presenter.MainPresenter
import com.gathin.tvhome.feature.main.presenter.MainState
import com.gathin.tvhome.core.util.activityScope
import kotlinx.coroutines.launch


class MainActivity : Activity(), AppAdapter.TileListener {

    private lateinit var root: ViewGroup
    private lateinit var homeConfig: Config
    private lateinit var presenter: MainPresenter
    private lateinit var appAdapter: AppAdapter

    private lateinit var networkManager: HomeNetworkManager
    private lateinit var metricsMonitor: HomeMetricsMonitor
    private lateinit var keyHandler: HomeKeyHandler
    private lateinit var headerCoordinator: HomeHeaderCoordinator
    private lateinit var themeCoordinator: HomeThemeCoordinator
    private lateinit var tvInputCoordinator: HomeTvInputCoordinator
    private lateinit var agentCoordinator: HomeAgentCoordinator
    private lateinit var heroCoordinator: HomeHeroCoordinator
    private lateinit var cardsCoordinator: HomeCardsCoordinator
    private lateinit var appCoordinator: HomeAppCoordinator
    private lateinit var gridCoordinator: HomeGridCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Palette.loadDarkMode(this)
        Fonts.preload(this)
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        homeConfig = Config.load(this)

        initPresentersAndRepositories()
        initCoordinators(homeConfig)
        observePresenter()

        presenter.processIntent(MainIntent.Init)
        reloadAllApps(homeConfig)
    }

    private fun initPresentersAndRepositories() {
        val appRepo = AppRepositoryImpl(AppLocalDataSource(this))
        val notifRepo = NotificationRepositoryImpl.getInstance()
        presenter = MainPresenter(activityScope, appRepo, notifRepo)
    }

    private fun initCoordinators(cfg: Config) {
        networkManager = HomeNetworkManager(this) {}
        metricsMonitor = HomeMetricsMonitor(this, activityScope, {}, {})
        keyHandler = HomeKeyHandler(
            settingsKeycodes = cfg.settingsKeycodes,
            settingsKeyPanel = cfg.settingsKeyPanel,
            onOpenQuickSettings = { QuickSettingsSheet(this, cfg).show() },
            onOpenTvSettings = { /* open tv settings */ },
            onWakeBackdrop = {},
            onDuinchLook = {}
        )
        headerCoordinator = HomeHeaderCoordinator(this) { action ->
            handleHeaderAction(action)
        }
        themeCoordinator = HomeThemeCoordinator(this)
        tvInputCoordinator = HomeTvInputCoordinator(this) { intent ->
            startActivity(intent)
        }
        agentCoordinator = HomeAgentCoordinator(this) {
            presenter.processIntent(MainIntent.OpenNotifications)
        }
        heroCoordinator = HomeHeroCoordinator()
        cardsCoordinator = HomeCardsCoordinator(this) { _, _ -> }
        appCoordinator = HomeAppCoordinator(this, AppRepositoryImpl(AppLocalDataSource(this)))

        appAdapter = AppAdapter(this, this, 0, cfg)
        gridCoordinator = HomeGridCoordinator(
            this,
            onTileFocused = { entry, pos ->
                presenter.processIntent(MainIntent.TileFocused(entry, pos))
            },
            onTileClicked = { entry, pos ->
                presenter.processIntent(MainIntent.TileClicked(entry, pos))
            }
        )

        headerCoordinator.setup(root, cfg)
        agentCoordinator.setup(root)
        heroCoordinator.setup(root)
        gridCoordinator.setup(root, appAdapter)
    }

    private fun observePresenter() {
        activityScope.launch {
            presenter.state.collect { state -> render(state) }
        }
        activityScope.launch {
            presenter.effects.collect { effect -> handleEffect(effect) }
        }
    }

    private fun render(state: MainState) {
        themeCoordinator.applyTheme(state.palette, root)
        headerCoordinator.updateNotificationBadge(state.unreadNotificationCount)
        if (state.greetingText.isNotEmpty()) {
            agentCoordinator.updateGreetingText(state.greetingText)
        }
    }

    private fun handleEffect(effect: MainEffect) {
        when (effect) {
            is MainEffect.LaunchEntry -> launchAppEntry(effect.entry)
            is MainEffect.ShowSettings -> LauncherSettingsSheet(this, homeConfig).show()
            is MainEffect.ShowInputs -> {
                val anchor = findViewById<View>(R.id.btnInputs)
                tvInputCoordinator.showInputs(anchor, root)
            }
            is MainEffect.ShowNotifications -> NotificationSheet(this, homeConfig).show()
            is MainEffect.ShowRunningApps -> RunningAppsSheet(this, homeConfig).show()
            is MainEffect.ShowSmartHome -> SmartHomeSheet(this, homeConfig, {}).show()
            is MainEffect.ShowAppLibrary -> { /* Library dialog */ }
            is MainEffect.ScrollToPosition -> gridCoordinator.focusTile(effect.position)
        }
    }

    private fun handleHeaderAction(action: HomeHeaderCoordinator.HeaderAction) {
        when (action) {
            is HomeHeaderCoordinator.HeaderAction.OpenInputs -> {
                presenter.processIntent(MainIntent.OpenInputs)
            }
            is HomeHeaderCoordinator.HeaderAction.OpenNetwork -> {
                QuickSettingsSheet(this, homeConfig).show()
            }
            is HomeHeaderCoordinator.HeaderAction.ShowNetworkMenu -> {
                QuickSettingsSheet(this, homeConfig).show()
            }
            is HomeHeaderCoordinator.HeaderAction.OpenRunningApps -> {
                presenter.processIntent(MainIntent.OpenRunningApps)
            }
            is HomeHeaderCoordinator.HeaderAction.OpenNotifications -> {
                presenter.processIntent(MainIntent.OpenNotifications)
            }
            is HomeHeaderCoordinator.HeaderAction.OpenSettings -> {
                presenter.processIntent(MainIntent.OpenSettings)
            }
            is HomeHeaderCoordinator.HeaderAction.ShowSettingsMenu -> {
                LauncherSettingsSheet(this, homeConfig).show()
            }
            is HomeHeaderCoordinator.HeaderAction.ToggleReorder -> {
                presenter.processIntent(MainIntent.EndReorder)
            }
            is HomeHeaderCoordinator.HeaderAction.FocusGridFirst -> {
                gridCoordinator.focusDefault()
            }
            is HomeHeaderCoordinator.HeaderAction.FocusGridFromHeader -> {
                gridCoordinator.focusDefault()
            }
        }
    }

    private fun reloadAllApps(cfg: Config) {
        appCoordinator.loadTiles(appAdapter, emptySet(), cfg.moduleCards)
    }

    private fun launchAppEntry(entry: Entry) {
        val launchIntent = packageManager.getLaunchIntentForPackage(entry.pkg)
        if (launchIntent != null) {
            startActivity(launchIntent)
        }
    }

    override fun onTileFocused(position: Int, card: View) {
        val label = appAdapter.labelAt(position)
        val color = appAdapter.ambientColorAt(position)
        val density = resources.displayMetrics.density
        heroCoordinator.showHero(label, color, root.height.toFloat(), density)
        presenter.processIntent(MainIntent.TileFocused(null, position))
    }

    override fun onTileLongPressed(position: Int, card: View) {
        // reorder logic
    }

    override fun onTileVerticalKey(position: Int, keyCode: Int): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && position < 6) {
            val anchor = findViewById<View>(R.id.btnSettings)
            anchor?.requestFocus()
            return true
        }
        return false
    }

    override fun onLibraryRequested() {
        presenter.processIntent(MainIntent.OpenAppLibrary)
    }

    override fun onLibraryFocused() {
        presenter.processIntent(MainIntent.OpenAppLibrary)
    }

    override fun onLibraryUp() {}

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (keyHandler.handleKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    fun updateTopWeather() {}

    override fun onResume() {
        super.onResume()
        isHomeResumed = true
        networkManager.start()
        metricsMonitor.start()
    }

    override fun onPause() {
        super.onPause()
        isHomeResumed = false
        networkManager.stop()
        metricsMonitor.stop()
    }

    override fun onBackPressed() {
        if (presenter.state.value.isReordering) {
            presenter.processIntent(MainIntent.EndReorder)
            return
        }
        super.onBackPressed()
    }

    companion object {
        @Volatile
        var isHomeResumed: Boolean = false

        const val ACTION_SHOW_QUICK_SETTINGS = "com.gathin.tvhome.SHOW_QUICK_SETTINGS"
        const val ACTION_SHOW_RUNNING_APPS = "com.gathin.tvhome.SHOW_RUNNING_APPS"
        const val ACTION_SHOW_INPUTS = "com.gathin.tvhome.SHOW_INPUTS"
        const val ACTION_LAUNCHER_SETTINGS = "com.gathin.tvhome.LAUNCHER_SETTINGS"
    }
}
