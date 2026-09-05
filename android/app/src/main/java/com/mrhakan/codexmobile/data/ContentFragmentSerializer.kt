package com.mrhakan.codexmobile.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * A message content part is either a bare string or `{content_type, text}`.
 * The backend mixes both shapes within one response, so both decode into
 * [ContentFragment].
 */
object ContentFragmentSerializer : KSerializer<ContentFragment> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.mrhakan.codexmobile.data.ContentFragment")

    override fun deserialize(decoder: Decoder): ContentFragment {
        val input = decoder as? JsonDecoder
            ?: return ContentFragment(text = decoder.decodeString())
        return when (val element = input.decodeJsonElement()) {
            is JsonPrimitive -> ContentFragment(
                contentType = "text",
                text = element.contentOrNullSafe(),
            )

            is JsonObject -> ContentFragment(
                contentType = element["content_type"]?.jsonPrimitive?.contentOrNullSafe(),
                text = element["text"]?.jsonPrimitive?.contentOrNullSafe(),
            )

            else -> ContentFragment()
        }
    }

    override fun serialize(encoder: Encoder, value: ContentFragment) {
        encoder.encodeString(value.text.orEmpty())
    }

    private fun JsonPrimitive.contentOrNullSafe(): String? = if (isString) content else null
}
