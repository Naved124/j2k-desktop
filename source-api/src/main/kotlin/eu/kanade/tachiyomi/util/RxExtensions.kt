package eu.kanade.tachiyomi.util

import kotlinx.coroutines.suspendCancellableCoroutine
import rx.Observable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Bridges old RxJava extensions into coroutines: wait for exactly one value. */
suspend fun <T> Observable<T>.awaitSingle(): T =
    suspendCancellableCoroutine { cont ->
        val subscription = single().subscribe(
            { value -> cont.resume(value) },
            { error -> cont.resumeWithException(error) },
        )
        cont.invokeOnCancellation { subscription.unsubscribe() }
    }