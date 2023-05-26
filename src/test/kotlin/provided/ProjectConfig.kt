package provided

import io.kotest.core.config.AbstractProjectConfig
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension
import ls.testing.KafkaExtension

object ProjectConfig : AbstractProjectConfig() {
    /**
     * IMPORTANT: The order of extensions is important. [MicronautKotest5Extension] must be last since it
     * returns not null which would prevent other extensions from being called.
     */
    override fun extensions() = listOf(KafkaExtension, MicronautKotest5Extension)
}
