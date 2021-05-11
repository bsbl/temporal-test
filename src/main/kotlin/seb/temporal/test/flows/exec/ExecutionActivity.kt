package seb.temporal.test.flows.exec

import io.temporal.activity.ActivityInterface

@ActivityInterface
interface ExecutionActivity {

}

class ExecutionActivityImpl: ExecutionActivity
