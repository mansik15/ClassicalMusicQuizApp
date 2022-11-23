/*
* Copyright (C) 2017 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*  	http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.example.android.classicalmusicquiz

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.session.MediaButtonReceiver
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.trackselection.TrackSelector
import com.google.android.exoplayer2.ui.SimpleExoPlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util

class QuizActivity : AppCompatActivity(), View.OnClickListener, ExoPlayer.EventListener {
    private val mButtonIDs = intArrayOf(R.id.buttonA, R.id.buttonB, R.id.buttonC, R.id.buttonD)
    private lateinit var mRemainingSampleIDs: ArrayList<Int>
    private lateinit var mQuestionSampleIDs: ArrayList<Int>
    private var mAnswerSampleID = 0
    private var mCurrentScore = 0
    private var mHighScore = 0
    private lateinit var mButtons: Array<Button?>
    private var mExoPlayer: SimpleExoPlayer? = null
    private lateinit var mPlayerView: SimpleExoPlayerView
    private lateinit var mStateBuilder: PlaybackStateCompat.Builder
    private var mNotificationManager: NotificationManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz)

        // Initialize the player view.
        mPlayerView = findViewById(R.id.playerView)
        val isNewGame: Boolean = !intent.hasExtra(REMAINING_SONGS_KEY)

        // If it's a new game, set the current score to 0 and load all samples.
        mRemainingSampleIDs = if (isNewGame) {
            QuizUtils.setCurrentScore(this, 0)
            Sample.getAllSampleIDs(this@QuizActivity)
            // Otherwise, get the remaining songs from the Intent.
        } else {
            intent.getIntegerArrayListExtra(REMAINING_SONGS_KEY)!!
        }

        // Get current and high scores.
        mCurrentScore = QuizUtils.getCurrentScore(this)
        mHighScore = QuizUtils.getHighScore(this)

        // Generate a question and get the correct answer.
        mQuestionSampleIDs = QuizUtils.generateQuestion(mRemainingSampleIDs)
        mAnswerSampleID = QuizUtils.getCorrectAnswerID(mQuestionSampleIDs)

        // Load the question mark as the background image until the user answers the question.
        mPlayerView.defaultArtwork = BitmapFactory.decodeResource(
            resources,
            R.drawable.question_mark
        )

        // If there is only one answer left, end the game.
        if (mQuestionSampleIDs.size < 2) {
            QuizUtils.endGame(this)
            finish()
        }

        // Initialize the buttons with the composers names.
        mButtons = initializeButtons(mQuestionSampleIDs)

        // Initialize the Media Session.
        initializeMediaSession()
        val answerSample = Sample.getSampleByID(this, mAnswerSampleID)
        if (answerSample == null) {
            Toast.makeText(
                this, getString(R.string.sample_not_found_error),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Initialize the player.
        initializePlayer(Uri.parse(answerSample.uri))
    }

    /**
     * Initializes the Media Session to be enabled with media buttons, transport controls, callbacks
     * and media controller.
     */
    private fun initializeMediaSession() {

        // Create a MediaSessionCompat.
        mMediaSession = MediaSessionCompat(this, TAG)

        // Enable callbacks from MediaButtons and TransportControls.
        mMediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )

        // Do not let MediaButtons restart the player when the app is not visible.
        mMediaSession.setMediaButtonReceiver(null)

        // Set an initial PlaybackState with ACTION_PLAY, so media buttons can start the player.
        mStateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
            )
        mMediaSession.setPlaybackState(mStateBuilder.build())


        // MySessionCallback has methods that handle callbacks from a media controller.
        mMediaSession.setCallback(MySessionCallback())

        // Start the Media Session since the activity is active.
        mMediaSession.isActive = true
    }

    /**
     * Initializes the button to the correct views, and sets the text to the composers names,
     * and set's the OnClick listener to the buttons.
     *
     * @param answerSampleIDs The IDs of the possible answers to the question.
     * @return The Array of initialized buttons.
     */
    private fun initializeButtons(answerSampleIDs: ArrayList<Int>): Array<Button?> {
        val buttons = arrayOfNulls<Button>(mButtonIDs.size)
        for (i in answerSampleIDs.indices) {
            val currentButton = findViewById<Button>(mButtonIDs[i])
            val currentSample = Sample.getSampleByID(this, answerSampleIDs[i])
            buttons[i] = currentButton
            currentButton.setOnClickListener(this)
            if (currentSample != null) {
                currentButton.text = currentSample.composer
            }
        }
        return buttons
    }

    /**
     * Shows Media Style notification, with actions that depend on the current MediaSession
     * PlaybackState.
     * @param state The PlaybackState of the MediaSession.
     */
    private fun showNotification(state: PlaybackStateCompat) {
        val builder: NotificationCompat.Builder = NotificationCompat.Builder(this)
        val icon: Int
        val play_pause: String
        if (state.state == PlaybackStateCompat.STATE_PLAYING) {
            icon = R.drawable.exo_controls_pause
            play_pause = getString(R.string.pause)
        } else {
            icon = R.drawable.exo_controls_play
            play_pause = getString(R.string.play)
        }
        val playPauseAction: NotificationCompat.Action = NotificationCompat.Action(
            icon, play_pause,
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                this,
                PlaybackStateCompat.ACTION_PLAY_PAUSE
            )
        )
        val restartAction: NotificationCompat.Action = NotificationCompat.Action(
            R.drawable.exo_controls_previous, getString(R.string.restart),
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                this,
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
        )
        val contentPendingIntent: PendingIntent =
            PendingIntent.getActivity(this, 0, Intent(this, QuizActivity::class.java), 0)
        builder.setContentTitle(getString(R.string.guess))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(contentPendingIntent)
            .setSmallIcon(R.drawable.ic_music_note)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(restartAction)
            .addAction(playPauseAction)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mMediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
        mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager?
        mNotificationManager!!.notify(0, builder.build())
    }

    /**
     * Initialize ExoPlayer.
     * @param mediaUri The URI of the sample to play.
     */
    private fun initializePlayer(mediaUri: Uri) {
        if (mExoPlayer == null) {
            // Create an instance of the ExoPlayer.
            val trackSelector: TrackSelector = DefaultTrackSelector()
            val loadControl: LoadControl = DefaultLoadControl()
            mExoPlayer = ExoPlayerFactory.newSimpleInstance(this, trackSelector, loadControl)
            mPlayerView!!.player = mExoPlayer

            // Set the ExoPlayer.EventListener to this activity.
            mExoPlayer?.addListener(this)

            // Prepare the MediaSource.
            val userAgent = Util.getUserAgent(this, "ClassicalMusicQuiz")
            val mediaSource: MediaSource = ExtractorMediaSource(
                mediaUri, DefaultDataSourceFactory(
                    this, userAgent
                ), DefaultExtractorsFactory(), null, null
            )
            mExoPlayer?.prepare(mediaSource)
            mExoPlayer?.playWhenReady = true
        }
    }

    /**
     * Release ExoPlayer.
     */
    private fun releasePlayer() {
        mNotificationManager?.cancelAll()
        mExoPlayer?.stop()
        mExoPlayer?.release()
        mExoPlayer = null
    }

    /**
     * The OnClick method for all of the answer buttons. The method uses the index of the button
     * in button array to to get the ID of the sample from the array of question IDs. It also
     * toggles the UI to show the correct answer.
     *
     * @param v The button that was clicked.
     */
    override fun onClick(v: View) {

        // Show the correct answer.
        showCorrectAnswer()

        // Get the button that was pressed.
        val pressedButton = v as Button

        // Get the index of the pressed button
        var userAnswerIndex = -1
        for (i in mButtons.indices) {
            if (pressedButton.id == mButtonIDs[i]) {
                userAnswerIndex = i
            }
        }

        // Get the ID of the sample that the user selected.
        val userAnswerSampleID = mQuestionSampleIDs!![userAnswerIndex]

        // If the user is correct, increase there score and update high score.
        if (QuizUtils.userCorrect(mAnswerSampleID, userAnswerSampleID)) {
            mCurrentScore++
            QuizUtils.setCurrentScore(this, mCurrentScore)
            if (mCurrentScore > mHighScore) {
                mHighScore = mCurrentScore
                QuizUtils.setHighScore(this, mHighScore)
            }
        }

        // Remove the answer sample from the list of all samples, so it doesn't get asked again.
        mRemainingSampleIDs!!.remove(Integer.valueOf(mAnswerSampleID))

        // Wait some time so the user can see the correct answer, then go to the next question.
        val handler = Handler()
        handler.postDelayed({
            mExoPlayer?.stop()
            val nextQuestionIntent = Intent(this@QuizActivity, QuizActivity::class.java)
            nextQuestionIntent.putExtra(REMAINING_SONGS_KEY, mRemainingSampleIDs)
            finish()
            startActivity(nextQuestionIntent)
        }, CORRECT_ANSWER_DELAY_MILLIS.toLong())
    }

    /**
     * Disables the buttons and changes the background colors and player art to
     * show the correct answer.
     */
    private fun showCorrectAnswer() {
        mPlayerView?.defaultArtwork = Sample.getComposerArtBySampleID(this, mAnswerSampleID)
        for (i in mQuestionSampleIDs!!.indices) {
            val buttonSampleID = mQuestionSampleIDs!![i]
            mButtons[i]!!.isEnabled = false
            if (buttonSampleID == mAnswerSampleID) {
                mButtons[i]!!
                    .background.setColorFilter(
                        ContextCompat.getColor(this, android.R.color.holo_green_light),
                        PorterDuff.Mode.MULTIPLY
                    )
                mButtons[i]!!.setTextColor(Color.WHITE)
            } else {
                mButtons[i]!!
                    .background.setColorFilter(
                        ContextCompat.getColor(this, android.R.color.holo_red_light),
                        PorterDuff.Mode.MULTIPLY
                    )
                mButtons[i]!!.setTextColor(Color.WHITE)
            }
        }
    }

    /**
     * Release the player when the activity is destroyed.
     */
    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
        mMediaSession.isActive = false
    }

    // ExoPlayer Event Listeners
    override fun onTimelineChanged(timeline: Timeline?, manifest: Any?) {}
    override fun onTracksChanged(trackGroups: TrackGroupArray?, trackSelections: TrackSelectionArray?) {}
    override fun onLoadingChanged(isLoading: Boolean) {}

    /**
     * Method that is called when the ExoPlayer state changes. Used to update the MediaSession
     * PlayBackState to keep in sync, and post the media notification.
     * @param playWhenReady true if ExoPlayer is playing, false if it's paused.
     * @param playbackState int describing the state of ExoPlayer. Can be STATE_READY, STATE_IDLE,
     * STATE_BUFFERING, or STATE_ENDED.
     */
    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
        if (playbackState == ExoPlayer.STATE_READY && playWhenReady) {
            mStateBuilder?.setState(
                PlaybackStateCompat.STATE_PLAYING,
                mExoPlayer!!.currentPosition, 1f
            )
        } else if (playbackState == ExoPlayer.STATE_READY) {
            mStateBuilder?.setState(
                PlaybackStateCompat.STATE_PAUSED,
                mExoPlayer!!.currentPosition, 1f
            )
        }
        mMediaSession.setPlaybackState(mStateBuilder?.build())
        showNotification(mStateBuilder!!.build())
    }

    override fun onPlayerError(error: ExoPlaybackException?) {}
    override fun onPositionDiscontinuity() {}

    /**
     * Media Session Callbacks, where all external clients control the player.
     */
    private inner class MySessionCallback : MediaSessionCompat.Callback() {
        override fun onPlay() {
            mExoPlayer?.playWhenReady = true
        }

        override fun onPause() {
            mExoPlayer?.playWhenReady = false
        }

        override fun onSkipToPrevious() {
            mExoPlayer?.seekTo(0)
        }
    }

    /**
     * Broadcast Receiver registered to receive the MEDIA_BUTTON intent coming from clients.
     */
    class MediaReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            MediaButtonReceiver.handleIntent(mMediaSession, intent)
        }
    }

    companion object {
        private const val CORRECT_ANSWER_DELAY_MILLIS = 1000
        private const val REMAINING_SONGS_KEY = "remaining_songs"
        private val TAG = QuizActivity::class.java.simpleName
        private lateinit var mMediaSession: MediaSessionCompat
    }
}