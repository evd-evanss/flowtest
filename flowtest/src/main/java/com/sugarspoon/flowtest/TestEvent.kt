package com.sugarspoon.flowtest

sealed class TestEvent<out T> {
    object Complete : TestEvent<Nothing>()
    data class Error(val throwable: Throwable) : TestEvent<Nothing>()
    data class Item<T>(val item: T) : TestEvent<T>()
}