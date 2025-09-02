package ls.testing

import io.kotest.core.extensions.ConstructorExtension
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

/**
 * Extension that starts Kafka server before test execution.
 * It is activated by [KafkaTest] annotation.
 */
object KafkaExtension : TestListener, ConstructorExtension {

    lateinit var kafka: KafkaContainer
        private set

    private val defaultEnvs = mapOf(
        "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR" to "1",
        "KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS" to "0",
        "KAFKA_TRANSACTION_STATE_LOG_MIN_ISR" to "1",
        "KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR" to "1",
        "KAFKA_LOG_RETENTION_MS" to "-1",
        "KAFKA_LOG_RETENTION_BYTES" to "-1",
        "KAFKA_MESSAGE_TIMESTAMP_TYPE" to "CreateTime",
        "KAFKA_DELETE_TOPIC_ENABLE" to "true",
        "KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR" to "1",
        "KAFKA_TRANSACTION_STATE_LOG_MIN_ISR" to "1",
        "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR" to "1",
        "KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS" to "0",
        "KAFKA_AUTO_CREATE_TOPICS_ENABLE" to "true"
    )

    override fun <T : Spec> instantiate(clazz: KClass<T>): Spec? {
        clazz.findAnnotation<KafkaTest>()?.let { annotation ->
            kafka = KafkaContainer(DockerImageName.parse(annotation.image)).apply {
                withEnv(defaultEnvs)
                start()
                System.setProperty("kafka.bootstrap.servers", bootstrapServers)
            }
        }
        return null
    }
}
