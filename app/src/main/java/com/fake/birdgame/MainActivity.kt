package com.fake.birdgame
import android.app.Activity
import android.os.Bundle

/** Hosts the custom Canvas-based game view defined in activity_main.xml. */
class MainActivity : Activity() {

private lateinit var gameView: BirdGameView
    override fun onCreate(savedInstanceState: Bundle?) {
                 super.onCreate(savedInstanceState)
                 // Inflate the XML that hosts our single custom drawing surface.
                 setContentView(R.layout.activity_main)
                 // Keep the View reference so lifecycle callbacks can pause and clean up timers.
                 gameView = findViewById(R.id.birdieGameView)
             }

         // Do not allow a countdown to continue while another app is covering this one.
         override fun onPause() {
                 if (::gameView.isInitialized) gameView.pauseForBackground()
                 super.onPause()
             }

         // Release the Timer when Android destroys this Activity.
         override fun onDestroy() {
                 if (::gameView.isInitialized) gameView.release()
                 super.onDestroy()
             }
}
