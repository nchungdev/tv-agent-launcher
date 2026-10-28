package com.gathin.tvhome.feature.main.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.agent.data.AgentEventHub
import com.gathin.tvhome.feature.agent.ui.AgentOverlay
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.feature.runningapps.data.AppLocalDataSource
import com.gathin.tvhome.feature.runningapps.data.AppRepositoryImpl
import com.gathin.tvhome.feature.runningapps.data.StuckLayers
import com.gathin.tvhome.feature.notification.data.NotificationRepositoryImpl
import com.gathin.tvhome.feature.main.adapter.AppAdapter
import com.gathin.tvhome.feature.main.data.HomeLook
import com.gathin.tvhome.feature.main.widget.SkyLayoutBinding
import com.gathin.tvhome.feature.main.coordinator.HomeWeatherCoordinator
import com.gathin.tvhome.feature.main.widget.SkyView
import com.gathin.tvhome.feature.main.adapter.Entry
import com.gathin.tvhome.feature.system.coordinator.HomeKeyHandler
import com.gathin.tvhome.feature.system.coordinator.HomeMenuHandler
import com.gathin.tvhome.feature.system.coordinator.HomeMetricsMonitor
import com.gathin.tvhome.feature.system.coordinator.HomeNetworkManager
import com.gathin.tvhome.feature.system.data.Weather
import com.gathin.tvhome.feature.settings.ui.AppLibraryDialog
import com.gathin.tvhome.feature.settings.ui.LauncherSettingsSheet
import com.gathin.tvhome.feature.notification.ui.NotificationSheet
import com.gathin.tvhome.feature.settings.ui.QuickSettingsSheet
import com.gathin.tvhome.feature.runningapps.ui.RunningAppsSheet
import com.gathin.tvhome.feature.smarthome.ui.SmartHomeSheet
import com.gathin.tvhome.feature.settings.ui.HomeAssistantSettingsSheet
import com.gathin.tvhome.feature.settings.data.AgentHomeConfig
import com.gathin.tvhome.core.design.Fonts
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.feature.main.coordinator.DockModeController
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
import com.gathin.tvhome.feature.main.widget.BottomDockView
import com.gathin.tvhome.feature.main.widget.HomeGrid
import com.gathin.tvhome.feature.main.widget.WidgetShelf
import com.gathin.tvhome.core.util.activityScope
import kotlinx.coroutines.launch


class MainActivity : Activity(), AppAdapter.TileListener {

    private lateinit var root: ViewGroup
    private lateinit var weatherCoordinator: HomeWeatherCoordinator
    private lateinit var skyView: SkyView
    private lateinit var homeConfig: Config
    private lateinit var presenter: MainPresenter
    private lateinit var appAdapter: AppAdapter

    private lateinit var appRepo: AppRepositoryImpl
    private val sessionRunningPackages = HashSet<String>()
    private lateinit var menuHandler: HomeMenuHandler
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
    private lateinit var dockModeController: DockModeController
    private lateinit var widgetShelf: WidgetShelf
    private lateinit var agentOverlay: AgentOverlay
    private val unreadListener: (Int) -> Unit = { count ->
        runOnUiThread { presenter.processIntent(MainIntent.UpdateNotificationCount(count)) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Palette.loadDarkMode(this)
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        agentOverlay = AgentOverlay(this, root)
        homeConfig = Config.load(this)
        configureSky(homeConfig)

        initPresentersAndRepositories()
        initCoordinators(homeConfig)
        observePresenter()

        presenter.processIntent(MainIntent.Init)
        reloadAllApps(homeConfig)
    }

    private fun configureSky(cfg: Config) {
        val choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE)
        val backdrop = HomeLook.backdrop(choices)
        val intensity = HomeLook.skyIntensity(backdrop, cfg.skyIntensity, Palette.darkMode())
        skyView = findViewById(R.id.sky)
        skyView.setIntensity(intensity)
        SkyLayoutBinding(root, skyView)
    }

    private fun initPresentersAndRepositories() {
        appRepo = AppRepositoryImpl(AppLocalDataSource(this))
        val notifRepo = NotificationRepositoryImpl.getInstance()
        presenter = MainPresenter(activityScope, appRepo, notifRepo)
        if (homeConfig.backgroundColor != NO_COLOUR) {
            Palette.publish(Palette.fromBackground(homeConfig.backgroundColor))
        }
        Palette.addListener(paletteListener)
        paletteListener.run()
        notifRepo.addListener(unreadListener)
        Thread({ notifRepo.init(applicationContext) }, "notifications-init").start()
    }

    private fun initCoordinators(cfg: Config) {
        menuHandler = HomeMenuHandler(this, cfg)
        headerCoordinator = HomeHeaderCoordinator(this) { action ->
            handleHeaderAction(action)
        }
        networkManager = HomeNetworkManager(this) {
            runOnUiThread { weatherCoordinator.tick() }
        }
        metricsMonitor = HomeMetricsMonitor(
            context = this,
            scope = activityScope,
            onMinuteTick = {
                weatherCoordinator.tick()
                widgetShelf.tick()
            },
            onSysInfoSample = { cpu ->
                headerCoordinator.updateSysInfo(cpu)
            }
        )
        keyHandler = HomeKeyHandler(
            settingsKeycodes = cfg.settingsKeycodes,
            settingsKeyPanel = cfg.settingsKeyPanel,
            onOpenQuickSettings = { QuickSettingsSheet(this, cfg).show() },
            onOpenTvSettings = { /* open tv settings */ },
            onWakeBackdrop = {},
            onDuinchLook = {}
        )
        themeCoordinator = HomeThemeCoordinator(this)
        tvInputCoordinator = HomeTvInputCoordinator(this) { intent ->
            startActivity(intent)
        }
        agentCoordinator = HomeAgentCoordinator(this) { agentOverlay.open() }
        heroCoordinator = HomeHeroCoordinator()
        cardsCoordinator = HomeCardsCoordinator(this) { _, _ -> }
        appCoordinator = HomeAppCoordinator(this, AppRepositoryImpl(AppLocalDataSource(this)))

        val cols = if (cfg.columns > 0) cfg.columns else 4
        appAdapter = AppAdapter(this, this, cols, cfg)
        gridCoordinator = HomeGridCoordinator(
            activity = this,
            columns = cols,
            onTileFocused = { entry, pos ->
                presenter.processIntent(MainIntent.TileFocused(entry, pos))
            },
            onTileClicked = { entry, pos ->
                presenter.processIntent(MainIntent.TileClicked(entry, pos))
            }
        )

        weatherCoordinator = HomeWeatherCoordinator(
            activityScope, HomeWeatherCoordinator.source(this),
            { Weather.selectedCity(this) },
            { report, scene ->
                headerCoordinator.updateWeather(report, scene.day)
                widgetShelf.updateWeather(report, scene.day, weatherCoordinator.offline)
                skyView.setScene(scene)
            }
        )
        headerCoordinator.onWeatherRefresh = { weatherCoordinator.tick(true) }

        val dockView = root.findViewById<BottomDockView>(R.id.bottomDock)
        val heroName = root.findViewById<View>(R.id.heroName)
        val homeGrid = root.findViewById<HomeGrid>(R.id.grid)
        val shelfView = root.findViewById<View>(R.id.widgetShelf)

        widgetShelf = WidgetShelf(
            activity = this,
            shelf = shelfView,
            animations = cfg.animations,
            reducedMotion = cfg.reducedMotion,
            advancedEffects = cfg.advancedEffects,
            host = object : WidgetShelf.Host {
                override fun showMenu(anchor: View, items: List<WidgetShelf.MenuItem>) {
                    menuHandler.showContextMenu(anchor, items)
                }
                override fun slotsChanged() {}
                override fun carryChanged() {}
                override fun startEditing(): Boolean = false
                override fun openSmartHome() {
                    val connection = AgentHomeConfig(this@MainActivity)
                    val configured = connection.homeAssistantUrl.isNotBlank() &&
                        connection.homeAssistantToken.isNotBlank()
                    if (configured) {
                        presenter.processIntent(MainIntent.OpenSmartHome)
                    } else {
                        openHomeAssistant()
                    }
                }
                override fun openHomeAssistant() {
                    HomeAssistantSettingsSheet(this@MainActivity, homeConfig).show()
                }
            }
        )
        widgetShelf.onWeatherLocationChanged = { weatherCoordinator.reloadLocation() }

        dockModeController = DockModeController(
            activity = this,
            dockView = dockView,
            grid = homeGrid,
            heroName = heroName,
            adapter = appAdapter,
            widgets = widgetShelf,
            host = object : DockModeController.Host {
                override fun openLibrary() {
                    presenter.processIntent(MainIntent.OpenAppLibrary)
                }

                override fun showContextMenu(anchor: View, items: List<WidgetShelf.MenuItem>) {
                    menuHandler.showContextMenu(anchor, items)
                }
                override fun focusTopHeader() {
                    val anchor = findViewById<View>(R.id.btnSettings)
                    anchor?.requestFocus()
                }

                override fun onAppsChanged() {
                    reloadAllApps(homeConfig)
                }

                override fun hideGridChrome() {}
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
        dockModeController.setPalette(state.palette)
        widgetShelf.setPalette(state.palette)
        if (state.greetingText.isNotEmpty()) {
            agentCoordinator.updateGreetingText(state.greetingText)
        }
    }

    private fun handleEffect(effect: MainEffect) {
        when (effect) {
            is MainEffect.LaunchEntry -> launchAppEntry(effect.entry)
            is MainEffect.ShowSettings -> LauncherSettingsSheet(this, homeConfig, widgetShelf.continueFeed()) {
                if (it) recreate()
            }.show()
            is MainEffect.ShowInputs -> {
                val anchor = findViewById<View>(R.id.btnInputs)
                tvInputCoordinator.showInputs(anchor, root)
            }
            is MainEffect.ShowNotifications -> NotificationSheet(this, homeConfig).show()
            is MainEffect.ShowRunningApps -> RunningAppsSheet(this, homeConfig).show()
            is MainEffect.ShowSmartHome -> SmartHomeSheet(this, homeConfig, {
                HomeAssistantSettingsSheet(this, homeConfig).show()
            }).show()
            is MainEffect.ShowAppLibrary -> {
                AppLibraryDialog(
                    activity = this,
                    animations = homeConfig.animations,
                    reducedMotion = homeConfig.reducedMotion,
                    onHome = { pkg -> appAdapter.containsPackage(pkg) },
                    changed = { reloadAllApps(homeConfig) },
                    dismissed = {}
                ).show()
            }
            is MainEffect.ScrollToPosition -> gridCoordinator.focusTile(effect.position)
        }
    }

    private fun handleHeaderAction(action: HomeHeaderCoordinator.HeaderAction) {
        when (action) {
            is HomeHeaderCoordinator.HeaderAction.OpenInputs -> {
                presenter.processIntent(MainIntent.OpenInputs)
            }
            is HomeHeaderCoordinator.HeaderAction.OpenNetwork -> {
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    startActivity(intent)
                } catch (_: Exception) {
                    try {
                        startActivity(
                            Intent(Settings.ACTION_WIRELESS_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {
                        QuickSettingsSheet(this, homeConfig).show()
                    }
                }
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
                LauncherSettingsSheet(this, homeConfig, widgetShelf.continueFeed()) {
                    if (it) recreate()
                }.show()
            }
            is HomeHeaderCoordinator.HeaderAction.ToggleReorder -> {
                presenter.processIntent(MainIntent.EndReorder)
            }
            is HomeHeaderCoordinator.HeaderAction.FocusGridFirst -> {
                if (dockModeController.isDockMode()) {
                    dockModeController.focusDefault()
                } else {
                    gridCoordinator.focusDefault()
                }
            }
            is HomeHeaderCoordinator.HeaderAction.FocusGridFromHeader -> {
                if (widgetShelf.enabled() && widgetShelf.focusFromHeader(left = false)) {
                    // focused into widget shelf
                } else if (dockModeController.isDockMode()) {
                    dockModeController.focusDefault()
                } else {
                    gridCoordinator.focusDefault()
                }
            }
        }
    }

    private fun reloadAllApps(cfg: Config) {
        appCoordinator.loadTiles(appAdapter, emptySet(), cfg.moduleCards)
        dockModeController.applyMode(
            mode = cfg.layoutMode,
            dockApps = cfg.dockApps,
            columns = cfg.columns,
            dockTitleEnabled = cfg.dockTitle
        )
        refreshRunningApps()
        root.post {
            dockModeController.alignShelf(cfg.columns)
        }
    }

    private fun refreshRunningApps() {
        activityScope.launch {
            val detected = try {
                appRepo.getRunningApps().getOrNull()?.map { it.pkg }?.toSet() ?: emptySet()
            } catch (_: Exception) {
                emptySet()
            }
            val combined = sessionRunningPackages + detected
            dockModeController.updateRunningApps(combined)
        }
    }

    private fun launchAppEntry(entry: Entry) {
        sessionRunningPackages.add(entry.pkg)
        dockModeController.updateRunningApps(sessionRunningPackages)
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
        if (agentOverlay.isOpen) return super.dispatchKeyEvent(event)
        if (keyHandler.handleKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        headerCoordinator.start()
        isHomeResumed = true
        agentOverlay.onResume()
        AgentEventHub.greetingListener = { text, mood ->
            agentCoordinator.showPushedGreeting(text, mood)
        }
        AgentEventHub.ensureStarted(this)
        networkManager.start()
        metricsMonitor.start()
        weatherCoordinator.start()
        headerCoordinator.updateSysInfo(0)
        widgetShelf.start()
        refreshRunningApps()
        StuckLayers.scheduleCheck(this) { isHomeResumed }

        val fresh = Config.load(this)
        if (fresh.layoutMode != homeConfig.layoutMode || fresh.columns != homeConfig.columns) {
            homeConfig = fresh
            recreate()
        }
    }

    override fun onPause() {
        super.onPause()
        headerCoordinator.stop()
        isHomeResumed = false
        StuckLayers.cancel()
        agentOverlay.onPause()
        AgentEventHub.greetingListener = null
        networkManager.stop()
        weatherCoordinator.stop()
        metricsMonitor.stop()
        widgetShelf.stop()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        headerCoordinator.onActivityResult(requestCode, resultCode, data)
    }

    private val paletteListener = Runnable {
        val chosen = homeConfig.backgroundColor != NO_COLOUR
        val palette = if (chosen) Palette.fromBackground(homeConfig.backgroundColor)
        else Palette.currentOrBaseline()
        presenter.processIntent(MainIntent.UpdatePalette(palette))
    }

    override fun onDestroy() {
        Palette.removeListener(paletteListener)
        NotificationRepositoryImpl.getInstance().removeListener(unreadListener)
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        code: Int,
        permissions: Array<out String>,
        results: IntArray
    ) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == com.gathin.tvhome.feature.watching.ui.WatchNextSourceSheet.REQUEST_CODE) {
            widgetShelf.continueFeed().refresh()
        } else agentOverlay.onPermissionResult(code, results)
    }

    override fun onBackPressed() {
        if (presenter.state.value.isReordering) {
            presenter.processIntent(MainIntent.EndReorder)
            return
        }
        super.onBackPressed()
    }

    companion object {
        private const val NO_COLOUR = -0x1000000
        @Volatile
        var isHomeResumed: Boolean = false

        const val ACTION_SHOW_QUICK_SETTINGS = "com.gathin.tvhome.SHOW_QUICK_SETTINGS"
        const val ACTION_SHOW_RUNNING_APPS = "com.gathin.tvhome.SHOW_RUNNING_APPS"
        const val ACTION_SHOW_INPUTS = "com.gathin.tvhome.SHOW_INPUTS"
        const val ACTION_LAUNCHER_SETTINGS = "com.gathin.tvhome.LAUNCHER_SETTINGS"
    }
}
