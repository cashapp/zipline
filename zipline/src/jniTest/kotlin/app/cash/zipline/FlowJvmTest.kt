package app.cash.zipline

import app.cash.zipline.internal.bridge.EndpointEventListener
import app.cash.zipline.internal.bridge.allReferencesSet
import app.cash.zipline.internal.bridge.detectLeaks
import app.cash.zipline.testing.newEndpointPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector

class FlowJvmTest {
  class LeakListener : EndpointEventListener {

    val leaks = mutableListOf<String>()

    override fun serviceLeaked(name: String) {
      leaks += name
    }

    override fun bindService(name: String, service: ZiplineService) {
    }

    override fun takeService(name: String, service: ZiplineService) {
    }

    override fun callStart(call: Call): Any? = null

    override fun callEnd(call: Call, result: CallResult, startValue: Any?) {
    }
  }

  @[JvmField Rule]
  val errorCollector = ErrorCollector()

  @Test
  @Ignore("https://github.com/cashapp/zipline/issues/828")
  fun flowCollectionReportsZeroLeaks() = runBlocking(Dispatchers.Unconfined) {
    val scope = ZiplineScope()
    val listenerA = LeakListener()
    val listenerB = LeakListener()
    val (endpointA, endpointB) = newEndpointPair(this, listenerA = listenerA, listenerB = listenerB)
    val service = FlowTest.RealFlowEchoService()

    endpointA.bind<FlowTest.FlowEchoService>("service", service)
    val client = endpointB.take<FlowTest.FlowEchoService>("service", scope)

    val flow = client.createFlow("hello", 3)
    errorCollector.checkThat(flow.toList(), equalTo(listOf("0 hello", "1 hello", "2 hello")))

    // Confirm that no services or clients were leaked.
    scope.close()
    errorCollector.checkThat(endpointA.serviceNames, equalTo(emptySet()))
    errorCollector.checkThat(endpointA.clientNames, equalTo(emptySet()))

    // Confirm that no services were reported as leaking.
    for (reference in allReferencesSet) {
      check(reference.enqueue())
    }
    detectLeaks()
    errorCollector.checkThat("endpointA reported zero leaks", listenerA.leaks, equalTo(emptyList()))
    errorCollector.checkThat("endpointB reported zero leaks", listenerB.leaks, equalTo(emptyList()))
  }
}
