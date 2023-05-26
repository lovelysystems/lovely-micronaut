package ls.testing

import io.kotest.core.extensions.ConstructorExtension
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

/**
 * Extension that starts Kafka server before test execution.
 * It is activated by [KafkaTest] annotation.
 */
object KafkaExtension : TestListener, ConstructorExtension {
    val kafka: KraftKafkaContainer by lazy {
        KraftKafkaContainer().also {
            it.start()
            System.setProperty("kafka.bootstrap.servers", it.bootstrapServers)
        }
    }

    override fun <T : Spec> instantiate(clazz: KClass<T>): Spec? {
        clazz.findAnnotation<KafkaTest>()?.let {
            kafka   //so that the container is started and initialized
        }
        return null
    }
}
