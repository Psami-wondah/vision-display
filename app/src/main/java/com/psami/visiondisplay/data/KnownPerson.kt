package com.psami.visiondisplay.data

data class KnownPerson(
    val id: String,
    val name: String,
    val embedding: FloatArray
)

data class FaceMatch(
    val person: KnownPerson,
    val similarity: Float
)