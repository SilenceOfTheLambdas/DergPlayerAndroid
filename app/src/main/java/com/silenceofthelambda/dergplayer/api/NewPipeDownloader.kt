package com.silenceofthelambda.dergplayer.api

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException

class NewPipeDownloader(private val client: OkHttpClient) : Downloader() {
    
    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    }

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBody = when {
            dataToSend != null -> dataToSend.toRequestBody()
            httpMethod == "POST" || httpMethod == "PUT" || httpMethod == "PATCH" -> "".toByteArray().toRequestBody()
            else -> null
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .method(httpMethod, requestBody)
            .header("User-Agent", USER_AGENT)

        headers?.forEach { (headerName, headerValues) ->
            if (headerValues.isNotEmpty()) {
                requestBuilder.header(headerName, headerValues[0])
            }
        }

        val response = client.newCall(requestBuilder.build()).execute()
        
        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha Challenge requested", url)
        }

        val responseCode = response.code
        val responseMessage = response.message
        val responseHeaders = response.headers.toMultimap()
        
        var responseBody: String? = null
        response.body?.let { body ->
            responseBody = body.string()
        }
        
        val latestUrl = response.request.url.toString()

        return Response(responseCode, responseMessage, responseHeaders, responseBody, latestUrl)
    }
}
