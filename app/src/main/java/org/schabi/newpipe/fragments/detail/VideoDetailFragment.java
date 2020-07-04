package org.schabi.newpipe.fragments.detail;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.database.ContentObserver;
import android.net.Uri;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.tabs.TabLayout;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.ViewPager;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.util.Linkify;
import android.util.DisplayMetrics;
import android.util.Log;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.tabs.TabLayout;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;

import org.schabi.newpipe.R;
import org.schabi.newpipe.ReCaptchaActivity;
import org.schabi.newpipe.download.DownloadDialog;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.fragments.BackPressable;
import org.schabi.newpipe.fragments.BaseStateFragment;
import org.schabi.newpipe.fragments.EmptyFragment;
import org.schabi.newpipe.fragments.list.comments.CommentsFragment;
import org.schabi.newpipe.fragments.list.videos.RelatedVideosFragment;
import org.schabi.newpipe.local.dialog.PlaylistAppendDialog;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.player.*;
import org.schabi.newpipe.player.event.PlayerEventListener;
import org.schabi.newpipe.player.event.PlayerServiceEventListener;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.playqueue.*;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.*;
import org.schabi.newpipe.views.AnimatedProgressBar;
import org.schabi.newpipe.views.LargeTextMovementMethod;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import icepick.State;
import io.noties.markwon.Markwon;
import io.noties.markwon.linkify.LinkifyPlugin;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import static org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability.COMMENTS;
import static org.schabi.newpipe.player.helper.PlayerHelper.isClearingQueueConfirmationRequired;
import static org.schabi.newpipe.player.playqueue.PlayQueueItem.RECOVERY_UNSET;
import static org.schabi.newpipe.extractor.stream.StreamExtractor.NO_AGE_LIMIT;
import static org.schabi.newpipe.util.AnimationUtils.animateView;

public class VideoDetailFragment
        extends BaseStateFragment<StreamInfo>
        implements BackPressable,
        SharedPreferences.OnSharedPreferenceChangeListener,
        View.OnClickListener,
        View.OnLongClickListener,
        PlayerEventListener,
        PlayerServiceEventListener {
    public static final String AUTO_PLAY = "auto_play";

    private static final int RELATED_STREAMS_UPDATE_FLAG = 0x1;
    private static final int COMMENTS_UPDATE_FLAG = 0x2;
    private static final float MAX_OVERLAY_ALPHA = 0.9f;
    private static final float MAX_PLAYER_HEIGHT = 0.7f;

    public static final String ACTION_SHOW_MAIN_PLAYER = "org.schabi.newpipe.VideoDetailFragment.ACTION_SHOW_MAIN_PLAYER";
    public static final String ACTION_HIDE_MAIN_PLAYER = "org.schabi.newpipe.VideoDetailFragment.ACTION_HIDE_MAIN_PLAYER";
    public static final String ACTION_VIDEO_FRAGMENT_RESUMED = "org.schabi.newpipe.VideoDetailFragment.ACTION_VIDEO_FRAGMENT_RESUMED";
    public static final String ACTION_VIDEO_FRAGMENT_STOPPED = "org.schabi.newpipe.VideoDetailFragment.ACTION_VIDEO_FRAGMENT_STOPPED";

    private static final String COMMENTS_TAB_TAG = "COMMENTS";
    private static final String RELATED_TAB_TAG = "NEXT VIDEO";
    private static final String EMPTY_TAB_TAG = "EMPTY TAB";

    private boolean showRelatedStreams;
    private boolean showComments;
    private String selectedTabTag;

    private int updateFlags = 0;

    @State
    protected int serviceId = Constants.NO_SERVICE_ID;
    @State
    protected String name;
    @State
    protected String url;
    @State
    protected PlayQueue playQueue;
    @State
    int bottomSheetState = BottomSheetBehavior.STATE_EXPANDED;
    @State
    protected boolean autoPlayEnabled = true;

    private StreamInfo currentInfo;
    private Disposable currentWorker;
    @NonNull
    private CompositeDisposable disposables = new CompositeDisposable();
    @Nullable
    private Disposable positionSubscriber = null;

    private List<VideoStream> sortedVideoStreams;
    private int selectedVideoStreamIndex = -1;
    private BottomSheetBehavior bottomSheetBehavior;
    private BroadcastReceiver broadcastReceiver;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private LinearLayout contentRootLayoutHiding;

    private View thumbnailBackgroundButton;
    private ImageView thumbnailImageView;
    private ImageView thumbnailPlayButton;
    private AnimatedProgressBar positionView;

    private View videoTitleRoot;
    private TextView videoTitleTextView;
    private ImageView videoTitleToggleArrow;
    private TextView videoCountView;

    private TextView detailControlsBackground;
    private TextView detailControlsPopup;
    private TextView detailControlsAddToPlaylist;
    private TextView detailControlsDownload;
    private TextView appendControlsDetail;
    private TextView detailDurationView;
    private TextView detailPositionView;

    private LinearLayout videoDescriptionRootLayout;
    private TextView videoUploadDateView;
    private TextView videoDescriptionView;

    private View uploaderRootLayout;
    private TextView uploaderTextView;
    private ImageView uploaderThumb;
    private TextView subChannelTextView;
    private ImageView subChannelThumb;

    private TextView thumbsUpTextView;
    private ImageView thumbsUpImageView;
    private TextView thumbsDownTextView;
    private ImageView thumbsDownImageView;
    private TextView thumbsDisabledTextView;

    private RelativeLayout overlay;
    private LinearLayout overlayMetadata;
    private ImageView overlayThumbnailImageView;
    private TextView overlayTitleTextView;
    private TextView overlayChannelTextView;
    private LinearLayout overlayButtons;
    private ImageButton overlayPlayPauseButton;
    private ImageButton overlayCloseButton;

    private AppBarLayout appBarLayout;
    private ViewPager viewPager;
    private TabAdaptor pageAdapter;
    private TabLayout tabLayout;
    private FrameLayout relatedStreamsLayout;

    private ContentObserver settingsContentObserver;
    private ServiceConnection serviceConnection;
    private boolean bound;
    private MainPlayer playerService;
    private VideoPlayerImpl player;


    /*//////////////////////////////////////////////////////////////////////////
    // Service management
    //////////////////////////////////////////////////////////////////////////*/

    private ServiceConnection getServiceConnection(boolean playAfterConnect) {
        return new ServiceConnection() {
            @Override
            public void onServiceDisconnected(ComponentName name) {
                if (DEBUG) Log.d(TAG, "Player service is disconnected");

                unbind();
            }

            @Override
            public void onServiceConnected(ComponentName compName, IBinder service) {
                if (DEBUG) Log.d(TAG, "Player service is connected");
                MainPlayer.LocalBinder localBinder = (MainPlayer.LocalBinder) service;

                playerService = localBinder.getService();
                player = localBinder.getPlayer();

                startPlayerListener();

                // It will do nothing if the player is not in fullscreen mode
                hideSystemUiIfNeeded();

                if (!player.videoPlayerSelected() && !playAfterConnect) return;

                if (playerIsNotStopped() && player.videoPlayerSelected()) addVideoPlayerView();

                if (isLandscape()) {
                    // If the video is playing but orientation changed let's make the video in fullscreen again
                    checkLandscape();
                } else if (player.isFullscreen()) {
                    // Device is in portrait orientation after rotation but UI is in fullscreen.
                    // Return back to non-fullscreen state
                    player.toggleFullscreen();
                }

                if (playAfterConnect || (currentInfo != null && isAutoplayEnabled() && player.getParentActivity() == null)) {
                    openVideoPlayer();
                }
            }
        };
    }

    private void bind() {
        if (DEBUG) Log.d(TAG, "bind() called");

        Intent serviceIntent = new Intent(getContext(), MainPlayer.class);
        final boolean success = getContext().bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        if (!success) getContext().unbindService(serviceConnection);
        bound = success;
    }

    private void unbind() {
        if (DEBUG) Log.d(TAG, "unbind() called");

        if (bound) {
            getContext().unbindService(serviceConnection);
            bound = false;
            stopPlayerListener();
            playerService = null;
            player = null;
        }
    }

    private void startPlayerListener() {
        if (player != null) player.setFragmentListener(this);
    }

    private void stopPlayerListener() {
        if (player != null) player.removeFragmentListener(this);
    }

    private void startService(boolean playAfterConnect) {
        // startService() can be called concurrently and it will give a random crashes and NullPointerExceptions
        // inside the service because the service will be bound twice. Prevent it with unbinding first
        unbind();
        getContext().startService(new Intent(getContext(), MainPlayer.class));
        serviceConnection = getServiceConnection(playAfterConnect);
        bind();
    }

    private void stopService() {
        unbind();
        getContext().stopService(new Intent(getContext(), MainPlayer.class));
    }


    /*////////////////////////////////////////////////////////////////////////*/

    private static final String COMMENTS_TAB_TAG = "COMMENTS";
    private static final String RELATED_TAB_TAG = "NEXT VIDEO";
    private static final String EMPTY_TAB_TAG = "EMPTY TAB";

    private static final String INFO_KEY = "info_key";
    private static final String STACK_KEY = "stack_key";

    /**
     * Stack that contains the "navigation history".<br>
     * The peek is the current video.
     */
    private final LinkedList<StackItem> stack = new LinkedList<>();

    public static VideoDetailFragment getInstance(final int serviceId, final String videoUrl,
                                                  final String name) {
        VideoDetailFragment instance = new VideoDetailFragment();
        instance.setInitialData(serviceId, videoUrl, name, playQueue);
        return instance;
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's Lifecycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        showRelatedStreams = PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(getString(R.string.show_next_video_key), true);

        showComments = PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(getString(R.string.show_comments_key), true);

        selectedTabTag = PreferenceManager.getDefaultSharedPreferences(activity)
                .getString(getString(R.string.stream_info_selected_tab_key), COMMENTS_TAB_TAG);

        PreferenceManager.getDefaultSharedPreferences(activity)
                .registerOnSharedPreferenceChangeListener(this);

        setupBroadcastReceiver();

        settingsContentObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                if(activity != null && !PlayerHelper.globalScreenOrientationLocked(activity))
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            }
        };
        activity.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false,
                settingsContentObserver);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_video_detail, container, false);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (currentWorker != null) currentWorker.dispose();

        setupBrightness(true);
        PreferenceManager.getDefaultSharedPreferences(getContext())
                .edit()
                .putString(getString(R.string.stream_info_selected_tab_key),
                        pageAdapter.getItemTitle(viewPager.getCurrentItem()))
                .apply();
    }

    @Override
    public void onResume() {
        super.onResume();

        activity.sendBroadcast(new Intent(ACTION_VIDEO_FRAGMENT_RESUMED));

        setupBrightness(false);

        if (updateFlags != 0) {
            if (!isLoading.get() && currentInfo != null) {
                if ((updateFlags & RELATED_STREAMS_UPDATE_FLAG) != 0) startLoading(false);
                if ((updateFlags & COMMENTS_UPDATE_FLAG) != 0) startLoading(false);
            }

            updateFlags = 0;
        }

        // Check if it was loading when the fragment was stopped/paused
        if (wasLoading.getAndSet(false) && !wasCleared())
            startLoading(false);
    }

    @Override
    public void onStop() {
        super.onStop();

        if (!activity.isChangingConfigurations())
            activity.sendBroadcast(new Intent(ACTION_VIDEO_FRAGMENT_STOPPED));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Stop the service when user leaves the app with double back press if video player is selected. Otherwise unbind
        if (activity.isFinishing() && player != null && player.videoPlayerSelected()) stopService();
        else unbind();

        PreferenceManager.getDefaultSharedPreferences(activity)
                .unregisterOnSharedPreferenceChangeListener(this);
        activity.unregisterReceiver(broadcastReceiver);
        activity.getContentResolver().unregisterContentObserver(settingsContentObserver);

        if (positionSubscriber != null) {
            positionSubscriber.dispose();
        }
        if (currentWorker != null) {
            currentWorker.dispose();
        }
        if (disposables != null) {
            disposables.clear();
        }
        positionSubscriber = null;
        currentWorker = null;
        disposables = null;
        bottomSheetBehavior.setBottomSheetCallback(null);
    }

    @Override
    public void onDestroyView() {
        if (DEBUG) {
            Log.d(TAG, "onDestroyView() called");
        }
        spinnerToolbar.setOnItemSelectedListener(null);
        spinnerToolbar.setAdapter(null);
        super.onDestroyView();
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case ReCaptchaActivity.RECAPTCHA_REQUEST:
                if (resultCode == Activity.RESULT_OK) {
                    NavigationHelper
                            .openVideoDetailFragment(getFragmentManager(), serviceId, url, name);
                } else {
                    Log.e(TAG, "ReCaptcha failed");
                }
                break;
            default:
                Log.e(TAG, "Request code from activity not supported [" + requestCode + "]");
                break;
        }
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences,
                                          final String key) {
        if (key.equals(getString(R.string.show_next_video_key))) {
            showRelatedStreams = sharedPreferences.getBoolean(key, true);
            updateFlags |= RELATED_STREAMS_UPDATE_FLAG;
        } else if (key.equals(getString(R.string.show_comments_key))) {
            showComments = sharedPreferences.getBoolean(key, true);
            updateFlags |= COMMENTS_UPDATE_FLAG;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // State Saving
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);

        // Check if the next video label and video is visible,
        // if it is, include the two elements in the next check
        int nextCount = currentInfo != null && currentInfo.getNextVideo() != null ? 2 : 0;

        if (!isLoading.get() && currentInfo != null && isVisible()) {
            outState.putSerializable(INFO_KEY, currentInfo);
        }

        if (playQueue != null) outState.putSerializable(VideoPlayer.PLAY_QUEUE_KEY, playQueue);
        outState.putSerializable(STACK_KEY, stack);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull final Bundle savedState) {
        super.onRestoreInstanceState(savedState);

        Serializable serializable = savedState.getSerializable(INFO_KEY);
        if (serializable instanceof StreamInfo) {
            //noinspection unchecked
            currentInfo = (StreamInfo) serializable;
            InfoCache.getInstance().putInfo(serviceId, url, currentInfo, InfoItem.InfoType.STREAM);
        }

        serializable = savedState.getSerializable(STACK_KEY);
        if (serializable instanceof Collection) {
            //noinspection unchecked
            stack.addAll((Collection<? extends StackItem>) serializable);
        }
        playQueue = (PlayQueue) savedState.getSerializable(VideoPlayer.PLAY_QUEUE_KEY);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // OnClick
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onClick(final View v) {
        if (isLoading.get() || currentInfo == null) {
            return;
        }

        switch (v.getId()) {
            case R.id.detail_controls_background:
                openBackgroundPlayer(false);
                break;
            case R.id.detail_controls_popup:
                openPopupPlayer(false);
                break;
            case R.id.detail_controls_playlist_append:
                if (getFragmentManager() != null && currentInfo != null) {
                    PlaylistAppendDialog.fromStreamInfo(currentInfo)
                            .show(getFragmentManager(), TAG);
                }
                break;
            case R.id.detail_controls_download:
                if (PermissionHelper.checkStoragePermissions(activity,
                        PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE)) {
                    this.openDownloadDialog();
                }
                break;
            case R.id.detail_uploader_root_layout:
                if (TextUtils.isEmpty(currentInfo.getSubChannelUrl())) {
                    if (!TextUtils.isEmpty(currentInfo.getUploaderUrl())) {
                        openChannel(currentInfo.getUploaderUrl(), currentInfo.getUploaderName());
                    }

                    if (DEBUG) {
                        Log.i(TAG, "Can't open sub-channel because we got no channel URL");
                    }
                } else {
                    openChannel(currentInfo.getSubChannelUrl(),
                            currentInfo.getSubChannelName());
                }
                break;
            case R.id.detail_thumbnail_root_layout:
                openVideoPlayer();
                break;
            case R.id.detail_title_root_layout:
                toggleTitleAndDescription();
                break;
            case R.id.overlay_thumbnail:
            case R.id.overlay_metadata_layout:
            case R.id.overlay_buttons_layout:
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                break;
            case R.id.overlay_play_pause_button:
                if (playerIsNotStopped()) {
                    player.onPlayPause();
                    player.hideControls(0,0);
                    showSystemUi();
                }
                else openVideoPlayer();

                setOverlayPlayPauseImage();
                break;
            case R.id.overlay_close_button:
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                break;
        }
    }

    private void openChannel(final String subChannelUrl, final String subChannelName) {
        try {
            NavigationHelper.openChannelFragment(
                    getFragmentManager(),
                    currentInfo.getServiceId(),
                    subChannelUrl,
                    subChannelName);
        } catch (Exception e) {
            ErrorActivity.reportUiError((AppCompatActivity) getActivity(), e);
        }
    }

    @Override
    public boolean onLongClick(final View v) {
        if (isLoading.get() || currentInfo == null) {
            return false;
        }

        switch (v.getId()) {
            case R.id.detail_controls_background:
                openBackgroundPlayer(true);
                break;
            case R.id.detail_controls_popup:
                openPopupPlayer(true);
                break;
            case R.id.detail_controls_download:
                NavigationHelper.openDownloads(activity);
                break;
            case R.id.detail_uploader_root_layout:
                if (TextUtils.isEmpty(currentInfo.getSubChannelUrl())) {
                    Log.w(TAG,
                            "Can't open parent channel because we got no parent channel URL");
                } else {
                    openChannel(currentInfo.getUploaderUrl(), currentInfo.getUploaderName());
                }
                break;
            case R.id.detail_title_root_layout:
                ShareUtils.copyToClipboard(getContext(), videoTitleTextView.getText().toString());
                break;
            case R.id.overlay_thumbnail:
            case R.id.overlay_metadata_layout:
                openChannel();
                break;
        }

        return true;
    }

    private void toggleTitleAndDescription() {
        if (videoDescriptionRootLayout.getVisibility() == View.VISIBLE) {
            videoTitleTextView.setMaxLines(1);
            videoDescriptionRootLayout.setVisibility(View.GONE);
            videoDescriptionView.setFocusable(false);
            videoTitleToggleArrow.setImageResource(
                    ThemeHelper.resolveResourceIdFromAttr(requireContext(), R.attr.ic_expand_more));
        } else {
            videoTitleTextView.setMaxLines(10);
            videoDescriptionRootLayout.setVisibility(View.VISIBLE);
            videoDescriptionView.setFocusable(true);
            videoDescriptionView.setMovementMethod(new LargeTextMovementMethod());
            videoTitleToggleArrow.setImageResource(
                    ThemeHelper.resolveResourceIdFromAttr(requireContext(), R.attr.ic_expand_less));
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        thumbnailBackgroundButton = rootView.findViewById(R.id.detail_thumbnail_root_layout);
        thumbnailImageView = rootView.findViewById(R.id.detail_thumbnail_image_view);
        thumbnailPlayButton = rootView.findViewById(R.id.detail_thumbnail_play_button);

        contentRootLayoutHiding = rootView.findViewById(R.id.detail_content_root_hiding);

        videoTitleRoot = rootView.findViewById(R.id.detail_title_root_layout);
        videoTitleTextView = rootView.findViewById(R.id.detail_video_title_view);
        videoTitleToggleArrow = rootView.findViewById(R.id.detail_toggle_description_view);
        videoCountView = rootView.findViewById(R.id.detail_view_count_view);
        positionView = rootView.findViewById(R.id.position_view);

        detailControlsBackground = rootView.findViewById(R.id.detail_controls_background);
        detailControlsPopup = rootView.findViewById(R.id.detail_controls_popup);
        detailControlsAddToPlaylist = rootView.findViewById(R.id.detail_controls_playlist_append);
        detailControlsDownload = rootView.findViewById(R.id.detail_controls_download);
        appendControlsDetail = rootView.findViewById(R.id.touch_append_detail);
        detailDurationView = rootView.findViewById(R.id.detail_duration_view);
        detailPositionView = rootView.findViewById(R.id.detail_position_view);

        videoDescriptionRootLayout = rootView.findViewById(R.id.detail_description_root_layout);
        videoUploadDateView = rootView.findViewById(R.id.detail_upload_date_view);
        videoDescriptionView = rootView.findViewById(R.id.detail_description_view);

        thumbsUpTextView = rootView.findViewById(R.id.detail_thumbs_up_count_view);
        thumbsUpImageView = rootView.findViewById(R.id.detail_thumbs_up_img_view);
        thumbsDownTextView = rootView.findViewById(R.id.detail_thumbs_down_count_view);
        thumbsDownImageView = rootView.findViewById(R.id.detail_thumbs_down_img_view);
        thumbsDisabledTextView = rootView.findViewById(R.id.detail_thumbs_disabled_view);

        uploaderRootLayout = rootView.findViewById(R.id.detail_uploader_root_layout);
        uploaderTextView = rootView.findViewById(R.id.detail_uploader_text_view);
        uploaderThumb = rootView.findViewById(R.id.detail_uploader_thumbnail_view);
        subChannelTextView = rootView.findViewById(R.id.detail_sub_channel_text_view);
        subChannelThumb = rootView.findViewById(R.id.detail_sub_channel_thumbnail_view);

        overlay = rootView.findViewById(R.id.overlay_layout);
        overlayMetadata = rootView.findViewById(R.id.overlay_metadata_layout);
        overlayThumbnailImageView = rootView.findViewById(R.id.overlay_thumbnail);
        overlayTitleTextView = rootView.findViewById(R.id.overlay_title_text_view);
        overlayChannelTextView = rootView.findViewById(R.id.overlay_channel_text_view);
        overlayButtons = rootView.findViewById(R.id.overlay_buttons_layout);
        overlayPlayPauseButton = rootView.findViewById(R.id.overlay_play_pause_button);
        overlayCloseButton = rootView.findViewById(R.id.overlay_close_button);

        appBarLayout = rootView.findViewById(R.id.appbarlayout);
        viewPager = rootView.findViewById(R.id.viewpager);
        pageAdapter = new TabAdaptor(getChildFragmentManager());
        viewPager.setAdapter(pageAdapter);
        tabLayout = rootView.findViewById(R.id.tablayout);
        tabLayout.setupWithViewPager(viewPager);

        relatedStreamsLayout = rootView.findViewById(R.id.relatedStreamsLayout);

        setHeightThumbnail();


    }

    @Override
    protected void initListeners() {
        super.initListeners();

        videoTitleRoot.setOnLongClickListener(this);
        uploaderRootLayout.setOnClickListener(this);
        uploaderRootLayout.setOnLongClickListener(this);
        videoTitleRoot.setOnClickListener(this);
        thumbnailBackgroundButton.setOnClickListener(this);
        detailControlsBackground.setOnClickListener(this);
        detailControlsPopup.setOnClickListener(this);
        detailControlsAddToPlaylist.setOnClickListener(this);
        detailControlsDownload.setOnClickListener(this);
        detailControlsDownload.setOnLongClickListener(this);

        detailControlsBackground.setLongClickable(true);
        detailControlsPopup.setLongClickable(true);
        detailControlsBackground.setOnLongClickListener(this);
        detailControlsPopup.setOnLongClickListener(this);

        overlayThumbnailImageView.setOnClickListener(this);
        overlayThumbnailImageView.setOnLongClickListener(this);
        overlayMetadata.setOnClickListener(this);
        overlayMetadata.setOnLongClickListener(this);
        overlayButtons.setOnClickListener(this);
        overlayCloseButton.setOnClickListener(this);
        overlayPlayPauseButton.setOnClickListener(this);

        detailControlsBackground.setOnTouchListener(getOnControlsTouchListener());
        detailControlsPopup.setOnTouchListener(getOnControlsTouchListener());

        setupBottomPlayer();
        startService(false);
    }

    private View.OnTouchListener getOnControlsTouchListener() {
        return (View view, MotionEvent motionEvent) -> {
            if (!PreferenceManager.getDefaultSharedPreferences(activity)
                    .getBoolean(getString(R.string.show_hold_to_append_key), true)) {
                return false;
            }

            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                animateView(appendControlsDetail, true, 250, 0, () ->
                        animateView(appendControlsDetail, false, 1500, 1000));
            }
            return false;
        };
    }

    private void initThumbnailViews(@NonNull final StreamInfo info) {
        thumbnailImageView.setImageResource(R.drawable.dummy_thumbnail_dark);

        if (!TextUtils.isEmpty(info.getThumbnailUrl())) {
            final String infoServiceName = NewPipe.getNameOfService(info.getServiceId());
            final ImageLoadingListener loadingListener = new SimpleImageLoadingListener() {
                @Override
                public void onLoadingFailed(final String imageUri, final View view,
                                            final FailReason failReason) {
                    showSnackBarError(failReason.getCause(), UserAction.LOAD_IMAGE,
                            infoServiceName, imageUri, R.string.could_not_load_thumbnails);
                }
            };

            IMAGE_LOADER.displayImage(info.getThumbnailUrl(), thumbnailImageView,
                    ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS, onFailListener);
        }

        if (!TextUtils.isEmpty(info.getSubChannelAvatarUrl())) {
            IMAGE_LOADER.displayImage(info.getSubChannelAvatarUrl(), subChannelThumb,
                    ImageDisplayConstants.DISPLAY_AVATAR_OPTIONS);
        }

        if (!TextUtils.isEmpty(info.getUploaderAvatarUrl())) {
            IMAGE_LOADER.displayImage(info.getUploaderAvatarUrl(), uploaderThumb,
                    ImageDisplayConstants.DISPLAY_AVATAR_OPTIONS);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        this.menu = menu;

        // CAUTION set item properties programmatically otherwise it would not be accepted by
        // appcompat itemsinflater.inflate(R.menu.videoitem_detail, menu);

        inflater.inflate(R.menu.video_detail_menu, menu);

        updateMenuItemVisibility();

        ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayHomeAsUpEnabled(true);
            supportActionBar.setDisplayShowTitleEnabled(false);
        }
    }

    private void updateMenuItemVisibility() {

        // show kodi if set in settings
        menu.findItem(R.id.action_play_with_kodi).setVisible(
                PreferenceManager.getDefaultSharedPreferences(activity).getBoolean(
                        activity.getString(R.string.show_play_with_kodi_key), false));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (isLoading.get()) {
            // if is still loading block menu
            return true;
        }

        int id = item.getItemId();
        switch (id) {
            case R.id.menu_item_share: {
                if (currentInfo != null) {
                    ShareUtils.shareUrl(this.getContext(), currentInfo.getName(), currentInfo.getOriginalUrl());
                }
                return true;
            }
            case R.id.menu_item_openInBrowser: {
                if (currentInfo != null) {
                    ShareUtils.openUrlInBrowser(this.getContext(), currentInfo.getOriginalUrl());
                }
                return true;
            }
            case R.id.action_play_with_kodi:
                try {
                    NavigationHelper.playWithKore(activity, Uri.parse(
                            url.replace("https", "http")));
                } catch (Exception e) {
                    if (DEBUG) Log.i(TAG, "Failed to start kore", e);
                    showInstallKoreDialog(activity);
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private static void showInstallKoreDialog(final Context context) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(R.string.kore_not_found)
                .setPositiveButton(R.string.install, (DialogInterface dialog, int which) ->
                        NavigationHelper.installKore(context))
                .setNegativeButton(R.string.cancel, (DialogInterface dialog, int which) -> {
                });
        builder.create().show();
    }

    private void setupActionBarOnError(final String url) {
        if (DEBUG) Log.d(TAG, "setupActionBarHandlerOnError() called with: url = [" + url + "]");
        Log.e("-----", "missing code");
    }

    private void setupActionBar(final StreamInfo info) {
        if (DEBUG) Log.d(TAG, "setupActionBarHandler() called with: info = [" + info + "]");
        boolean isExternalPlayerEnabled = PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(activity.getString(R.string.use_external_video_player_key), false);

        sortedVideoStreams = ListHelper.getSortedStreamVideosList(
                activity,
                info.getVideoStreams(),
                info.getVideoOnlyStreams(),
                false);
        selectedVideoStreamIndex = ListHelper.getDefaultResolutionIndex(activity, sortedVideoStreams);

        final StreamItemAdapter<VideoStream, Stream> streamsAdapter =
                new StreamItemAdapter<>(activity,
                        new StreamSizeWrapper<>(sortedVideoStreams, activity), isExternalPlayerEnabled);
        spinnerToolbar.setAdapter(streamsAdapter);
        spinnerToolbar.setSelection(selectedVideoStreamIndex);
        spinnerToolbar.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedVideoStreamIndex = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    /*//////////////////////////////////////////////////////////////////////////
    // OwnStack
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Stack that contains the "navigation history".<br>
     * The peek is the current video.
     */
    protected final LinkedList<StackItem> stack = new LinkedList<>();

    public void pushToStack(int serviceId, String videoUrl, String name) {
        if (DEBUG) {
            Log.d(TAG, "pushToStack() called with: serviceId = ["
                    + serviceId + "], videoUrl = [" + videoUrl + "], name = [" + name + "]");
        }

        if (stack.size() > 0
                && stack.peek().getServiceId() == serviceId
                && stack.peek().getUrl().equals(videoUrl)) {
            Log.d(TAG, "pushToStack() called with: serviceId == peek.serviceId = ["
                    + serviceId + "], videoUrl == peek.getUrl = [" + videoUrl + "]");
            return;
        } else {
            Log.d(TAG, "pushToStack() wasn't equal");
        }

        stack.push(new StackItem(serviceId, videoUrl, name));
    }

    public void setTitleToUrl(int serviceId, String videoUrl, String name) {
        if (name != null && !name.isEmpty()) {
            for (StackItem stackItem : stack) {
                if (stack.peek().getServiceId() == sid
                        && stackItem.getUrl().equals(videoUrl)) {
                    stackItem.setTitle(title);
                }
            }
        }
    }*/

    @Override
    public boolean onBackPressed() {
        if (DEBUG) Log.d(TAG, "onBackPressed() called");

        // If we are in fullscreen mode just exit from it via first back press
        if (player != null && player.isFullscreen()) {
            if (!PlayerHelper.isTablet(activity)) player.onPause();
            restoreDefaultOrientation();
            setAutoplay(false);
            return true;
        }

        // If we have something in history of played items we replay it here
        if (player != null
                && player.getPlayQueue() != null
                && player.videoPlayerSelected()
                && player.getPlayQueue().previous()) {
            return true;
        }
        // That means that we are on the start of the stack,
        // return false to let the MainActivity handle the onBack
        if (stack.size() <= 1) {
            restoreDefaultOrientation();

            return false;
        }
        // Remove top
        stack.pop();
        // Get stack item from the new top
        setupFromHistoryItem(stack.peek());

        selectAndLoadVideo(peek.getServiceId(), peek.getUrl(),
                !TextUtils.isEmpty(peek.getTitle()) ? peek.getTitle() : "");
        return true;
    }

    private void setupFromHistoryItem(StackItem item) {
        setAutoplay(false);
        hideMainPlayer();

        setInitialData(
                item.getServiceId(),
                item.getUrl(),
                !TextUtils.isEmpty(item.getTitle()) ? item.getTitle() : "",
                item.getPlayQueue());
        startLoading(false);

        // Maybe an item was deleted in background activity
        if (item.getPlayQueue().getItem() == null) return;

        PlayQueueItem playQueueItem = item.getPlayQueue().getItem();
        // Update title, url, uploader from the last item in the stack (it's current now)
        boolean isPlayerStopped = player == null || player.isPlayerStopped();
        if (playQueueItem != null && isPlayerStopped)
            updateOverlayData(playQueueItem.getTitle(), playQueueItem.getUploader(), playQueueItem.getThumbnailUrl());
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Info loading and handling
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void doInitialLoadLogic() {
        if (wasCleared()) return;

        if (currentInfo == null) prepareAndLoadInfo();
        else prepareAndHandleInfo(currentInfo, false);
    }

    public void selectAndLoadVideo(int serviceId, String videoUrl, String name, PlayQueue playQueue) {
        // Situation when user switches from players to main player. All needed data is here, we can start watching
        if (this.playQueue != null && this.playQueue.equals(playQueue)) {
            openVideoPlayer();
            return;
        }
        setInitialData(serviceId, videoUrl, name, playQueue);
        startLoading(false, true);
    }

    private void prepareAndHandleInfo(final StreamInfo info, final boolean scrollToTop) {
        if (DEBUG) {
            Log.d(TAG, "prepareAndHandleInfo() called with: "
                    + "info = [" + info + "], scrollToTop = [" + scrollToTop + "]");
        }

        showLoading();
        initTabs();

        if (scrollToTop) scrollToTop();
        handleResult(info);
        showContent();

    }

    private void prepareAndLoadInfo() {
        appBarLayout.setExpanded(true, true);
        pushToStack(serviceId, url, name);
        startLoading(false);
    }

    @Override
    public void startLoading(final boolean forceLoad) {
        super.startLoading(forceLoad);

        initTabs();
        currentInfo = null;
        if (currentWorker != null) {
            currentWorker.dispose();
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);

        runWorker(forceLoad, stack.isEmpty());
    }

    private void startLoading(boolean forceLoad, boolean addToBackStack) {
        super.startLoading(false);

        initTabs();
        currentInfo = null;
        if (currentWorker != null) currentWorker.dispose();

        runWorker(forceLoad, addToBackStack);
    }

    private void runWorker(boolean forceLoad, boolean addToBackStack) {
        currentWorker = ExtractorHelper.getStreamInfo(serviceId, url, forceLoad)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((@NonNull final StreamInfo result) -> {
                    isLoading.set(false);
                    if (result.getAgeLimit() != NO_AGE_LIMIT && !prefs.getBoolean(
                            getString(R.string.show_age_restricted_content), false)) {
                        hideAgeRestrictedContent();
                    } else {
                        currentInfo = result;
                        handleResult(result);
                        showContent();
                    }
                    hideMainPlayer();
                    if (addToBackStack) {
                        if (playQueue == null) playQueue = new SinglePlayQueue(result);
                        stack.push(new StackItem(serviceId, url, name, playQueue));
                    }
                    if (isAutoplayEnabled()) openVideoPlayer();
                }, (@NonNull Throwable throwable) -> {
                    isLoading.set(false);
                    onError(throwable);
                });
    }

    private void initTabs() {
        if (pageAdapter.getCount() != 0) {
            selectedTabTag = pageAdapter.getItemTitle(viewPager.getCurrentItem());
        }
        pageAdapter.clearAllItems();

        if (shouldShowComments()) {
            pageAdapter.addFragment(CommentsFragment.getInstance(serviceId, url, name),
                    COMMENTS_TAB_TAG);
        }

        if (showRelatedStreams && null == relatedStreamsLayout) {
            //temp empty fragment. will be updated in handleResult
            pageAdapter.addFragment(new Fragment(), RELATED_TAB_TAG);
        }

        if (pageAdapter.getCount() == 0) {
            pageAdapter.addFragment(new EmptyFragment(), EMPTY_TAB_TAG);
        }

        pageAdapter.notifyDataSetUpdate();

        if (pageAdapter.getCount() < 2) {
            tabLayout.setVisibility(View.GONE);
        } else {
            int position = pageAdapter.getItemPositionByTitle(selectedTabTag);
            if (position != -1) {
                viewPager.setCurrentItem(position);
            }
            tabLayout.setVisibility(View.VISIBLE);
        }
    }

    private boolean shouldShowComments() {
        try {
            return showComments && NewPipe.getService(serviceId)
                    .getServiceInfo()
                    .getMediaCapabilities()
                    .contains(COMMENTS);
        } catch (ExtractionException e) {
            return false;
        }
    }

    public void scrollToTop() {
        appBarLayout.setExpanded(true, true);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Play Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void openBackgroundPlayer(final boolean append) {
        AudioStream audioStream = currentInfo.getAudioStreams()
                .get(ListHelper.getDefaultAudioFormat(activity, currentInfo.getAudioStreams()));

        boolean useExternalAudioPlayer = PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(activity.getString(R.string.use_external_audio_player_key), false);

        //  If a user watched video inside fullscreen mode and than chose another player return to non-fullscreen mode
        if (player != null && player.isFullscreen()) player.toggleFullscreen();

        if (!useExternalAudioPlayer && android.os.Build.VERSION.SDK_INT >= 16) {
            openNormalBackgroundPlayer(append);
        } else {
            startOnExternalPlayer(activity, currentInfo, audioStream);
        }
    }

    private void openPopupPlayer(final boolean append) {
        if (!PermissionHelper.isPopupEnabled(activity)) {
            PermissionHelper.showPopupEnablementToast(activity);
            return;
        }

        // See UI changes while remote playQueue changes
        if (!bound) startService(false);

        //  If a user watched video inside fullscreen mode and than chose another player return to non-fullscreen mode
        if (player != null && player.isFullscreen()) player.toggleFullscreen();

        PlayQueue queue = setupPlayQueueForIntent(append);
        if (append) {
            NavigationHelper.enqueueOnPopupPlayer(activity, queue, false);
        } else {
            replaceQueueIfUserConfirms(() -> NavigationHelper.playOnPopupPlayer(activity, queue, true));
        }
    }

    private void openVideoPlayer() {
        if (PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(this.getString(R.string.use_external_video_player_key), false)) {
            VideoStream selectedVideoStream = getSelectedVideoStream();
            startOnExternalPlayer(activity, currentInfo, selectedVideoStream);
        } else {
            replaceQueueIfUserConfirms(this::openMainPlayer);
        }
    }

    private void openNormalBackgroundPlayer(final boolean append) {
        // See UI changes while remote playQueue changes
        if (!bound) startService(false);

        PlayQueue queue = setupPlayQueueForIntent(append);
        if (append) {
            NavigationHelper.enqueueOnBackgroundPlayer(activity, queue, false);
        } else {
            replaceQueueIfUserConfirms(() -> NavigationHelper.playOnBackgroundPlayer(activity, queue, true));
        }
    }

    private void openMainPlayer() {
        if (playerService == null) {
                startService(true);
                return;
        }
        if (currentInfo == null) return;

        PlayQueue queue = setupPlayQueueForIntent(false);

        // Video view can have elements visible from popup, We hide it here but once it ready the view will be shown in handleIntent()
        playerService.getView().setVisibility(View.GONE);
        addVideoPlayerView();

        Intent playerIntent = NavigationHelper.getPlayerIntent(getContext(), MainPlayer.class, queue, null, true);
        activity.startService(playerIntent);
    }

    private void hideMainPlayer() {
        if (playerService == null || playerService.getView() == null || !player.videoPlayerSelected())
            return;

        removeVideoPlayerView();
        playerService.stop(isAutoplayEnabled());
        playerService.getView().setVisibility(View.GONE);
    }

    private PlayQueue setupPlayQueueForIntent(boolean append) {
        if (append) return new SinglePlayQueue(currentInfo);

        PlayQueue queue = playQueue;
        // Size can be 0 because queue removes bad stream automatically when error occurs
        if (queue == null || queue.size() == 0)
            queue = new SinglePlayQueue(currentInfo);

        return queue;
    }

    private void openChannel() {
        if (TextUtils.isEmpty(currentInfo.getUploaderUrl())) {
            Log.w(TAG, "Can't open channel because we got no channel URL");
        } else {
            try {
                NavigationHelper.openChannelFragment(
                        getFragmentManager(),
                        currentInfo.getServiceId(),
                        currentInfo.getUploaderUrl(),
                        currentInfo.getUploaderName());
            } catch (Exception e) {
                ErrorActivity.reportUiError(activity, e);
            }
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    public void setAutoplay(final boolean autoplay) {
        this.autoPlayEnabled = autoplay;
    }

    private void startOnExternalPlayer(@NonNull final Context context,
                                       @NonNull final StreamInfo info,
                                       @NonNull final Stream selectedStream) {
        NavigationHelper.playOnExternalPlayer(context, currentInfo.getName(),
                currentInfo.getSubChannelName(), selectedStream);

        final HistoryRecordManager recordManager = new HistoryRecordManager(requireContext());
        disposables.add(recordManager.onViewed(info).onErrorComplete()
                .subscribe(
                        ignored -> { /* successful */ },
                        error -> Log.e(TAG, "Register view failure: ", error)
                ));
    }

    private boolean isExternalPlayerEnabled() {
        return PreferenceManager.getDefaultSharedPreferences(getContext())
                .getBoolean(getString(R.string.use_external_video_player_key), false);
    }

    // This method overrides default behaviour when setAutoplay() is called.
    // Don't auto play if the user selected an external player or disabled it in settings
    private boolean isAutoplayEnabled() {
        return autoPlayEnabled
                && !isExternalPlayerEnabled()
                && (player == null || player.videoPlayerSelected())
                && bottomSheetState != BottomSheetBehavior.STATE_HIDDEN
                && isAutoplayAllowedByUser();
    }

    private boolean isAutoplayAllowedByUser () {
        if (activity == null) return false;

        switch (PlayerHelper.getAutoplayType(activity)) {
            case PlayerHelper.AutoplayType.AUTOPLAY_TYPE_NEVER:
                return false;
            case PlayerHelper.AutoplayType.AUTOPLAY_TYPE_WIFI:
                return !ListHelper.isMeteredNetwork(activity);
            case PlayerHelper.AutoplayType.AUTOPLAY_TYPE_ALWAYS:
            default:
                return true;
        }
    }

    private void addVideoPlayerView() {
        if (player == null || getView() == null) return;

        FrameLayout viewHolder = getView().findViewById(R.id.player_placeholder);

        // Check if viewHolder already contains a child
        if (player.getRootView().getParent() != viewHolder) removeVideoPlayerView();
        setHeightThumbnail();

        // Prevent from re-adding a view multiple times
        if (player.getRootView().getParent() == null) viewHolder.addView(player.getRootView());
    }

    private void removeVideoPlayerView() {
        makeDefaultHeightForVideoPlaceholder();

        playerService.removeViewFromParent();
    }

    private void makeDefaultHeightForVideoPlaceholder() {
        if (getView() == null) return;

        FrameLayout viewHolder = getView().findViewById(R.id.player_placeholder);
        viewHolder.getLayoutParams().height = FrameLayout.LayoutParams.MATCH_PARENT;
        viewHolder.requestLayout();
    }


    @Nullable
    private VideoStream getSelectedVideoStream() {
        return sortedVideoStreams != null ? sortedVideoStreams.get(selectedVideoStreamIndex) : null;
    }

    private void prepareDescription(final Description description) {
        if (TextUtils.isEmpty(description.getContent())
                || description == Description.emptyDescription) {
            return;
        }

        if (description.getType() == Description.HTML) {
            disposables.add(Single.just(description.getContent())
                    .map((@io.reactivex.annotations.NonNull String descriptionText) -> {
                        Spanned parsedDescription;
                        if (Build.VERSION.SDK_INT >= 24) {
                            parsedDescription = Html.fromHtml(descriptionText, 0);
                        } else {
                            //noinspection deprecation
                            parsedDescription = Html.fromHtml(descriptionText);
                        }
                        return parsedDescription;
                    })
                    .subscribeOn(Schedulers.computation())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe((@io.reactivex.annotations.NonNull Spanned spanned) -> {
                        videoDescriptionView.setText(spanned);
                        videoDescriptionView.setVisibility(View.VISIBLE);
                    }));
        } else if (description.getType() == Description.MARKDOWN) {
            final Markwon markwon = Markwon.builder(getContext())
                    .usePlugin(LinkifyPlugin.create())
                    .build();
            markwon.setMarkdown(videoDescriptionView, description.getContent());
            videoDescriptionView.setVisibility(View.VISIBLE);
        } else {
            //== Description.PLAIN_TEXT
            videoDescriptionView.setAutoLinkMask(Linkify.WEB_URLS);
            videoDescriptionView.setText(description.getContent(), TextView.BufferType.SPANNABLE);
            videoDescriptionView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Method which controls the size of thumbnail and the size of main player inside a layout with thumbnail.
     * It decides what height the player should have in both screen orientations. It knows about multiWindow feature
     * and about videos with aspectRatio ZOOM (the height for them will be a bit higher, {@link #MAX_PLAYER_HEIGHT})
     */
    private void setHeightThumbnail() {
        final DisplayMetrics metrics = getResources().getDisplayMetrics();
        boolean isPortrait = metrics.heightPixels > metrics.widthPixels;

        int height;
        if (player != null && player.isFullscreen())
            height = isInMultiWindow() ? getView().getHeight() : activity.getWindow().getDecorView().getHeight();
        else
            height = isPortrait
                    ? (int) (metrics.widthPixels / (16.0f / 9.0f))
                    : (int) (metrics.heightPixels / 2.0f);

        thumbnailImageView.setLayoutParams(new FrameLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, height));
        thumbnailImageView.setMinimumHeight(height);
        if (player != null) {
            int maxHeight = (int) (metrics.heightPixels * MAX_PLAYER_HEIGHT);
            player.getSurfaceView().setHeights(height, player.isFullscreen() ? height : maxHeight);
        }
    }

    private void showContent() {
        contentRootLayoutHiding.setVisibility(View.VISIBLE);
    }

    protected void setInitialData(final int serviceId, final String url, final String title, final PlayQueue playQueue) {
        this.serviceId = serviceId;
        this.url = url;
        this.name = !TextUtils.isEmpty(title) ? title : "";
        this.playQueue = playQueue;
    }

    private void setErrorImage(final int imageResource) {
        if (thumbnailImageView == null || activity == null) {
            return;
        }

        thumbnailImageView.setImageDrawable(
                AppCompatResources.getDrawable(requireContext(), imageResource));
        animateView(thumbnailImageView, false, 0, 0,
                () -> animateView(thumbnailImageView, true, 500));
    }

    @Override
    public void showError(final String message, final boolean showRetryButton) {
        showError(message, showRetryButton, R.drawable.not_available_monkey);
    }

    protected void showError(final String message, final boolean showRetryButton,
                             @DrawableRes final int imageError) {
        super.showError(message, showRetryButton);
        setErrorImage(imageError);
    }

    private void setupBroadcastReceiver() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(intent.getAction().equals(ACTION_SHOW_MAIN_PLAYER)) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                } else if(intent.getAction().equals(ACTION_HIDE_MAIN_PLAYER)) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_SHOW_MAIN_PLAYER);
        intentFilter.addAction(ACTION_HIDE_MAIN_PLAYER);
        activity.registerReceiver(broadcastReceiver, intentFilter);
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Orientation listener
    //////////////////////////////////////////////////////////////////////////*/

    private void restoreDefaultOrientation() {
        if (player == null || !player.videoPlayerSelected() || activity == null) return;

        if (player != null && player.isFullscreen()) player.toggleFullscreen();
        // This will show systemUI and pause the player.
        // User can tap on Play button and video will be in fullscreen mode again
        // Note for tablet: trying to avoid orientation changes since it's not easy to physically rotate the tablet every time
        if (!PlayerHelper.isTablet(activity))
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void showLoading() {

        super.showLoading();

        //if data is already cached, transition from VISIBLE -> INVISIBLE -> VISIBLE is not required
        if (!ExtractorHelper.isCached(serviceId, url, InfoItem.InfoType.STREAM)) {
            contentRootLayoutHiding.setVisibility(View.INVISIBLE);
        }

        animateView(thumbnailPlayButton, false, 50);
        animateView(detailDurationView, false, 100);
        animateView(detailPositionView, false, 100);
        animateView(positionView, false, 50);

        videoTitleTextView.setText(name != null ? name : "");
        videoTitleTextView.setMaxLines(1);
        animateView(videoTitleTextView, true, 0);

        videoDescriptionRootLayout.setVisibility(View.GONE);
        videoTitleToggleArrow.setVisibility(View.GONE);
        videoTitleRoot.setClickable(false);

        if(relatedStreamsLayout != null){
            if(showRelatedStreams){
                relatedStreamsLayout.setVisibility(player != null && player.isFullscreen() ? View.GONE : View.INVISIBLE);
            } else {
                relatedStreamsLayout.setVisibility(View.GONE);
            }
        }

        IMAGE_LOADER.cancelDisplayTask(thumbnailImageView);
        IMAGE_LOADER.cancelDisplayTask(subChannelThumb);
        thumbnailImageView.setImageBitmap(null);
        subChannelThumb.setImageBitmap(null);
    }

    @Override
    public void handleResult(@NonNull final StreamInfo info) {
        super.handleResult(info);

        currentInfo = info;
        setInitialData(info.getServiceId(), info.getOriginalUrl(), info.getName(), playQueue);

        if(showRelatedStreams){
            if(null == relatedStreamsLayout){ //phone
                pageAdapter.updateItem(RELATED_TAB_TAG, RelatedVideosFragment.getInstance(info));
                pageAdapter.notifyDataSetUpdate();
            } else { //tablet
                getChildFragmentManager().beginTransaction()
                        .replace(R.id.relatedStreamsLayout,
                                RelatedVideosFragment.getInstance(currentInfo))
                        .commitNow();
                relatedStreamsLayout.setVisibility(player != null && player.isFullscreen() ? View.GONE : View.VISIBLE);
            }
        }

        animateView(thumbnailPlayButton, true, 200);
        videoTitleTextView.setText(name);

        if (!TextUtils.isEmpty(info.getSubChannelName())) {
            displayBothUploaderAndSubChannel(info);
        } else if (!TextUtils.isEmpty(info.getUploaderName())) {
            displayUploaderAsSubChannel(info);
        } else {
            uploaderTextView.setVisibility(View.GONE);
            uploaderThumb.setVisibility(View.GONE);
        }

        Drawable buddyDrawable = AppCompatResources.getDrawable(activity, R.drawable.buddy);
        subChannelThumb.setImageDrawable(buddyDrawable);
        uploaderThumb.setImageDrawable(buddyDrawable);

        if (info.getViewCount() >= 0) {
            if (info.getStreamType().equals(StreamType.AUDIO_LIVE_STREAM)) {
                videoCountView.setText(Localization.listeningCount(activity, info.getViewCount()));
            } else if (info.getStreamType().equals(StreamType.LIVE_STREAM)) {
                videoCountView.setText(Localization
                        .localizeWatchingCount(activity, info.getViewCount()));
            } else {
                videoCountView.setText(Localization
                        .localizeViewCount(activity, info.getViewCount()));
            }
            videoCountView.setVisibility(View.VISIBLE);
        } else {
            videoCountView.setVisibility(View.GONE);
        }

        if (info.getDislikeCount() == -1 && info.getLikeCount() == -1) {
            thumbsDownImageView.setVisibility(View.VISIBLE);
            thumbsUpImageView.setVisibility(View.VISIBLE);
            thumbsUpTextView.setVisibility(View.GONE);
            thumbsDownTextView.setVisibility(View.GONE);

            thumbsDisabledTextView.setVisibility(View.VISIBLE);
        } else {
            if (info.getDislikeCount() >= 0) {
                thumbsDownTextView.setText(Localization
                        .shortCount(activity, info.getDislikeCount()));
                thumbsDownTextView.setVisibility(View.VISIBLE);
                thumbsDownImageView.setVisibility(View.VISIBLE);
            } else {
                thumbsDownTextView.setVisibility(View.GONE);
                thumbsDownImageView.setVisibility(View.GONE);
            }

            if (info.getLikeCount() >= 0) {
                thumbsUpTextView.setText(Localization.shortCount(activity, info.getLikeCount()));
                thumbsUpTextView.setVisibility(View.VISIBLE);
                thumbsUpImageView.setVisibility(View.VISIBLE);
            } else {
                thumbsUpTextView.setVisibility(View.GONE);
                thumbsUpImageView.setVisibility(View.GONE);
            }
            thumbsDisabledTextView.setVisibility(View.GONE);
        }

        if (info.getDuration() > 0) {
            detailDurationView.setText(Localization.getDurationString(info.getDuration()));
            detailDurationView.setBackgroundColor(
                    ContextCompat.getColor(activity, R.color.duration_background_color));
            animateView(detailDurationView, true, 100);
        } else if (info.getStreamType() == StreamType.LIVE_STREAM) {
            detailDurationView.setText(R.string.duration_live);
            detailDurationView.setBackgroundColor(
                    ContextCompat.getColor(activity, R.color.live_duration_background_color));
            animateView(detailDurationView, true, 100);
        } else {
            detailDurationView.setVisibility(View.GONE);
        }

        videoDescriptionView.setVisibility(View.GONE);
        videoTitleRoot.setClickable(true);
        videoTitleToggleArrow.setImageResource(
                ThemeHelper.resolveResourceIdFromAttr(requireContext(), R.attr.ic_expand_more));
        videoTitleToggleArrow.setVisibility(View.VISIBLE);
        videoDescriptionRootLayout.setVisibility(View.GONE);

        if (info.getUploadDate() != null) {
            videoUploadDateView.setText(Localization
                    .localizeUploadDate(activity, info.getUploadDate().date().getTime()));
            videoUploadDateView.setVisibility(View.VISIBLE);
        } else {
            videoUploadDateView.setText(null);
            videoUploadDateView.setVisibility(View.GONE);
        }

        prepareDescription(info.getDescription());
        updateProgressInfo(info);
        initThumbnailViews(info);

        if (player == null || player.isPlayerStopped())
            updateOverlayData(info.getName(), info.getUploaderName(), info.getThumbnailUrl());


        if (!info.getErrors().isEmpty()) {
            showSnackBarError(info.getErrors(),
                    UserAction.REQUESTED_STREAM,
                    NewPipe.getNameOfService(info.getServiceId()),
                    info.getUrl(),
                    0);
        }

        switch (info.getStreamType()) {
            case LIVE_STREAM:
            case AUDIO_LIVE_STREAM:
                detailControlsDownload.setVisibility(View.GONE);
                break;
            default:
                if (info.getAudioStreams().isEmpty()) {
                    detailControlsBackground.setVisibility(View.GONE);
                }
                if (!info.getVideoStreams().isEmpty() || !info.getVideoOnlyStreams().isEmpty()) {
                    break;
                }

                detailControlsPopup.setVisibility(View.GONE);
                spinnerToolbar.setVisibility(View.GONE);
                thumbnailPlayButton.setImageResource(R.drawable.ic_headset_white_24dp);
                break;
        }
    }

    private void hideAgeRestrictedContent() {
        showError(getString(R.string.restricted_video), false);

        if (relatedStreamsLayout != null) { // tablet
            relatedStreamsLayout.setVisibility(View.INVISIBLE);
        }

        viewPager.setVisibility(View.GONE);
        tabLayout.setVisibility(View.GONE);
    }

    private void displayUploaderAsSubChannel(final StreamInfo info) {
        subChannelTextView.setText(info.getUploaderName());
        subChannelTextView.setVisibility(View.VISIBLE);
        subChannelTextView.setSelected(true);
        uploaderTextView.setVisibility(View.GONE);
    }

    private void displayBothUploaderAndSubChannel(final StreamInfo info) {
        subChannelTextView.setText(info.getSubChannelName());
        subChannelTextView.setVisibility(View.VISIBLE);
        subChannelTextView.setSelected(true);

        subChannelThumb.setVisibility(View.VISIBLE);

        if (!TextUtils.isEmpty(info.getUploaderName())) {
            uploaderTextView.setText(
                    String.format(getString(R.string.video_detail_by), info.getUploaderName()));
            uploaderTextView.setVisibility(View.VISIBLE);
            uploaderTextView.setSelected(true);
        } else {
            uploaderTextView.setVisibility(View.GONE);
        }
    }


    public void openDownloadDialog() {
        try {
            DownloadDialog downloadDialog = DownloadDialog.newInstance(currentInfo);
            downloadDialog.setVideoStreams(sortedVideoStreams);
            downloadDialog.setAudioStreams(currentInfo.getAudioStreams());
            downloadDialog.setSelectedVideoStream(selectedVideoStreamIndex);
            downloadDialog.setSubtitleStreams(currentInfo.getSubtitles());

                downloadDialog.show(getActivity().getSupportFragmentManager(), "downloadDialog");
            } catch (Exception e) {
                ErrorActivity.ErrorInfo info = ErrorActivity.ErrorInfo.make(UserAction.UI_ERROR,
                        ServiceList.all()
                                .get(currentInfo
                                        .getServiceId())
                                .getServiceInfo()
                                .getName(), "",
                        R.string.could_not_setup_download_menu);

                ErrorActivity.reportError(getActivity(),
                        e,
                        getActivity().getClass(),
                        getActivity().findViewById(android.R.id.content), info);
            }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Stream Results
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected boolean onError(final Throwable exception) {
        if (super.onError(exception)) {
            return true;
        }

        int errorId = exception instanceof YoutubeStreamExtractor.DecryptException
                ? R.string.youtube_signature_decryption_error
                : exception instanceof ExtractionException
                        ? R.string.parsing_error
                        : R.string.general_error;

        onUnrecoverableError(exception, UserAction.REQUESTED_STREAM,
                NewPipe.getNameOfService(serviceId), url, errorId);

        return true;
    }

    private void updateProgressInfo(@NonNull final StreamInfo info) {
        if (positionSubscriber != null) {
            positionSubscriber.dispose();
        }
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        final boolean playbackResumeEnabled =
                prefs.getBoolean(activity.getString(R.string.enable_watch_history_key), true)
                        && prefs.getBoolean(activity.getString(R.string.enable_playback_resume_key), true);
        if (!playbackResumeEnabled || info.getDuration() <= 0) {
            positionView.setVisibility(View.INVISIBLE);
            detailPositionView.setVisibility(View.GONE);
            return;
        }
        final HistoryRecordManager recordManager = new HistoryRecordManager(requireContext());

        // TODO: Separate concerns when updating database data.
        //  (move the updating part to when the loading happens)
        positionSubscriber = recordManager.loadStreamState(info)
                .subscribeOn(Schedulers.io())
                .onErrorComplete()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(state -> {
                    final int seconds = (int) TimeUnit.MILLISECONDS.toSeconds(state.getProgressTime());
                    positionView.setMax((int) info.getDuration());
                    positionView.setProgressAnimated(seconds);
                    detailPositionView.setText(Localization.getDurationString(seconds));
                    animateView(positionView, true, 500);
                    animateView(detailPositionView, true, 500);
                }, e -> {
                    if (DEBUG) {
                        e.printStackTrace();
                    }
                }, () -> {
                    positionView.setVisibility(View.GONE);
                    detailPositionView.setVisibility(View.GONE);
                });
    }

    private void showPlaybackProgress(long progress, long duration) {
        final int progressSeconds = (int) TimeUnit.MILLISECONDS.toSeconds(progress);
        final int durationSeconds = (int) TimeUnit.MILLISECONDS.toSeconds(duration);
        positionView.setMax(durationSeconds);
        positionView.setProgress(progressSeconds);
        detailPositionView.setText(Localization.getDurationString(progressSeconds));
        if (positionView.getVisibility() != View.VISIBLE) {
            animateView(positionView, true, 100);
            animateView(detailPositionView, true, 100);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Player event listener
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onQueueUpdate(PlayQueue queue) {
        playQueue = queue;
        StackItem stackWithQueue;
        // This should be the only place where we push data to stack. It will allow to have live instance of PlayQueue with actual
        // information about deleted/added items inside Channel/Playlist queue and makes possible to have a history of played items
        if (stack.isEmpty() || !stack.peek().getPlayQueue().equals(queue)) {
            stack.push(new StackItem(serviceId, url, name, playQueue));
        } else if ((stackWithQueue = findQueueInStack(queue)) != null) {
            // On every MainPlayer service's destroy() playQueue gets disposed and no longer able to track progress.
            // That's why we update our cached disposed queue with the new one that is active and have the same history
            // Without that the cached playQueue will have an old recovery position
            stackWithQueue.setPlayQueue(queue);
        }

        if (DEBUG) {
            Log.d(TAG, "onQueueUpdate() called with: serviceId = ["
                    + serviceId + "], videoUrl = [" + url + "], name = [" + name + "], playQueue = [" + playQueue + "]");
        }
    }

    @Override
    public void onPlaybackUpdate(int state, int repeatMode, boolean shuffled, PlaybackParameters parameters) {
        setOverlayPlayPauseImage();

        switch (state) {
            case BasePlayer.STATE_COMPLETED:
                restoreDefaultOrientation();
                break;
            case BasePlayer.STATE_PLAYING:
                if (positionView.getAlpha() != 1.0f
                        && player.getPlayQueue() != null
                        && player.getPlayQueue().getItem() != null
                        && player.getPlayQueue().getItem().getUrl().equals(url)) {
                    animateView(positionView, true, 100);
                    animateView(detailPositionView, true, 100);
                }
                break;
        }
    }

    @Override
    public void onProgressUpdate(int currentProgress, int duration, int bufferPercent) {
        // Progress updates every second even if media is paused. It's useless until playing
        if (!player.getPlayer().isPlaying() || playQueue == null) return;

        if (player.getPlayQueue().getItem().getUrl().equals(url))
            showPlaybackProgress(currentProgress, duration);
    }

    @Override
    public void onMetadataUpdate(StreamInfo info, PlayQueue queue) {
        StackItem item = findQueueInStack(queue);
        if (item != null) {
            // When PlayQueue can have multiple streams (PlaylistPlayQueue or ChannelPlayQueue) every new played stream gives
            // new title and url. StackItem contains information about first played stream. Let's update it here
            item.setTitle(info.getName());
            item.setUrl(info.getUrl());
        }
        // They are not equal when user watches something in popup while browsing in fragment and then changes screen orientation
        // In that case the fragment will set itself as a service listener and will receive initial call to onMetadataUpdate()
        if (!queue.equals(playQueue)) return;

        updateOverlayData(info.getName(), info.getUploaderName(), info.getThumbnailUrl());
        if (currentInfo != null && info.getUrl().equals(currentInfo.getUrl())) return;

        currentInfo = info;
        setInitialData(info.getServiceId(), info.getUrl(),info.getName(), queue);
        setAutoplay(false);
        prepareAndHandleInfo(info, true);
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        if (error.type == ExoPlaybackException.TYPE_SOURCE || error.type == ExoPlaybackException.TYPE_UNEXPECTED) {
            hideMainPlayer();
            if (playerService != null && player.isFullscreen())
                player.toggleFullscreen();
        }
    }

    @Override
    public void onServiceStopped() {
        unbind();
        setOverlayPlayPauseImage();
        if (currentInfo != null)
            updateOverlayData(currentInfo.getName(), currentInfo.getUploaderName(), currentInfo.getThumbnailUrl());
    }

    @Override
    public void onFullscreenStateChanged(boolean fullscreen) {
        if (playerService.getView() == null || player.getParentActivity() == null)
            return;

        View view = playerService.getView();
        ViewGroup parent = (ViewGroup) view.getParent();
        if (parent == null) return;

        if (fullscreen) {
            hideSystemUiIfNeeded();
        } else {
            showSystemUi();
        }

        if (relatedStreamsLayout != null) relatedStreamsLayout.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        scrollToTop();

        addVideoPlayerView();
    }

    @Override
    public void onScreenRotationButtonClicked() {
        // In tablet user experience will be better if screen will not be rotated from landscape to portrait every time
        // Just turn on fullscreen mode in landscape orientation
        if (isLandscape() && PlayerHelper.isTablet(activity)) {
            player.toggleFullscreen();
            return;
        }

        int newOrientation = isLandscape() ?
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;

        activity.setRequestedOrientation(newOrientation);
    }

    /*
    * Will scroll down to description view after long click on moreOptionsButton
    * */
    @Override
    public void onMoreOptionsLongClicked() {
        CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) appBarLayout.getLayoutParams();
        AppBarLayout.Behavior behavior = (AppBarLayout.Behavior) params.getBehavior();
        ValueAnimator valueAnimator = ValueAnimator.ofInt(0, -getView().findViewById(R.id.player_placeholder).getHeight());
        valueAnimator.setInterpolator(new DecelerateInterpolator());
        valueAnimator.addUpdateListener(animation -> {
            behavior.setTopAndBottomOffset((int) animation.getAnimatedValue());
            appBarLayout.requestLayout();
        });
        valueAnimator.setInterpolator(new DecelerateInterpolator());
        valueAnimator.setDuration(500);
        valueAnimator.start();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Player related utils
    //////////////////////////////////////////////////////////////////////////*/

    private void showSystemUi() {
        if (DEBUG) Log.d(TAG, "showSystemUi() called");

        if (activity == null) return;

        activity.getWindow().getDecorView().setSystemUiVisibility(0);
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
    }

    private void hideSystemUi() {
        if (DEBUG) Log.d(TAG, "hideSystemUi() called");

        if (activity == null) return;

        int visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        activity.getWindow().getDecorView().setSystemUiVisibility(visibility);
        activity.getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
    }

    // Listener implementation
    public void hideSystemUiIfNeeded() {
        if (player != null && player.isFullscreen() && bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED)
            hideSystemUi();
    }

    private boolean playerIsNotStopped() {
        return player != null && player.getPlayer() != null && player.getPlayer().getPlaybackState() != Player.STATE_IDLE;
    }

    private void setupBrightness(boolean save) {
        if (activity == null) return;

        WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
        float brightnessLevel;

        if (save) {
            // Save current brightness level
            PlayerHelper.setScreenBrightness(activity, lp.screenBrightness);

            // Restore the old  brightness when fragment.onPause() called.
            // It means when user leaves this fragment brightness will be set to system brightness
            lp.screenBrightness = -1;
        } else {
            // Restore already saved brightness level
            brightnessLevel = PlayerHelper.getScreenBrightness(activity);
            if (brightnessLevel <= 0.0f && brightnessLevel > 1.0f)
                return;

            lp.screenBrightness = brightnessLevel;
        }
        activity.getWindow().setAttributes(lp);
    }

    private void checkLandscape() {
        if ((!player.isPlaying() && player.getPlayQueue() != playQueue) || player.getPlayQueue() == null)
            setAutoplay(true);

        player.checkLandscape();
        boolean orientationLocked = PlayerHelper.globalScreenOrientationLocked(activity);
        // Let's give a user time to look at video information page if video is not playing
        if (orientationLocked && !player.isPlaying()) {
            player.onPlay();
            player.showControlsThenHide();
        }
    }

    private boolean isLandscape() {
        return getResources().getDisplayMetrics().heightPixels < getResources().getDisplayMetrics().widthPixels;
    }

    private boolean isInMultiWindow() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInMultiWindowMode();
    }

    /*
    * Means that the player fragment was swiped away via BottomSheetLayout and is empty but ready for any new actions. See cleanUp()
    * */
    private boolean wasCleared() {
        return url == null;
    }

    private StackItem findQueueInStack(PlayQueue queue) {
        StackItem item = null;
        Iterator<StackItem> iterator = stack.descendingIterator();
        while (iterator.hasNext()) {
            StackItem next = iterator.next();
            if (next.getPlayQueue().equals(queue)) {
                item = next;
                break;
            }
        }
        return item;
    }

    private void replaceQueueIfUserConfirms(final Runnable onAllow) {
        @Nullable final PlayQueue activeQueue = player == null ? null : player.getPlayQueue();

        // Player will have STATE_IDLE when a user pressed back button
        if (isClearingQueueConfirmationRequired(activity)
                && playerIsNotStopped()
                && activeQueue != null
                && !activeQueue.equals(playQueue)
                && activeQueue.getStreams().size() > 1) {
            showClearingQueueConfirmation(onAllow);
        } else {
            onAllow.run();
        }
    }

    private void showClearingQueueConfirmation(Runnable onAllow) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.confirm_prompt)
                .setMessage(R.string.clear_queue_confirmation_description)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                    onAllow.run();
                    dialog.dismiss();
                }).show();
    }

    /*
    * Remove unneeded information while waiting for a next task
    * */
    private void cleanUp() {
        // New beginning
        stack.clear();
        if (currentWorker != null) currentWorker.dispose();
        stopService();
        setInitialData(0,null,"", null);
        currentInfo = null;
        updateOverlayData(null, null, null);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Bottom mini player
    //////////////////////////////////////////////////////////////////////////*/

    private void setupBottomPlayer() {
        CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) appBarLayout.getLayoutParams();
        AppBarLayout.Behavior behavior = (AppBarLayout.Behavior) params.getBehavior();

        FrameLayout bottomSheetLayout = activity.findViewById(R.id.fragment_player_holder);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetLayout);
        bottomSheetBehavior.setState(bottomSheetState);
        final int peekHeight = getResources().getDimensionPixelSize(R.dimen.mini_player_height);
        if (bottomSheetState != BottomSheetBehavior.STATE_HIDDEN) {
            bottomSheetBehavior.setPeekHeight(peekHeight);
            if (bottomSheetState == BottomSheetBehavior.STATE_COLLAPSED) {
                overlay.setAlpha(MAX_OVERLAY_ALPHA);
            } else if (bottomSheetState == BottomSheetBehavior.STATE_EXPANDED) {
                overlay.setAlpha(0);
                setOverlayElementsClickable(false);
            }
        }

        bottomSheetBehavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override public void onStateChanged(@NonNull View bottomSheet, int newState) {
                bottomSheetState = newState;

                switch (newState) {
                    case BottomSheetBehavior.STATE_HIDDEN:
                        bottomSheetBehavior.setPeekHeight(0);
                        cleanUp();
                        break;
                    case BottomSheetBehavior.STATE_EXPANDED:
                        bottomSheetBehavior.setPeekHeight(peekHeight);
                        // Disable click because overlay buttons located on top of buttons from the player
                        setOverlayElementsClickable(false);
                        hideSystemUiIfNeeded();
                        // Conditions when the player should be expanded to fullscreen
                        if (isLandscape()
                                && player != null
                                && player.isPlaying()
                                && !player.isFullscreen()
                                && !PlayerHelper.isTablet(activity)
                                && player.videoPlayerSelected()) player.toggleFullscreen();
                        break;
                    case BottomSheetBehavior.STATE_COLLAPSED:
                        // Re-enable clicks
                        setOverlayElementsClickable(true);
                        if (player != null) player.onQueueClosed();
                        break;
                    case BottomSheetBehavior.STATE_DRAGGING:
                    case BottomSheetBehavior.STATE_SETTLING:
                        if (player != null && player.isFullscreen()) showSystemUi();
                        if (player != null && player.isControlsVisible()) player.hideControls(0, 0);
                        break;
                }
            }
            @Override public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                setOverlayLook(appBarLayout, behavior, slideOffset);
            }
        });

        // User opened a new page and the player will hide itself
        activity.getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED)
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        });
    }

    private void updateOverlayData(@Nullable String title, @Nullable String uploader, @Nullable String thumbnailUrl) {
        overlayTitleTextView.setText(TextUtils.isEmpty(title) ? "" : title);
        overlayChannelTextView.setText(TextUtils.isEmpty(uploader) ? "" : uploader);
        overlayThumbnailImageView.setImageResource(R.drawable.dummy_thumbnail_dark);
        if (!TextUtils.isEmpty(thumbnailUrl))
            imageLoader.displayImage(thumbnailUrl, overlayThumbnailImageView,
                ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS, null);
    }

    private void setOverlayPlayPauseImage() {
        int attr = player != null && player.getPlayer().getPlayWhenReady() ? R.attr.pause : R.attr.play;
        overlayPlayPauseButton.setImageResource(ThemeHelper.resolveResourceIdFromAttr(activity, attr));
    }

    private void setOverlayLook(AppBarLayout appBarLayout, AppBarLayout.Behavior behavior, float slideOffset) {
        if (behavior != null) {
            overlay.setAlpha(Math.min(MAX_OVERLAY_ALPHA, 1 - slideOffset));

            // These numbers are not special. They just do a cool transition
            behavior.setTopAndBottomOffset((int)(-thumbnailImageView.getHeight() * 2 * (1 - slideOffset) / 3));
            appBarLayout.requestLayout();
        }
    }

    private void setOverlayElementsClickable(boolean enable) {
        overlayThumbnailImageView.setClickable(enable);
        overlayThumbnailImageView.setLongClickable(enable);
        overlayMetadata.setClickable(enable);
        overlayMetadata.setLongClickable(enable);
        overlayButtons.setClickable(enable);
        overlayPlayPauseButton.setClickable(enable);
        overlayCloseButton.setClickable(enable);
    }
}
