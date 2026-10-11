package smoke

import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

@Serializable data class CorePayload(val message: String)
@Serializable data class CoreNamedPayload(val number: Int) { companion object Factory }
@Serializable object CoreSingletonPayload

object CoreSerializationSmoke {
    @JvmStatic fun main(args: Array<String>) {
        check(serializer(CorePayload::class.java).descriptor.elementsCount == 1)
        check(serializer(CoreNamedPayload::class.java).descriptor.elementsCount == 1)
        check(serializer(CoreSingletonPayload::class.java).descriptor.elementsCount == 0)
        println("PASS serialization-core isolated")
    }
}
