package ls.testing

/**
 * Annotation that activates Kafka server via [KafkaExtension].
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class KafkaTest
