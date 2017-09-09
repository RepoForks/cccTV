package de.stefanmedack.ccctv.model

import android.os.Parcelable
import info.metadude.kotlin.library.c3media.models.Event
import kotlinx.android.parcel.Parcelize

@Parcelize
data class MiniEvent(
        val title: String,
        val subtitle: String,
        val description: String,
        val url: String,
        val posterUrl: String,
        val thumbUrl: String
) : Parcelable {
    object ModelMapper {
        fun from(from: Event?): MiniEvent = MiniEvent(
                title = from?.title ?: "",
                subtitle = from?.subtitle ?: "",
                description = from?.description ?: "",
                url = from?.url ?: "",
                posterUrl = from?.posterUrl ?: "",
                thumbUrl = from?.thumbUrl ?: "")
    }
}