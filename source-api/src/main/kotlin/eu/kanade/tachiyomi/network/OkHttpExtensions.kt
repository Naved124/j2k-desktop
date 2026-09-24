package eu.kanade.tachiyomi.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import rx.Producer
import rx.Subscription
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

val jsonMime = "application/json; charset=utf-8".toMediaType()

@Deprecated("Use suspend APIs instead", ReplaceWith("await()"))
fun Call.asObservable(): Observable<Response> {
    return Observable.unsafeCreate { subscriber ->
        // A Call can only run once, so clone it for each subscriber.
        val call = clone()

        val requestArbiter = object : AtomicBoolean(), Producer, Subscription {
            override fun request(n: Long) {
                if (n == 0L || !compareAndSet(false, true)) return
                try {
                    val response = call.execute()
                    if (!subscriber.isUnsubscribed) {
                        subscriber.onNext(response)
                        subscriber.onCompleted()
                    }
                } catch (e: Exception) {
                    if (!subscriber.isUnsubscribed) subscriber.onError(e)
                }
            }

            override fun unsubscribe() {
                call.cancel()
            }

            override fun isUnsubscribed(): Boolean = call.isCanceled()
        }

        subscriber.add(requestArbiter)
        subscriber.setProducer(requestArbiter)
    }
}

@Suppress("DEPRECATION")
@Deprecated("Use suspend APIs instead", ReplaceWith("awaitSuccess()"))
fun Call.asObservableSuccess(): Observable<Response> =
    asObservable().doOnNext { response ->
        if (!response.isSuccessful) {
            response.close()
            throw HttpException(response.code)
        }
    }

suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }

        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}

/** @since extensions-lib 1.5 */
suspend fun Call.awaitSuccess(): Response {
    val response = await()
    if (!response.isSuccessful) {
        response.close()
        throw HttpException(response.code)
    }
    return response
}

fun OkHttpClient.newCachelessCallWithProgress(request: Request, listener: ProgressListener): Call {
    val progressClient = newBuilder()
        .cache(null)
        .addNetworkInterceptor { chain ->
            val originalResponse = chain.proceed(chain.request())
            originalResponse.newBuilder()
                .body(ProgressResponseBody(originalResponse.body, listener))
                .build()
        }
        .build()
    return progressClient.newCall(request)
}

/** @since extensions-lib 1.5 */
class HttpException(val code: Int) : IllegalStateException("HTTP error $code")