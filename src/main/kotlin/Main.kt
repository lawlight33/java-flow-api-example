package com.example.demo.api

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Flow
import java.util.concurrent.Flow.Subscriber
import java.util.concurrent.SubmissionPublisher
import java.util.function.Function

private val latch = CountDownLatch(1)

private val publisher = SubmissionPublisher<Int>()
private val transformer = TransformProcessor(Int::toString)
private val subscriber = EndSubscriber<String>(latch)

fun main() {
    // subscribe
    publisher.subscribe(transformer)
    transformer.subscribe(subscriber)

    // publish and mark publishing finished
    (1..10).toList().forEach { publisher.submit(it) }
    publisher.close()

    // await, while asynchronous stream will fully be received by subscriber
    latch.await()
    transformer.close()
}

class TransformProcessor<T, R>(private val function: Function<T, R>) : SubmissionPublisher<R>(), Flow.Processor<T, R> {
    private lateinit var subscription: Flow.Subscription

    override fun onSubscribe(subscription: Flow.Subscription) {
        this.subscription = subscription
        // it is indented actual start to be there, otherwise subscription field might not be instantiated yet
        subscription.request(1)
    }

    override fun onError(throwable: Throwable) {
        println("error in processing! ${throwable.stackTraceToString()}")
    }

    override fun onComplete() {
        println("transformer was closed")
        close()
    }

    override fun onNext(item: T) {
        submit(function.apply(item))
        subscription.request(1)
    }
}

class EndSubscriber<T>(private val latch: CountDownLatch) : Subscriber<T> {
    private lateinit var subscription: Flow.Subscription

    override fun onSubscribe(subscription: Flow.Subscription) {
        println("new subscription: $subscription")
        this.subscription = subscription
        // it is indented actual start to be there, otherwise subscription field might not be instantiated yet
        subscription.request(5)
    }

    override fun onComplete() {
        println("subscriber was closed")
        latch.countDown()
    }

    override fun onError(throwable: Throwable) {
        println("error in processing! ${throwable.stackTraceToString()}")
    }

    override fun onNext(item: T) {
        println("New item: $item. type: ${item!!::class.simpleName}")
        subscription.request(1)
    }
}