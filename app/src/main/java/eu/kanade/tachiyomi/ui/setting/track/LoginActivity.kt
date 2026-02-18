package eu.kanade.tachiyomi.ui.setting.track

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

abstract class BaseLoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}

class MyAnimeListLoginActivity : BaseLoginActivity()

class AnilistLoginActivity : BaseLoginActivity()

class ShikimoriLoginActivity : BaseLoginActivity()

class BangumiLoginActivity : BaseLoginActivity()
