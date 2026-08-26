package com.example.localfirst.board

internal const val CircularTimeWheelItemCount: Int = Int.MAX_VALUE

internal fun circularTimeValue(index: Int, valueCount: Int): Int =
    Math.floorMod(index, valueCount)

internal fun circularTimeInitialIndex(valueCount: Int, selectedValue: Int): Int {
    val middle = CircularTimeWheelItemCount / 2
    return middle - Math.floorMod(middle, valueCount) + Math.floorMod(selectedValue, valueCount)
}

internal fun nearestCircularTimeIndex(
    currentIndex: Int,
    valueCount: Int,
    selectedValue: Int,
): Int {
    val currentValue = circularTimeValue(currentIndex, valueCount)
    val forward = Math.floorMod(selectedValue - currentValue, valueCount)
    val backward = forward - valueCount
    val offset = if (forward <= -backward) forward else backward
    return currentIndex + offset
}
