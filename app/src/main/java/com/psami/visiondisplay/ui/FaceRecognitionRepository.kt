package com.psami.visiondisplay.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.psami.visiondisplay.data.FaceMatch
import com.psami.visiondisplay.data.KnownPerson
import com.psami.visiondisplay.data.KnownPersonStore
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

class FaceRecognitionRepository(
    context: Context
) : AutoCloseable {

    private val store =
        KnownPersonStore(
            context
        )

    private val embedder =
        FaceEmbeddingProcessor(
            context
        )

    @Volatile
    private var people =
        store.load()

    fun listPeople():
            List<KnownPerson> =
        people

    fun createEmbedding(
        frame: Bitmap,
        boundingBox: Rect
    ): FloatArray {

        val crop =
            cropFace(
                frame,
                boundingBox
            )

        return try {
            embedder.embed(
                crop
            )
        } finally {
            crop.recycle()
        }
    }

    fun recognize(
        frame: Bitmap,
        result: FaceDetectionResult
    ): FaceDetectionResult {

        if (
            people.isEmpty()
        ) {
            return result
        }

        val recognised =
            result.faces.map {
                    face ->

                /*
                 * Extreme poses are more likely
                 * to produce unreliable matches.
                 */
                if (
                    abs(
                        face.eulerY
                    ) > 35f ||
                    abs(
                        face.eulerX
                    ) > 30f
                ) {
                    face
                } else {

                    val embedding =
                        createEmbedding(
                            frame,
                            face.boundingBox
                        )

                    val match =
                        findBestMatch(
                            embedding
                        )

                    if (
                        match == null
                    ) {
                        face
                    } else {
                        face.copy(
                            recognizedName =
                                match
                                    .person
                                    .name,

                            similarity =
                                match
                                    .similarity
                        )
                    }
                }
            }

        return result.copy(
            faces =
                recognised
        )
    }

    @Synchronized
    fun enroll(
        name: String,
        samples:
        List<FloatArray>
    ): KnownPerson {

        require(
            samples.isNotEmpty()
        )

        val average =
            averageEmbeddings(
                samples
            )

        /*
         * Re-enrolling the same name updates
         * its existing template instead of
         * creating duplicates.
         */
        val existing =
            people.firstOrNull {
                it.name.equals(
                    name,
                    ignoreCase = true
                )
            }

        val person =
            KnownPerson(
                id =
                    existing?.id
                        ?: UUID
                            .randomUUID()
                            .toString(),

                name =
                    name.trim(),

                embedding =
                    average
            )

        people =
            people
                .filterNot {
                    it.id ==
                            person.id
                } +
                    person

        store.save(
            people
        )

        return person
    }

    @Synchronized
    fun delete(
        personId: String
    ) {
        people =
            people.filterNot {
                it.id ==
                        personId
            }

        store.save(
            people
        )
    }

    private fun findBestMatch(
        embedding: FloatArray
    ): FaceMatch? {

        val best =
            people
                .map {
                        person ->

                    FaceMatch(
                        person =
                            person,

                        similarity =
                            cosineSimilarity(
                                embedding,
                                person.embedding
                            )
                    )
                }
                .maxByOrNull {
                    it.similarity
                }
                ?: return null

        return best.takeIf {
            it.similarity >=
                    MATCH_THRESHOLD
        }
    }

    private fun averageEmbeddings(
        samples:
        List<FloatArray>
    ): FloatArray {

        val dimensions =
            samples.first()
                .size

        val average =
            FloatArray(
                dimensions
            )

        samples.forEach {
                sample ->

            require(
                sample.size ==
                        dimensions
            )

            for (
            index in
            0 until dimensions
            ) {
                average[index] +=
                    sample[index]
            }
        }

        for (
        index in
        average.indices
        ) {
            average[index] /=
                samples.size
        }

        var magnitude =
            0f

        average.forEach {
                value ->

            magnitude +=
                value *
                        value
        }

        magnitude =
            kotlin.math.sqrt(
                magnitude
            )

        if (
            magnitude > 1e-6f
        ) {
            for (
            index in
            average.indices
            ) {
                average[index] /=
                    magnitude
            }
        }

        return average
    }

    private fun cosineSimilarity(
        first: FloatArray,
        second: FloatArray
    ): Float {

        if (
            first.size !=
            second.size
        ) {
            return -1f
        }

        /*
         * Both embeddings are L2-normalised,
         * so cosine similarity is simply
         * their dot product.
         */
        var score =
            0f

        for (
        index in
        first.indices
        ) {
            score +=
                first[index] *
                        second[index]
        }

        return score
    }

    private fun cropFace(
        frame: Bitmap,
        box: Rect
    ): Bitmap {

        val horizontalMargin =
            box.width() *
                    0.18f

        val verticalMargin =
            box.height() *
                    0.18f

        val left =
            (
                    box.left -
                            horizontalMargin
                    )
                .roundToInt()
                .coerceIn(
                    0,
                    frame.width - 1
                )

        val top =
            (
                    box.top -
                            verticalMargin
                    )
                .roundToInt()
                .coerceIn(
                    0,
                    frame.height - 1
                )

        val right =
            (
                    box.right +
                            horizontalMargin
                    )
                .roundToInt()
                .coerceIn(
                    left + 1,
                    frame.width
                )

        val bottom =
            (
                    box.bottom +
                            verticalMargin
                    )
                .roundToInt()
                .coerceIn(
                    top + 1,
                    frame.height
                )

        return Bitmap.createBitmap(
            frame,
            left,
            top,
            right - left,
            bottom - top
        )
    }

    override fun close() {
        embedder.close()
    }

    companion object {

        /*
         * Starting value only.
         *
         * We WILL calibrate this using
         * your actual device/testing data.
         */
        const val MATCH_THRESHOLD =
            0.65f
    }
}