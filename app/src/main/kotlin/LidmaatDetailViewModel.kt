package za.co.jpsoft.winkerkreader

import android.app.Application
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry

class LidmaatDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val _memberCursor = MutableLiveData<Cursor?>()
    val memberCursor: LiveData<Cursor?> = _memberCursor

    private val _familyCursor = MutableLiveData<Cursor?>()
    val familyCursor: LiveData<Cursor?> = _familyCursor

    private val contentResolver = application.contentResolver

    fun loadMember(memberUri: Uri) {
        val selection = """
            SELECT _rowid_ AS _id, * 
            FROM ${winkerkEntry.SELECTION_LIDMAAT_FROM}
            WHERE (${winkerkEntry.LIDMATE_TABLE_NAME}.${winkerkEntry.LIDMATE_REKORDSTATUS} = ${winkerkEntry.RECORDSTATUS})
            AND (${winkerkEntry.LIDMATE_TABLE_NAME}._rowid_ = ${memberUri.lastPathSegment})
        """.trimIndent()
        val cursor = contentResolver.query(
            memberUri,
            null,
            selection,
            null,
            null
        )
        _memberCursor.postValue(cursor)
    }

    fun loadFamily(familyHeadGuid: String) {
        val selection = """
            SELECT _rowid_ AS _id, ${winkerkEntry.LIDMATE_TABLE_NAME}.${winkerkEntry.LIDMATE_VAN},
                   ${winkerkEntry.LIDMATE_TABLE_NAME}.${winkerkEntry.LIDMATE_NOEMNAAM},
                   ${winkerkEntry.LIDMATE_TABLE_NAME}.${winkerkEntry.LIDMATE_GEBOORTEDATUM},
                   ${winkerkEntry.LIDMATE_TABLE_NAME}.${winkerkEntry.LIDMATE_PICTUREPATH}
            FROM ${winkerkEntry.LIDMATE_TABLE_NAME}
            WHERE (${winkerkEntry.LIDMATE_TABLE_NAME}.${winkerkEntry.LIDMATE_REKORDSTATUS} = ${winkerkEntry.RECORDSTATUS})
            AND (members.FamilyHeadGUID = "$familyHeadGuid")
            ORDER BY Gesinsrol ASC
        """.trimIndent()
        val uri = ContentUris.withAppendedId(winkerkEntry.CONTENT_GESIN_URI, 0L)
        val cursor = contentResolver.query(
            uri,
            null,
            selection,
            null,
            null
        )
        _familyCursor.postValue(cursor)
    }

    override fun onCleared() {
        _memberCursor.value?.close()
        _familyCursor.value?.close()
        super.onCleared()
    }
}