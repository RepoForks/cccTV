package de.stefanmedack.ccctv.ui.detail

import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.ViewModelProviders
import android.net.Uri
import android.os.Bundle
import android.support.v17.leanback.app.DetailsSupportFragment
import android.support.v17.leanback.app.DetailsSupportFragmentBackgroundController
import android.support.v17.leanback.widget.*
import android.support.v4.content.ContextCompat
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.GlideDrawable
import com.bumptech.glide.request.animation.GlideAnimation
import com.bumptech.glide.request.target.SimpleTarget
import dagger.android.support.AndroidSupportInjection
import de.stefanmedack.ccctv.R
import de.stefanmedack.ccctv.ui.cards.EventCardPresenter
import de.stefanmedack.ccctv.ui.cards.SpeakerCardPresenter
import de.stefanmedack.ccctv.ui.detail.playback.ExoPlayerAdapter
import de.stefanmedack.ccctv.ui.detail.playback.VideoMediaPlayerGlue
import de.stefanmedack.ccctv.ui.detail.uiModels.DetailUiModel
import de.stefanmedack.ccctv.ui.detail.uiModels.SpeakerUiModel
import de.stefanmedack.ccctv.util.*
import info.metadude.kotlin.library.c3media.models.Event
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.subscribeBy
import timber.log.Timber
import javax.inject.Inject

class DetailFragment : DetailsSupportFragment() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var viewModel: DetailViewModel

    private val disposables = CompositeDisposable()

    private lateinit var detailsBackground: DetailsSupportFragmentBackgroundController
    private lateinit var detailsOverview: DetailsOverviewRow

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidSupportInjection.inject(this)
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProviders.of(this, viewModelFactory).get(DetailViewModel::class.java).apply {
            event = activity.intent.getParcelableExtra("Event")
        }

        setupUi()
        setupEventListeners()
        bindViewModel()
    }

    override fun onDestroy() {
        disposables.clear()
        super.onDestroy()
    }

    private fun setupUi() {
        detailsBackground = DetailsSupportFragmentBackgroundController(this)

        // detail overview row - presents the detail, description and actions
        val detailOverviewRowPresenter = FullWidthDetailsOverviewRowPresenter(DetailDescriptionPresenter())
        detailOverviewRowPresenter.actionsBackgroundColor = ContextCompat.getColor(activity, R.color.amber_800)

        // init Shared Element Transition
        detailOverviewRowPresenter.setListener(FullWidthDetailsOverviewSharedElementHelper().apply {
            setSharedElementEnterTransition(activity, SHARED_DETAIL_TRANSITION)
        })
        detailOverviewRowPresenter.isParticipatingEntranceTransition = true

        // Setup action and detail row.
        detailsOverview = DetailsOverviewRow(viewModel.event)
        showPoster(detailsOverview)

        detailsOverview.actionsAdapter = ArrayObjectAdapter().apply {
            add(Action(DETAIL_ACTION_PLAY, getString(R.string.action_watch)))
            //            add(Action(DETAIL_ACTION_BOOKMARK, getString(R.string.action_bookmark))) TODO add back bookmarking when db is added
        }

        adapter = ArrayObjectAdapter(
                // Setup PresenterSelector to distinguish between the different rows.
                ClassPresenterSelector().apply {
                    addClassPresenter(DetailsOverviewRow::class.java, detailOverviewRowPresenter)
                    // Setup ListRow Presenter without shadows, to hide highlighting boxes for SpeakerCards
                    addClassPresenter(ListRow::class.java, ListRowPresenter().apply {
                        shadowEnabled = false
                    })
                }
        ).apply {
            add(detailsOverview)
        }
    }

    private fun showPoster(detailsOverview: DetailsOverviewRow) {
        detailsOverview.imageDrawable = ContextCompat.getDrawable(activity, R.drawable.voctocat)

        Glide.with(activity)
                .load(viewModel.event.thumbUrl)
                .centerCrop()
                .error(R.drawable.voctocat)
                .into<SimpleTarget<GlideDrawable>>(object : SimpleTarget<GlideDrawable>(
                        resources.getDimensionPixelSize(R.dimen.event_card_width),
                        resources.getDimensionPixelSize(R.dimen.event_card_height)) {
                    override fun onResourceReady(resource: GlideDrawable,
                                                 glideAnimation: GlideAnimation<in GlideDrawable>) {
                        detailsOverview.imageDrawable = resource
                    }
                })
    }

    private fun bindViewModel() {
        disposables.add(viewModel.getEventDetail()
                .subscribeBy(
                        onSuccess = { render(it) },
                        // TODO proper error handling
                        onError = { it.printStackTrace() }
                ))
    }

    private fun render(result: DetailUiModel) {
        val playerAdapter = ExoPlayerAdapter(activity)
        val mediaPlayerGlue = VideoMediaPlayerGlue(activity, playerAdapter)
        mediaPlayerGlue.isSeekEnabled = true
        mediaPlayerGlue.title = result.event.title
        mediaPlayerGlue.subtitle = result.event.subtitle
        result.event.bestRecording()?.let {
            Timber.d("PLAYABLE_VIDEO_URL=$it")
            // TODO show ErrorFragment if url is null
            mediaPlayerGlue.playerAdapter.setDataSource(Uri.parse(it.recordingUrl), it.width, it.height)
        }

        detailsBackground.enableParallax()
        detailsBackground.setupVideoPlayback(mediaPlayerGlue)

        (adapter as ArrayObjectAdapter).apply {
            // add speaker
            if (!result.speaker.isEmpty()) {
                val listRowAdapter = ArrayObjectAdapter(SpeakerCardPresenter())
                listRowAdapter += result.speaker
                add(ListRow(HeaderItem(0, getString(R.string.header_speaker)), listRowAdapter))

                // add go-to-speaker-section-button to DetailOverviewRow on the very top
                (detailsOverview.actionsAdapter as ArrayObjectAdapter).add(Action(DETAIL_ACTION_SPEAKER, getString(R.string.action_show_speaker)))

            }
            // add related
            if (!result.related.isEmpty()) {
                val listRowAdapter = ArrayObjectAdapter(EventCardPresenter())
                listRowAdapter += result.related
                add(ListRow(HeaderItem(1, getString(R.string.header_related)), listRowAdapter))

                // add go-to-related-section-button to DetailOverviewRow on the very top
                (detailsOverview.actionsAdapter as ArrayObjectAdapter).add(Action(DETAIL_ACTION_RELATED, getString(R.string.action_show_related)))
            }
        }
    }

    private fun setupEventListeners() {
        onItemViewClickedListener = OnItemViewClickedListener { itemViewHolder, item, _, _ ->
            when (item) {
                is Action -> when (item.id) {
                    DETAIL_ACTION_PLAY -> detailsBackground.switchToVideo()
                    DETAIL_ACTION_SPEAKER -> setSelectedPosition(1)
                    DETAIL_ACTION_RELATED -> setSelectedPosition(2)
                    else -> Toast.makeText(activity, R.string.implement_me_toast, Toast.LENGTH_LONG).show()
                }
                is SpeakerUiModel -> {
                    Toast.makeText(activity, R.string.implement_me_toast, Toast.LENGTH_LONG).show()
                }
                is Event -> {
                    DetailActivity.start(activity, item, (itemViewHolder.view as ImageCardView).mainImageView)
                }
            }
        }
    }

    fun onKeyDown(keyCode: Int): Boolean =
            (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && shouldShowDetailsOnKeyDownEvent())
                    .also {
                        if (it) detailsBackground.switchToRows()
                    }

    // Note: this is a workaround to find the current focus on the video playback screen
    // -> we use this information to switch back to information on KEYCODE_DPAD_DOWN press
    private fun shouldShowDetailsOnKeyDownEvent(): Boolean {
        val playbackFragmentView = detailsBackground.findOrCreateVideoSupportFragment().view
        val progressBarView = playbackFragmentView?.findViewById<View>(R.id.playback_progress)
        return (playbackFragmentView?.hasFocus() == true && progressBarView?.hasFocus() == true)
    }

}
