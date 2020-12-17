package com.safetica.datasafe.model

import androidx.room.*

@Entity(tableName = "image")
data class Image (
        @PrimaryKey(autoGenerate = false)
        @ColumnInfo(name = "p")
        var path: String,
        @ColumnInfo(name = "ims")
        var imageSize: Long,
        @ColumnInfo(name = "lm")
        var lastModified: Long,
        @ColumnInfo(name="kb", typeAffinity = ColumnInfo.BLOB)
        var key: ByteArray?,
        @ColumnInfo(name="ib", typeAffinity = ColumnInfo.BLOB)
        var iv: ByteArray?) {

}