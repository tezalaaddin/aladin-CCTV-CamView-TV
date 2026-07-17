package com.aladin.aladincamviewer

import android.os.Parcel
import android.os.Parcelable

/**
 * Data class representing a CCTV camera with its RTSP stream URLs and PTZ info.
 */
data class CameraModel(
    val name: String,
    val mainStreamUrl: String,
    val subStreamUrl: String,
    val ipAddress: String = "",
    val ptzSupported: Boolean = false,
    val username: String = "",
    val password: String = "",
    val brand: String = "Custom"
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readByte() != 0.toByte(),
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "Custom"
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeString(mainStreamUrl)
        parcel.writeString(subStreamUrl)
        parcel.writeString(ipAddress)
        parcel.writeByte(if (ptzSupported) 1 else 0)
        parcel.writeString(username)
        parcel.writeString(password)
        parcel.writeString(brand)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<CameraModel> {
        override fun createFromParcel(parcel: Parcel): CameraModel = CameraModel(parcel)
        override fun newArray(size: Int): Array<CameraModel?> = arrayOfNulls(size)
    }
}
