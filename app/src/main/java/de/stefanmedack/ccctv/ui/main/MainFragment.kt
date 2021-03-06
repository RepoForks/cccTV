package de.stefanmedack.ccctv.ui.main

import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v17.leanback.app.BrowseFragment
import android.support.v17.leanback.app.BrowseSupportFragment
import android.support.v17.leanback.widget.*
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.view.KeyEvent
import dagger.android.support.AndroidSupportInjection
import de.stefanmedack.ccctv.R
import de.stefanmedack.ccctv.ui.about.AboutFragment
import de.stefanmedack.ccctv.util.plusAssign
import info.metadude.kotlin.library.c3media.models.Conference
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.subscribeBy
import javax.inject.Inject

class MainFragment : BrowseSupportFragment() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var viewModel: MainViewModel

    private val disposables = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidSupportInjection.inject(this)
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProviders.of(activity, viewModelFactory).get(MainViewModel::class.java)

        setupUi()
        bindViewModel()
    }

    override fun onDestroy() {
        disposables.clear()
        super.onDestroy()
    }

    private fun setupUi() {
        prepareEntranceTransition()

        headersState = BrowseFragment.HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        badgeDrawable = ContextCompat.getDrawable(activity, R.drawable.voctocat)

        // TODO add back search
        //        setOnSearchClickedListener {
        //            Toast.makeText(
        //                    activity, "implement Search", Toast.LENGTH_SHORT)
        //                    .show()
        //        }

        mainFragmentRegistry.registerFragment(PageRow::class.java, PageRowFragmentFactory())
    }

    private fun bindViewModel() {
        disposables.add(viewModel.getConferences()
                .subscribeBy(
                        onSuccess = { render(it) },
                        // TODO proper error handling
                        onError = { it.printStackTrace() }
                ))
    }

    private fun render(mappedConferences: Map<String, List<Conference>>) {
        adapter = ArrayObjectAdapter(ListRowPresenter())
        (adapter as ArrayObjectAdapter).let {
            it += SectionRow(HeaderItem(1L, getString(R.string.main_videos_header)))
            it += mappedConferences.map { PageRow(HeaderItem(2L, it.key)) }
            it += DividerRow()
            it += SectionRow(HeaderItem(3L, getString(R.string.main_more_header)))
            it += PageRow(HeaderItem(4L, getString(R.string.main_about_app)))
        }

        startEntranceTransition()
    }

    fun onKeyDown(keyCode: Int): Boolean =
            // enable the main menu animation when clicking KEYCODE_DPAD_LEFT or KEYCODE_DPAD_UP(when scroll position is top) on the about page
            shouldShowHeadersForKeyCode(keyCode)
                    .also {
                        if (it) startHeadersTransition(true)
                    }

    private fun shouldShowHeadersForKeyCode(keyCode: Int): Boolean {
        return selectedPosition == adapter.size() - 1 // is last main menu item selected (AboutFragment)
                && !isShowingHeaders // is left side bar not shown?
                // case 1) LEFT is pressed
                && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                // case 2) UP is pressed and the scroll position of AboutFragment is the initial one
                || (keyCode == KeyEvent.KEYCODE_DPAD_UP
                && (mainFragment as AboutFragment).shouldKeyUpEventTriggerBackAnimation))
    }

    private class PageRowFragmentFactory internal constructor() : BrowseSupportFragment.FragmentFactory<Fragment>() {
        override fun createFragment(rowObj: Any): Fragment {
            return when ((rowObj as Row).headerItem.id) {
                4L -> AboutFragment()
                else -> GroupedConferencesFragment.create(rowObj.headerItem.name)
            }
        }
    }
}
