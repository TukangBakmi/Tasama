package com.example.tasama.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object FlexibleLongSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleLong", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Long) {
        encoder.encodeLong(value)
    }

    override fun deserialize(decoder: Decoder): Long {
        return try {
            decoder.decodeLong()
        } catch (e: Exception) {
            val fallback = try {
                decoder.decodeDouble().toLong()
            } catch (e2: Exception) {
                try {
                    decoder.decodeString().toLong()
                } catch (e3: Exception) {
                    null
                }
            }
            
            if (fallback != null) {
                println("DEBUG: [SERIALIZER] FlexibleLong fallback used: $fallback")
                fallback
            } else {
                println("ERROR: [SERIALIZER] FlexibleLong failed to decode value. Defaulting to 0L.")
                0L
            }
        }
    }
}
