package ls.testing

/**
 * Annotation that activates Kafka server via [KafkaExtension].
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class KafkaTest(
    val image: String = "apache/kafka:3.9.1"
)
