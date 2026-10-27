package com.gathin.tvhome.feature.main.coordinator

import android.view.View
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.core.motion.Motion


class HomeHeroCoordinator {
    private var heroName: TextView? = null
    private var ambient: View? = null
    private var heroShown = false
    private var glow: HomeGridController? = null

    fun setup(root: View) {
        heroName = root.findViewById(R.id.heroName)
        ambient = root.findViewById(R.id.ambient)
        glow = ambient?.let { HomeGridController(it, root.resources.displayMetrics.density) }
    }

    fun showHero(label: String, color: Int, dockTopInRoot: Float, density: Float) {
        val hero = heroName ?: return
        val targetY = dockTopInRoot - 14 * density - 26 * density - hero.height
        val changed = label != hero.text.toString()
        glow?.setAmbientColor(color, true)

        if (!heroShown) {
            hero.animate().cancel()
            ambient?.animate()?.cancel()
            hero.text = label
            hero.y = targetY
            hero.translationX = 0f
            Motion.DEFAULT_EFFECTS.drive(hero.animate().alpha(1f)).start()
            ambient?.animate()?.alpha(1f)?.setDuration(600)?.start()
            heroShown = true
        } else if (changed) {
            Motion.FAST_EFFECTS.drive(
                hero.animate().alpha(0f).translationX(-12 * density)
            ).withEndAction {
                hero.text = label
                hero.y = targetY
                hero.translationX = 12 * density
                Motion.FAST_SPATIAL.drive(
                    hero.animate().alpha(1f).translationX(0f)
                ).start()
            }.start()
        } else {
            hero.y = targetY
        }
    }

    fun hideHero() {
        heroName?.animate()?.alpha(0f)?.setDuration(200)?.start()
        ambient?.animate()?.alpha(0f)?.setDuration(200)?.start()
        heroShown = false
    }
}
