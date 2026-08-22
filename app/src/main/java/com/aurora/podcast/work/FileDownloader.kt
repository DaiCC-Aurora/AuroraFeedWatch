package com.aurora.podcast.work

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

/**
 * OkHttp 下载器，支持断点续传（Range 头）。
 */
object FileDownloader {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 下载 URL 到 destFile（临时 .part 文件 + 重命名）。
     * 服务器忽略 Range（返回 200）时从头重写，返回 206 时续传。
     * @throws IOException 下载失败时抛出
     */
    suspend fun download(url: String, destFile: File) {
        withContext(Dispatchers.IO) {
            try {
                destFile.parentFile?.mkdirs()
                val tmp = File(destFile.parentFile, destFile.name + ".part")
                val existing = if (tmp.exists()) tmp.length() else 0L

                val requestBuilder = Request.Builder().url(url)
                if (existing > 0) requestBuilder.header("Range", "bytes=$existing-")
                val request = requestBuilder.build()

                client.newCall(request).execute().use { response ->
                    when {
                        response.code == 416 -> {
                            // Range 已超出文件大小：文件其实已完整，直接落盘
                            tmp.copyTo(destFile, overwrite = true)
                            tmp.delete()
                        }
                        response.isSuccessful -> {
                            val body = response.body
                                ?: throw IOException("下载失败 $url：空响应体")
                            val isPartial = response.code == 206
                            RandomAccessFile(tmp, "rw").use { raf ->
                                if (isPartial) raf.seek(existing) else raf.setLength(0)
                                body.byteStream().use { input ->
                                    val buf = ByteArray(8192)
                                    var n: Int
                                    while (input.read(buf).also { n = it } != -1) {
                                        raf.write(buf, 0, n)
                                    }
                                }
                            }
                            tmp.renameTo(destFile)
                        }
                        else -> throw IOException("下载失败 $url：HTTP ${response.code}")
                    }
                }
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                throw IOException("下载失败 $url：${e.message}", e)
            }
        }
    }
}