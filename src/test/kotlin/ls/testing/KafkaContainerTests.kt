package ls.testing

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

@KafkaTest("bitnami/kafka:3.4.1")
class KafkaContainerTests : StringSpec({

    val testTopic = "UNIT_TEST_TOPIC"

    "produce and consume two messages" {

        val producerProps = mapOf(
            "bootstrap.servers" to System.getProperty("kafka.bootstrap.servers"),
            "key.serializer" to "org.apache.kafka.common.serialization.StringSerializer",
            "value.serializer" to "org.apache.kafka.common.serialization.ByteArraySerializer",
            "security.protocol" to "PLAINTEXT",
        )
        val producer = KafkaProducer<String, ByteArray>(producerProps)
        producer.use {
            it.send(ProducerRecord(testTopic, "1", "Hello, world!".encodeToByteArray()))
            it.send(ProducerRecord(testTopic, "2", "Hello, world again!".encodeToByteArray()))
        }

        val consumerProps = mapOf(
            "bootstrap.servers" to System.getProperty("kafka.bootstrap.servers"),
            "auto.offset.reset" to "earliest",
            "key.deserializer" to "org.apache.kafka.common.serialization.StringDeserializer",
            "value.deserializer" to "org.apache.kafka.common.serialization.ByteArrayDeserializer",
            "security.protocol" to "PLAINTEXT",
            "group.id" to "someGroup"
        )

        val consumer = KafkaConsumer<String, ByteArray>(consumerProps)
        consumer.subscribe(listOf(testTopic))

        consumer.use {
            val messages = it.poll(6.seconds.toJavaDuration()).map { record -> String(record.value()) }
            messages shouldHaveSize 2
        }
    }
})
