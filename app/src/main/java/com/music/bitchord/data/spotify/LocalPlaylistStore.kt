package com.music.bitchord.data.spotify

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class LocalPlaylist(
    val id: String,
    val title: String,
    val songs: List<Song>,
    /** Absolute path of the cover picked by the user, inside the app's own storage. */
    val coverPath: String? = null,
) {
    val browseId: String get() = "local:playlist:$id"

    /** The cover as a URL the image loader understands; null when none was picked. */
    val coverUrl: String? get() = coverPath?.let { "file://$it" }

    /** What to draw for this playlist: the user's cover, else the first song's artwork. */
    val artworkUrl: String?
        get() = coverUrl ?: songs.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl
}

object LocalPlaylistStore {
    private const val PREF_NAME = "bitchord_local_playlists"
    private const val KEY_PLAYLISTS = "playlists_json"
    private const val COVERS_DIR = "playlist_covers"
    private const val COVER_SIDE_PX = 1024

    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context

    private val _playlists = MutableStateFlow<List<LocalPlaylist>>(emptyList())
    val playlists = _playlists.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        load()
    }

    private fun load() {
        if (!this::prefs.isInitialized) return
        val raw = prefs.getString(KEY_PLAYLISTS, null) ?: return
        runCatching {
            val list = mutableListOf<LocalPlaylist>()
            val jsonArray = JSONArray(raw)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                val title = obj.getString("title")
                val cover = obj.optString("coverPath", "").takeIf { it.isNotBlank() && it != "null" }
                    ?.takeIf { File(it).isFile }
                val songsArray = obj.getJSONArray("songs")
                val songs = mutableListOf<Song>()
                for (j in 0 until songsArray.length()) {
                    val sObj = songsArray.getJSONObject(j)
                    val song = Song(
                        videoId = sObj.optString("videoId", ""),
                        title = sObj.optString("title", ""),
                        artist = sObj.optString("artist", ""),
                        thumbnailUrl = sObj.optString("thumbnailUrl", null).takeIf { !it.isNullOrBlank() && it != "null" },
                        durationText = sObj.optString("durationText", null).takeIf { !it.isNullOrBlank() && it != "null" },
                        artistId = sObj.optString("artistId", null).takeIf { !it.isNullOrBlank() && it != "null" },
                        albumId = sObj.optString("albumId", null).takeIf { !it.isNullOrBlank() && it != "null" },
                        albumName = sObj.optString("albumName", null).takeIf { !it.isNullOrBlank() && it != "null" },
                        isVideo = sObj.optBoolean("isVideo", false),
                    )
                    songs.add(song)
                }
                list.add(LocalPlaylist(id, title, songs, cover))
            }
            _playlists.value = list
        }
    }

    /** Saves a playlist that already has its songs, as the Spotify import does. */
    fun savePlaylist(title: String, songs: List<Song>): LocalPlaylist =
        createPlaylist(title = title, songs = songs)

    /**
     * Creates a playlist on this device. It may start empty: songs are added
     * later from any song's menu.
     */
    fun createPlaylist(
        title: String,
        songs: List<Song> = emptyList(),
        coverPath: String? = null,
    ): LocalPlaylist {
        val id = "sp_local_" + System.currentTimeMillis()
        val playlist = LocalPlaylist(id = id, title = title.trim(), songs = songs, coverPath = coverPath)
        update { listOf(playlist) + it.filterNot { p -> p.id == id } }
        return playlist
    }

    fun deletePlaylist(id: String) {
        val target = getPlaylist(id)
        update { list ->
            list.filterNot { it.id == id || it.browseId == id || it.id == id.removePrefix("local:playlist:") }
        }
        target?.coverPath?.let { runCatching { File(it).delete() } }
    }

    fun renamePlaylist(id: String, newTitle: String) {
        val clean = newTitle.trim()
        if (clean.isEmpty()) return
        mutate(id) { it.copy(title = clean) }
    }

    /**
     * Adds [song] at the end. Returns false, and changes nothing, when the
     * playlist already holds that track or does not exist.
     */
    fun addSong(id: String, song: Song): Boolean {
        val playlist = getPlaylist(id) ?: return false
        if (playlist.songs.any { it.videoId == song.videoId }) return false
        mutate(id) { it.copy(songs = it.songs + song) }
        return true
    }

    fun removeSongAt(id: String, index: Int) {
        mutate(id) {
            if (index !in it.songs.indices) it else it.copy(songs = it.songs.filterIndexed { i, _ -> i != index })
        }
    }

    fun moveSong(id: String, from: Int, to: Int) {
        mutate(id) {
            if (from !in it.songs.indices || to !in it.songs.indices || from == to) {
                it
            } else {
                val songs = it.songs.toMutableList()
                songs.add(to, songs.removeAt(from))
                it.copy(songs = songs)
            }
        }
    }

    /** Points the playlist at a cover already written by [importCover], or clears it with null. */
    fun setCoverPath(id: String, path: String?) {
        val old = getPlaylist(id)?.coverPath
        mutate(id) { it.copy(coverPath = path) }
        if (old != null && old != path) runCatching { File(old).delete() }
    }

    /**
     * Copies the picture at [source] into the app's own storage as a square
     * JPEG and returns its path, or null if it could not be read.
     *
     * Copied rather than referenced: a gallery URI can be revoked or the photo
     * deleted, and the playlist must keep its cover regardless. A fresh file
     * name every time so the image loader never serves the previous cover.
     */
    suspend fun importCover(context: Context, source: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= COVER_SIDE_PX && bounds.outHeight / (sample * 2) >= COVER_SIDE_PX) {
                sample *= 2
            }
            val decoded = resolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null

            val rotation = resolver.openInputStream(source)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f

            val upright = if (rotation == 0f) {
                decoded
            } else {
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation) }, true)
            }
            val side = minOf(upright.width, upright.height)
            val square = Bitmap.createBitmap(upright, (upright.width - side) / 2, (upright.height - side) / 2, side, side)
            val scaled = if (side > COVER_SIDE_PX) Bitmap.createScaledBitmap(square, COVER_SIDE_PX, COVER_SIDE_PX, true) else square

            val dir = File(context.filesDir, COVERS_DIR).apply { mkdirs() }
            val out = File(dir, "cover_${System.currentTimeMillis()}.jpg")
            out.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            out.absolutePath
        }.getOrNull()
    }

    fun getPlaylist(id: String): LocalPlaylist? {
        val cleanId = id.removePrefix("local:playlist:").removePrefix("VL")
        return _playlists.value.firstOrNull { it.id == cleanId || it.id == id || it.browseId == id }
    }

    private fun mutate(id: String, change: (LocalPlaylist) -> LocalPlaylist) {
        val cleanId = id.removePrefix("local:playlist:").removePrefix("VL")
        update { list -> list.map { if (it.id == cleanId || it.id == id || it.browseId == id) change(it) else it } }
    }

    private fun update(change: (List<LocalPlaylist>) -> List<LocalPlaylist>) {
        val updated = change(_playlists.value)
        _playlists.value = updated
        persist(updated)
    }

    private fun persist(list: List<LocalPlaylist>) {
        if (!this::prefs.isInitialized) return
        runCatching {
            val jsonArray = JSONArray()
            for (playlist in list) {
                val pObj = JSONObject()
                pObj.put("id", playlist.id)
                pObj.put("title", playlist.title)
                pObj.put("coverPath", playlist.coverPath ?: "")
                val songsArray = JSONArray()
                for (song in playlist.songs) {
                    val sObj = JSONObject()
                    sObj.put("videoId", song.videoId)
                    sObj.put("title", song.title)
                    sObj.put("artist", song.artist)
                    sObj.put("thumbnailUrl", song.thumbnailUrl)
                    sObj.put("durationText", song.durationText)
                    sObj.put("artistId", song.artistId)
                    sObj.put("albumId", song.albumId)
                    sObj.put("albumName", song.albumName)
                    sObj.put("isVideo", song.isVideo)
                    songsArray.put(sObj)
                }
                pObj.put("songs", songsArray)
                jsonArray.put(pObj)
            }
            prefs.edit().putString(KEY_PLAYLISTS, jsonArray.toString()).apply()
        }
    }
}
