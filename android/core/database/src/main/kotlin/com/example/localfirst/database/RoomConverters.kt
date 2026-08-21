package com.example.localfirst.database

import androidx.room.TypeConverter
import com.example.localfirst.sync.OperationState
import com.example.localfirst.sync.OperationType
import com.example.localfirst.sync.TaskStatus

class RoomConverters {
    @TypeConverter
    fun taskStatusToString(value: TaskStatus?): String? = value?.name

    @TypeConverter
    fun stringToTaskStatus(value: String?): TaskStatus? = value?.let(TaskStatus::valueOf)

    @TypeConverter
    fun operationTypeToString(value: OperationType?): String? = value?.name

    @TypeConverter
    fun stringToOperationType(value: String?): OperationType? = value?.let(OperationType::valueOf)

    @TypeConverter
    fun operationStateToString(value: OperationState?): String? = value?.name

    @TypeConverter
    fun stringToOperationState(value: String?): OperationState? = value?.let(OperationState::valueOf)
}
