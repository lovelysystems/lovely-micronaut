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

    // allows to access SFTPExtension.sftp in tests
    lateinit var kafka: KraftKafkaContainer

    override fun <T : Spec> instantiate(clazz: KClass<T>): Spec? {
        clazz.findAnnotation<KafkaTest>()?.let { annotation ->
            kafka = KraftKafkaContainer(image = annotation.image).also {container ->
                container.start()
                System.setProperty("kafka.bootstrap.servers", container.bootstrapServers)
            }
        }
        return null
    }
}
