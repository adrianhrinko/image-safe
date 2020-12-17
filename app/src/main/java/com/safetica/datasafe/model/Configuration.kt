package com.safetica.datasafe.model

import androidx.room.*
import com.safetica.datasafe.constants.Constants

@Entity(tableName = "configuration")
data class Configuration(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "n")
    var name: String,
    @ColumnInfo(name = "d", typeAffinity = ColumnInfo.BLOB)
    var data: ByteArray
)