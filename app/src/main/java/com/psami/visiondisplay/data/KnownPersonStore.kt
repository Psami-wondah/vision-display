package com.psami.visiondisplay.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KnownPersonStore(
    context: Context
) {

    private val file =
        File(
            context.filesDir,
            "known_people.bin"
        )

    @Synchronized
    fun load():
            List<KnownPerson> {

        if (
            !file.exists()
        ) {
            return emptyList()
        }

        return runCatching {

            val plain =
                decrypt(
                    file.readBytes()
                )

            val array =
                JSONArray(
                    String(
                        plain,
                        Charsets.UTF_8
                    )
                )

            buildList {

                for (
                index in
                0 until array.length()
                ) {

                    val item =
                        array.getJSONObject(
                            index
                        )

                    val embeddingJson =
                        item.getJSONArray(
                            "embedding"
                        )

                    val embedding =
                        FloatArray(
                            embeddingJson.length()
                        ) {
                                valueIndex ->

                            embeddingJson
                                .getDouble(
                                    valueIndex
                                )
                                .toFloat()
                        }

                    add(
                        KnownPerson(
                            id =
                                item.getString(
                                    "id"
                                ),

                            name =
                                item.getString(
                                    "name"
                                ),

                            embedding =
                                embedding
                        )
                    )
                }
            }

        }.getOrDefault(
            emptyList()
        )
    }

    @Synchronized
    fun save(
        people: List<KnownPerson>
    ) {

        val json =
            JSONArray()

        people.forEach {
                person ->

            val embedding =
                JSONArray()

            person.embedding
                .forEach {
                        value ->

                    embedding.put(
                        value.toDouble()
                    )
                }

            json.put(
                JSONObject()
                    .put(
                        "id",
                        person.id
                    )
                    .put(
                        "name",
                        person.name
                    )
                    .put(
                        "embedding",
                        embedding
                    )
            )
        }

        val encrypted =
            encrypt(
                json
                    .toString()
                    .toByteArray(
                        Charsets.UTF_8
                    )
            )

        file.writeBytes(
            encrypted
        )
    }

    private fun encrypt(
        input: ByteArray
    ): ByteArray {

        val cipher =
            Cipher.getInstance(
                TRANSFORMATION
            )

        cipher.init(
            Cipher.ENCRYPT_MODE,
            secretKey()
        )

        val encrypted =
            cipher.doFinal(
                input
            )

        val iv =
            cipher.iv

        return ByteBuffer
            .allocate(
                Int.SIZE_BYTES +
                        iv.size +
                        encrypted.size
            )
            .apply {

                putInt(
                    iv.size
                )

                put(
                    iv
                )

                put(
                    encrypted
                )
            }
            .array()
    }

    private fun decrypt(
        input: ByteArray
    ): ByteArray {

        val buffer =
            ByteBuffer.wrap(
                input
            )

        val ivLength =
            buffer.int

        require(
            ivLength in 12..32
        )

        val iv =
            ByteArray(
                ivLength
            )

        buffer.get(
            iv
        )

        val encrypted =
            ByteArray(
                buffer.remaining()
            )

        buffer.get(
            encrypted
        )

        val cipher =
            Cipher.getInstance(
                TRANSFORMATION
            )

        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(
                128,
                iv
            )
        )

        return cipher.doFinal(
            encrypted
        )
    }

    private fun secretKey():
            SecretKey {

        val keyStore =
            KeyStore.getInstance(
                "AndroidKeyStore"
            ).apply {
                load(null)
            }

        val existing =
            keyStore.getKey(
                KEY_ALIAS,
                null
            )

        if (
            existing is
                    SecretKey
        ) {
            return existing
        }

        val generator =
            KeyGenerator.getInstance(
                KeyProperties
                    .KEY_ALGORITHM_AES,

                "AndroidKeyStore"
            )

        generator.init(
            KeyGenParameterSpec
                .Builder(
                    KEY_ALIAS,

                    KeyProperties
                        .PURPOSE_ENCRYPT or
                            KeyProperties
                                .PURPOSE_DECRYPT
                )
                .setBlockModes(
                    KeyProperties
                        .BLOCK_MODE_GCM
                )
                .setEncryptionPaddings(
                    KeyProperties
                        .ENCRYPTION_PADDING_NONE
                )
                .build()
        )

        return generator
            .generateKey()
    }

    private companion object {

        const val KEY_ALIAS =
            "vision_display_faces"

        const val TRANSFORMATION =
            "AES/GCM/NoPadding"
    }
}