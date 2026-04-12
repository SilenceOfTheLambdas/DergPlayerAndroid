package com.silenceofthelambda.dergplayer

import android.app.Application
import com.silenceofthelambda.dergplayer.api.NewPipeDownloader
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import java.io.File
import java.util.concurrent.TimeUnit

class DergPlayerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val cacheSize = 10 * 1024 * 1024L // 10 MB
        val cache = Cache(File(applicationContext.cacheDir, "http_cache"), cacheSize)

        val client = OkHttpClient.Builder()
            .cache(cache)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()
                chain.proceed(request)
            }
            .build()
            
        NewPipe.init(NewPipeDownloader(client))
    }
}
