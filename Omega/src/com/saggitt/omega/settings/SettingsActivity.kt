/*
 *  This file is part of Omega Launcher.
 *  Copyright (c) 2021   Saul Henriquez
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.saggitt.omega.settings

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.*
import androidx.annotation.XmlRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.preference.*
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.*
import com.android.launcher3.R
import com.android.launcher3.notification.NotificationListener
import com.android.launcher3.settings.NotificationDotsPreference
import com.android.launcher3.settings.PreferenceHighlighter
import com.farmerbb.taskbar.lib.Taskbar
import com.saggitt.omega.changeDefaultHome
import com.saggitt.omega.preferences.ControlledPreference
import com.saggitt.omega.preferences.PreferenceController
import com.saggitt.omega.preferences.ResumablePreference
import com.saggitt.omega.preferences.SubPreference
import com.saggitt.omega.settings.search.SettingsSearchActivity
import com.saggitt.omega.theme.ThemeOverride
import com.saggitt.omega.theme.ui.ThemeListDialogFragment
import com.saggitt.omega.theme.ui.ThemePreference
import com.saggitt.omega.util.SettingsObserver
import com.saggitt.omega.views.SpringRecyclerView

open class SettingsActivity : SettingsBaseActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
    PreferenceFragment.OnPreferenceDisplayDialogCallback,
    FragmentManager.OnBackStackChangedListener, View.OnClickListener {
    private var isSubSettings = false
    private var forceSubSettings = false
    private var hasPreview = false
    override fun onCreate(savedInstanceState: Bundle?) {
        var savedInstanceState: Bundle? = savedInstanceState
        savedInstanceState = getRelaunchInstanceState(savedInstanceState)
        val fragmentName = intent.getStringExtra(EXTRA_FRAGMENT)
        val content = intent.getIntExtra(SubSettingsFragment.CONTENT_RES_ID, 0)
        isSubSettings = content != 0 || fragmentName != null || forceSubSettings
        hasPreview = intent.getBooleanExtra(SubSettingsFragment.HAS_PREVIEW, false)
        val showSearch = shouldShowSearch()
        super.onCreate(savedInstanceState)
        decorLayout.hideToolbar = showSearch
        setContentView(if (showSearch) R.layout.activity_settings_home else R.layout.activity_settings)
        if (savedInstanceState == null) {
            val fragment = createLaunchFragment(intent)

            // Display the fragment as the main content.
            supportFragmentManager.beginTransaction()
                .replace(R.id.content, fragment)
                .commit()
        }
        supportFragmentManager.addOnBackStackChangedListener(this)
        updateUpButton()
        if (showSearch) {
            val toolbar = findViewById<Toolbar>(R.id.search_action_bar)
            toolbar.setOnClickListener(this)
        }
        if (hasPreview) {
            overrideOpenAnim()
        }
        defaultHome = resolveDefaultHome()
    }

    private fun updateUpButton(enabled: Boolean = isSubSettings || supportFragmentManager.backStackEntryCount != 0) {
        if (supportActionBar == null) {
            return
        }
        supportActionBar!!.setDisplayHomeAsUpEnabled(enabled)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackStackChanged() {
        updateUpButton()
    }

    protected open fun shouldShowSearch(): Boolean {
        return Utilities.getOmegaPrefs(applicationContext).settingsSearch && !isSubSettings
    }

    override fun onClick(v: View) {
        if (v.id == R.id.search_action_bar) {
            startActivity(Intent(this, SettingsSearchActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        if (shouldShowSearch()) {
            val search: Drawable =
                ResourcesCompat.getDrawable(resources, R.drawable.ic_settings_search, null)!!
            search.setTint(Utilities.getOmegaPrefs(applicationContext).accentColor)
            val toolbar = findViewById<Toolbar>(R.id.search_action_bar)
            toolbar.navigationIcon = search
            toolbar.menu.clear()
            toolbar.inflateMenu(R.menu.menu_settings)
            var menuView: ActionMenuView? = null
            val count = toolbar.childCount
            for (i in 0 until count) {
                val child = toolbar.getChildAt(i)
                if (child is ActionMenuView) {
                    menuView = child
                    break
                }
            }
            menuView?.overflowIcon?.setTint(
                Utilities.getOmegaPrefs(
                    applicationContext
                ).accentColor
            )
            if (BuildConfig.APPLICATION_ID != resolveDefaultHome()) {
                toolbar.inflateMenu(R.menu.menu_change_default_home)
            }
            toolbar.setOnMenuItemClickListener { menuItem: MenuItem ->
                when (menuItem.itemId) {
                    R.id.action_change_default_home -> changeDefaultHome(this)
                    R.id.action_restart_launcher -> Utilities.killLauncher()
                    R.id.action_dev_options -> {
                        val intent = Intent(this, SettingsActivity::class.java)
                        intent.putExtra(
                            SubSettingsFragment.TITLE,
                            getString(R.string.developer_options_title)
                        )
                        intent.putExtra(
                            SubSettingsFragment.CONTENT_RES_ID,
                            R.xml.omega_preferences_developer
                        )
                        intent.putExtra(EXTRA_FROM_SETTINGS, true)
                        startActivity(intent)
                    }
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
        }
    }

    private fun resolveDefaultHome(): String? {
        val homeIntent: Intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
        val info: ResolveInfo? = packageManager
            .resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        return if (info?.activityInfo != null) {
            info.activityInfo.packageName
        } else {
            null
        }
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        preference: Preference
    ): Boolean {
        val fragment: Fragment = if (preference is SubPreference) {
            preference.start(this)
            return true
        } else {
            Fragment.instantiate(this, preference.fragment, preference.extras)
        }
        if (fragment is DialogFragment) {
            fragment.show(supportFragmentManager, preference.key)
        } else {
            startFragment(this, preference.fragment, preference.extras, preference.title)
        }
        return true
    }

    override fun finish() {
        super.finish()
        if (hasPreview) {
            overrideCloseAnim()
        }
    }

    override fun onPreferenceDisplayDialog(caller: PreferenceFragment, pref: Preference): Boolean {
        return false
    }

    protected open fun createLaunchFragment(intent: Intent): Fragment {
        val title: CharSequence = intent.getCharSequenceExtra(EXTRA_TITLE) ?: ""
        if (title.isEmpty()) {
            setTitle(title)
        }
        val fragment: String = intent.getStringExtra(EXTRA_FRAGMENT) ?: ""
        if (fragment.isNotEmpty()) {
            return Fragment.instantiate(this, fragment, intent.getBundleExtra(EXTRA_FRAGMENT_ARGS))
        }
        val content: Int = intent.getIntExtra(SubSettingsFragment.CONTENT_RES_ID, 0)
        return if (content != 0) SubSettingsFragment.newInstance(getIntent()) else LauncherSettingsFragment()
    }

    abstract class BaseFragment : PreferenceFragmentCompat() {
        private var mAdapter: HighlightablePreferenceGroupAdapter? = null
        private var mPreferenceHighlighted = false
        var mHighLightKey: String? = null
        private var mCurrentRootAdapter: RecyclerView.Adapter<*>? = null
        private var mIsDataSetObserverRegistered = false
        private val mDataSetObserver: RecyclerView.AdapterDataObserver =
            object : RecyclerView.AdapterDataObserver() {
                override fun onChanged() {
                    onDataSetChanged()
                }

                override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                    onDataSetChanged()
                }

                override fun onItemRangeChanged(
                    positionStart: Int, itemCount: Int,
                    payload: Any?
                ) {
                    onDataSetChanged()
                }

                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    onDataSetChanged()
                }

                override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                    onDataSetChanged()
                }

                override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                    onDataSetChanged()
                }
            }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            if (savedInstanceState != null) {
                mPreferenceHighlighted = savedInstanceState.getBoolean(SAVE_HIGHLIGHTED_KEY)
            }
        }

        private fun highlightPreferenceIfNeeded() {
            if (!isAdded) {
                return
            }
            if (mAdapter != null) {
                mAdapter!!.requestHighlight(requireView(), listView)
            }
        }

        @SuppressLint("RestrictedApi")
        override fun onCreateRecyclerView(
            inflater: LayoutInflater, parent: ViewGroup,
            savedInstanceState: Bundle?
        ): RecyclerView {
            val recyclerView: RecyclerView = inflater
                .inflate(recyclerViewLayoutRes, parent, false) as RecyclerView
            if (recyclerView is SpringRecyclerView) {
                recyclerView.shouldTranslateSelf = false
            }
            recyclerView.layoutManager = onCreateLayoutManager()
            recyclerView.setAccessibilityDelegateCompat(
                PreferenceRecyclerViewAccessibilityDelegate(recyclerView)
            )
            return recyclerView
        }

        protected abstract val recyclerViewLayoutRes: Int
        override fun setDivider(divider: Drawable) {
            super.setDivider(null)
        }

        override fun setDividerHeight(height: Int) {
            super.setDividerHeight(0)
        }

        override fun onCreateAdapter(preferenceScreen: PreferenceScreen): RecyclerView.Adapter<*> {
            val arguments: Bundle? = activity?.intent?.extras
            mAdapter = HighlightablePreferenceGroupAdapter(
                preferenceScreen,
                arguments?.getString(EXTRA_FRAGMENT_ARG_KEY),
                mPreferenceHighlighted
            )
            return mAdapter as HighlightablePreferenceGroupAdapter
        }

        override fun onSaveInstanceState(outState: Bundle) {
            super.onSaveInstanceState(outState)
            if (mAdapter != null) {
                outState.putBoolean(SAVE_HIGHLIGHTED_KEY, mAdapter!!.isHighlightRequested)
            }
        }

        protected fun onDataSetChanged() {
            highlightPreferenceIfNeeded()
        }

        val initialExpandedChildCount: Int
            get() = -1

        override fun onResume() {
            super.onResume()
            highlightPreferenceIfNeeded()
            if (isAdded && !mPreferenceHighlighted) {
                val highlighter: PreferenceHighlighter? = createHighlighter()
                if (highlighter != null) {
                    view?.postDelayed(highlighter, DELAY_HIGHLIGHT_DURATION_MILLIS.toLong())
                    mPreferenceHighlighted = true
                }
            }
            dispatchOnResume(preferenceScreen)
        }

        private fun createHighlighter(): PreferenceHighlighter? {
            if (TextUtils.isEmpty(mHighLightKey)) {
                return null
            }
            val screen = preferenceScreen ?: return null
            val list = listView
            val callback = list.adapter as PreferenceGroup.PreferencePositionCallback?
            val position = callback!!.getPreferenceAdapterPosition(mHighLightKey)
            return if (position >= 0) PreferenceHighlighter(
                list, position, screen.findPreference(mHighLightKey!!)
            ) else null
        }

        private fun dispatchOnResume(group: PreferenceGroup) {
            val count = group.preferenceCount
            for (i in 0 until count) {
                val preference = group.getPreference(i)
                if (preference is ResumablePreference) {
                    (preference as ResumablePreference).onResume()
                }
                if (preference is PreferenceGroup) {
                    dispatchOnResume(preference)
                }
            }
        }

        @SuppressLint("RestrictedApi")
        override fun onBindPreferences() {
            registerObserverIfNeeded()
        }

        @SuppressLint("RestrictedApi")
        override fun onUnbindPreferences() {
            unregisterObserverIfNeeded()
        }

        private fun registerObserverIfNeeded() {
            if (!mIsDataSetObserverRegistered) {
                mCurrentRootAdapter?.unregisterAdapterDataObserver(mDataSetObserver)
                mCurrentRootAdapter = listView.adapter
                mCurrentRootAdapter?.registerAdapterDataObserver(mDataSetObserver)
                mIsDataSetObserverRegistered = true
                onDataSetChanged()
            }
        }

        private fun unregisterObserverIfNeeded() {
            if (mIsDataSetObserverRegistered) {
                if (mCurrentRootAdapter != null) {
                    mCurrentRootAdapter?.unregisterAdapterDataObserver(mDataSetObserver)
                    mCurrentRootAdapter = null
                }
                mIsDataSetObserverRegistered = false
            }
        }

        fun onPreferencesAdded(group: PreferenceGroup) {
            var i = 0
            while (i < group.preferenceCount) {
                val preference = group.getPreference(i)
                if (preference is ControlledPreference) {
                    val controller: PreferenceController? = (preference as ControlledPreference)
                        .controller
                    if (controller?.onPreferenceAdded(preference) == false) {
                        i--
                        i++
                        continue
                    }
                }
                if (preference is PreferenceGroup) {
                    onPreferencesAdded(preference)
                }
                i++
            }
        }

        companion object {
            private const val SAVE_HIGHLIGHTED_KEY = "android:preference_highlighted"
        }
    }

    class LauncherSettingsFragment : BaseFragment() {
        private var mShowDevOptions = false

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            mShowDevOptions = Utilities.getOmegaPrefs(activity).developerOptionsEnabled
            preferenceManager.sharedPreferencesName = LauncherFiles.SHARED_PREFERENCES_KEY
            setHasOptionsMenu(true)
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.omega_preferences, rootKey)
            onPreferencesAdded(preferenceScreen);
        }

        override fun onResume() {
            super.onResume()
            requireActivity().setTitle(R.string.settings_button_text)
            activity?.titleColor = Utilities.getOmegaPrefs(activity).accentColor
            val dev = Utilities.getOmegaPrefs(activity).developerOptionsEnabled
            if (dev != mShowDevOptions) {
                activity?.recreate()
            }
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            return super.onPreferenceTreeClick(preference)
        }

        override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
            inflater.inflate(R.menu.menu_settings, menu)
            if (BuildConfig.APPLICATION_ID != defaultHome) {
                inflater.inflate(R.menu.menu_change_default_home, menu)
            }
        }

        override fun onOptionsItemSelected(item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.action_change_default_home -> changeDefaultHome(requireContext())
                R.id.action_restart_launcher -> Utilities.killLauncher()
                R.id.action_dev_options -> {
                    val intent = Intent(context, SettingsActivity::class.java)
                    intent.putExtra(
                        SubSettingsFragment.TITLE,
                        getString(R.string.developer_options_title)
                    )
                    intent.putExtra(
                        SubSettingsFragment.CONTENT_RES_ID,
                        R.xml.omega_preferences_developer
                    )
                    intent.putExtra(EXTRA_FROM_SETTINGS, true)
                    startActivity(intent)
                }
                else -> return false
            }
            return true
        }

        override val recyclerViewLayoutRes: Int
            get() = if (Utilities.getOmegaPrefs(context).settingsSearch)
                R.layout.preference_home_recyclerview
            else
                R.layout.preference_dialog_recyclerview
    }

    open class SubSettingsFragment : BaseFragment(), Preference.OnPreferenceChangeListener,
        Preference.OnPreferenceClickListener {
        private var mIconBadgingObserver: IconBadgingObserver? = null

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            preferenceManager.sharedPreferencesName = LauncherFiles.SHARED_PREFERENCES_KEY
            if (content == R.xml.omega_preferences_desktop) {
                if (!Utilities.ATLEAST_OREO) {
                    preferenceScreen.removePreference(
                        findPreference(SessionCommitReceiver.ADD_ICON_PREFERENCE_KEY)
                    )
                }
            } else if (content == R.xml.omega_preferences_notification) {
                val iconBadgingPref: NotificationDotsPreference = findPreference<Preference>(
                    NOTIFICATION_DOTS_PREFERENCE_KEY
                ) as NotificationDotsPreference
                // Listen to system notification badge settings while this UI is active.
                mIconBadgingObserver = IconBadgingObserver(
                    iconBadgingPref, requireContext().contentResolver, fragmentManager
                )
                mIconBadgingObserver?.register(
                    NOTIFICATION_BADGING,
                    NOTIFICATION_ENABLED_LISTENERS
                )
            } else if (content == R.xml.omega_preferences_developer) {
                findPreference<Preference>("kill")?.onPreferenceClickListener = this

                findPreference<Preference>("pref_desktop_mode_settings")?.setOnPreferenceClickListener {
                    Taskbar.openSettings(
                        requireContext(),
                        ThemeOverride.Settings().getTheme(requireContext())
                    )
                    true
                }
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(content, rootKey)
            onPreferencesAdded(preferenceScreen)
        }

        private val content: Int
            get() = arguments?.getInt(CONTENT_RES_ID) ?: -1

        override fun onResume() {
            super.onResume()
            setActivityTitle()
        }

        protected open fun setActivityTitle() {
            requireActivity().title = arguments?.getString(TITLE)
        }

        override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {

            return false
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            if (preference.key != null && preference.fragment != null) {
                Log.d("Settings", "Opening Fragment: " + preference.fragment)
                startFragment(requireContext(), preference.fragment, null, preference.title)
            }
            return false
        }

        override fun onPreferenceClick(preference: Preference): Boolean {
            if (preference.key == "kill") Utilities.killLauncher()
            return false
        }

        override fun onDisplayPreferenceDialog(preference: Preference) {
            val f: DialogFragment
            parentFragmentManager.let {
                f = if (preference is ThemePreference) {
                    ThemeListDialogFragment.newInstance(preference)
                } else {
                    super.onDisplayPreferenceDialog(preference)
                    return
                }
                f.setTargetFragment(this, 0)
                f.show(it, "android.support.v7.preference.PreferenceFragment.DIALOG")
            }
        }

        override val recyclerViewLayoutRes: Int
            get() = R.layout.preference_insettable_recyclerview

        companion object {
            const val TITLE = "title"
            const val CONTENT_RES_ID = "content_res_id"
            const val HAS_PREVIEW = "has_preview"
            fun newInstance(title: String?, @XmlRes content: Int): SubSettingsFragment {
                val fragment = SubSettingsFragment()
                val b = Bundle(2)
                b.putString(TITLE, title)
                b.putInt(CONTENT_RES_ID, content)
                fragment.arguments = b
                return fragment
            }

            fun newInstance(preference: SubPreference): SubSettingsFragment {
                val fragment = SubSettingsFragment()
                val b = Bundle(2)
                b.putString(TITLE, preference.title as String)
                b.putInt(CONTENT_RES_ID, preference.content)
                fragment.arguments = b
                return fragment
            }

            fun newInstance(intent: Intent): SubSettingsFragment {
                val fragment = SubSettingsFragment()
                val b = Bundle(2)
                b.putString(TITLE, intent.getStringExtra(TITLE))
                b.putInt(CONTENT_RES_ID, intent.getIntExtra(CONTENT_RES_ID, 0))
                fragment.arguments = b
                return fragment
            }
        }
    }

    companion object {
        const val NOTIFICATION_BADGING = "notification_badging"
        private const val NOTIFICATION_DOTS_PREFERENCE_KEY = "pref_icon_badging"

        /**
         * Hidden field Settings.Secure.ENABLED_NOTIFICATION_LISTENERS
         */
        private const val NOTIFICATION_ENABLED_LISTENERS = "enabled_notification_listeners"
        const val ENABLE_MINUS_ONE_PREF = "pref_enable_minus_one"
        const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
        const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"
        private const val DELAY_HIGHLIGHT_DURATION_MILLIS = 600
        const val EXTRA_TITLE = "title"
        const val EXTRA_FRAGMENT = "fragment"
        const val EXTRA_FRAGMENT_ARGS = "fragmentArgs"
        var defaultHome: String? = ""

        @JvmOverloads
        fun startFragment(
            context: Context,
            fragment: String?,
            args: Bundle?,
            title: CharSequence? = null
        ) {
            context.startActivity(createFragmentIntent(context, fragment, args, title))
        }

        fun startFragment(context: Context, fragment: String?, title: Int) {
            startFragment(context, fragment, null, context.getString(title))
        }

        private fun createFragmentIntent(
            context: Context,
            fragment: String?,
            args: Bundle?,
            title: CharSequence?
        ): Intent {
            val intent = Intent(context, SettingsActivity::class.java)
            intent.putExtra(EXTRA_FRAGMENT, fragment)
            intent.putExtra(EXTRA_FRAGMENT_ARGS, args)
            if (title != null) {
                intent.putExtra(EXTRA_TITLE, title)
            }
            return intent
        }
    }

    class DialogSettingsFragment : SubSettingsFragment() {
        override fun setActivityTitle() {}
        override val recyclerViewLayoutRes: Int
            get() = R.layout.preference_dialog_recyclerview

        companion object {
            fun newInstance(title: String?, @XmlRes content: Int): DialogSettingsFragment {
                val fragment = DialogSettingsFragment()
                val b = Bundle(2)
                b.putString(TITLE, title)
                b.putInt(CONTENT_RES_ID, content)
                fragment.arguments = b
                return fragment
            }
        }
    }

    class NotificationAccessConfirmation : DialogFragment(), DialogInterface.OnClickListener {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val context: Context? = activity
            val msg = context!!.getString(
                R.string.msg_missing_notification_access,
                context.getString(R.string.derived_app_name)
            )
            return AlertDialog.Builder(context)
                .setTitle(R.string.title_missing_notification_access)
                .setMessage(msg)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.title_change_settings, this)
                .create()
        }

        override fun onClick(dialogInterface: DialogInterface, i: Int) {
            val cn = ComponentName(requireActivity(), NotificationListener::class.java)
            val intent: Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(":settings:fragment_args_key", cn.flattenToString())
            requireActivity().startActivity(intent)
        }
    }

    /**
     * Content observer which listens for system badging setting changes, and updates the launcher
     * badging setting subtext accordingly.
     */
    private class IconBadgingObserver(
        val badgingPref: NotificationDotsPreference, val resolver: ContentResolver,
        val fragmentManager: FragmentManager?
    ) : SettingsObserver.Secure(resolver), Preference.OnPreferenceClickListener {
        private var serviceEnabled = true
        override fun onSettingChanged(keySettingEnabled: Boolean) {
            var summary =
                if (keySettingEnabled) R.string.notification_dots_desc_on else R.string.notification_dots_desc_off
            if (keySettingEnabled) {
                // Check if the listener is enabled or not.
                val enabledListeners =
                    Settings.Secure.getString(resolver, NOTIFICATION_ENABLED_LISTENERS)
                val myListener =
                    ComponentName(badgingPref.context, NotificationListener::class.java)
                serviceEnabled = enabledListeners != null &&
                        (enabledListeners.contains(myListener.flattenToString()) ||
                                enabledListeners.contains(myListener.flattenToShortString()))
                if (!serviceEnabled) {
                    summary = R.string.title_missing_notification_access
                }
            }
            badgingPref.setWidgetFrameVisible(!serviceEnabled)
            badgingPref.onPreferenceClickListener =
                if (serviceEnabled && Utilities.ATLEAST_OREO) null else this
            badgingPref.setSummary(summary)
        }

        override fun onPreferenceClick(preference: Preference): Boolean {
            if (!Utilities.ATLEAST_OREO && serviceEnabled) {
                val cn = ComponentName(
                    preference.context,
                    NotificationListener::class.java
                )
                val intent: Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(":settings:fragment_args_key", cn.flattenToString())
                preference.context.startActivity(intent)
            } else {
                fragmentManager?.let {
                    NotificationAccessConfirmation()
                        .show(it, "notification_access")
                }
            }
            return true
        }
    }
}