package seb.temporal.test.utils

import io.temporal.activity.ActivityExecutionContext

fun ActivityExecutionContext.info(): String =
    "${info.runId}/${info.workflowId}/${info.activityId}/${info.activityType}"
