package com.grantgz.example

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private val videoUrl = "https://file.yizhujiao.com/o_1cm4d1a1t1uicke618cb4k39013.mp4"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        mExoPlayerView.setDataSource(videoUrl)
    }
}
