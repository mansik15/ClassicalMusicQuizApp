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

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val highScoreTextView: TextView = findViewById(R.id.highscoreText)

        // Get the high and max score.
        val highScore = QuizUtils.getHighScore(this)
        val maxScore = Sample.getAllSampleIDs(this).size - 1

        // Set the high score text.
        val highScoreText: String = getString(R.string.high_score, highScore, maxScore)
        highScoreTextView.text = highScoreText

        // If the game is over, show the game finished UI.
        if (intent.hasExtra(GAME_FINISHED)) {
            val gameFinishedTextView: TextView = findViewById(R.id.gameResult)
            val yourScoreTextView: TextView = findViewById(R.id.resultScore)
            val yourScore = QuizUtils.getCurrentScore(this)
            val yourScoreText: String = getString(R.string.score_result, yourScore, maxScore)
            yourScoreTextView.text = yourScoreText
            gameFinishedTextView.visibility = View.VISIBLE
            yourScoreTextView.visibility = View.VISIBLE
        }
    }

    /**
     * The OnClick method for the New Game button that starts a new game.
     * @param view The New Game button.
     */
    fun newGame(view: View?) {
        val quizIntent = Intent(this, QuizActivity::class.java)
        startActivity(quizIntent)
    }

    companion object {
        private const val GAME_FINISHED = "game_finished"
    }
}