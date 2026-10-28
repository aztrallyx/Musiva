package com.music.bitchord.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.spotify.LocalPlaylist
import com.music.bitchord.data.spotify.LocalPlaylistStore
import com.music.bitchord.ui.icons.BitChordIcons
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * The playlists kept on this device: pick where a song goes, or make a new
 * playlist — name and cover photo — without needing any account.
 *
 * [song] is null when the sheet was opened from the Library tab, where
 * "New playlist" is the whole point. [onOpenAccountPlaylists] is non-null only
 * while signed in to YouTube Music, and swaps this sheet for the account's own.
 */
@Composable
fun LocalPlaylistPickerSheet(
    playlists: List<LocalPlaylist>,
    onAddTo: (LocalPlaylist) -> Unit,
    onCreate: (title: String, coverPath: String?) -> Unit,
    modifier: Modifier = Modifier,
    song: Song? = null,
    startCreating: Boolean = false,
    onOpenAccountPlaylists: (() -> Unit)? = null,
) {
    var creating by remember { mutableStateOf(startCreating) }

    if (creating) {
        NewLocalPlaylistForm(
            onBack = if (startCreating) null else ({ creating = false }),
            onCreate = onCreate,
            modifier = modifier,
        )
        return
    }

    Column(modifier.fillMaxWidth()) {
        if (song != null) {
            SheetTrackHeader(song)
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
        }
        SheetHeading(
            stringResource(if (song != null) R.string.add_to_playlist else R.string.your_playlists)
                .uppercase(Locale.getDefault()),
        )
        ActionRow(
            icon = BitChordIcons.Plus,
            label = stringResource(R.string.new_playlist),
            onClick = { creating = true },
        )
        onOpenAccountPlaylists?.let {
            ActionRow(
                icon = Icons.Rounded.MusicNote,
                label = stringResource(R.string.lp_account_playlists),
                onClick = it,
            )
        }
        if (playlists.isEmpty()) {
            Text(
                text = stringResource(R.string.no_playlists_yet),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
            )
        } else {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline,
            )
            // Capped so a long list scrolls inside the sheet instead of pushing it off screen.
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                itemsIndexed(playlists, key = { _, p -> p.id }) { _, playlist ->
                    LocalPlaylistRow(
                        playlist = playlist,
                        alreadyIn = song != null && playlist.songs.any { it.videoId == song.videoId },
                        onClick = { onAddTo(playlist) },
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LocalPlaylistRow(playlist: LocalPlaylist, alreadyIn: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverBox(url = playlist.artworkUrl.artworkAt(ROW_ART_PX), size = 44, corner = 7)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.local_playlist_subtitle, playlist.songs.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (alreadyIn) {
            Text(
                text = stringResource(R.string.lp_already_added),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A rounded square showing [url], or a note glyph while there is nothing to show. */
@Composable
private fun CoverBox(url: String?, size: Int, corner: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(corner.dp)
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size.dp),
            )
        } else {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size((size / 2).dp),
            )
        }
    }
}

/**
 * The big square on the forms: tap to choose a photo from the gallery.
 * [onPicked] receives the path of the square copy kept inside the app.
 */
@Composable
private fun CoverPicker(coverPath: String?, onPicked: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                LocalPlaylistStore.importCover(context, uri)?.let(onPicked)
            }
        }
    }
    val pick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    Box(
        modifier = modifier
            .size(132.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = pick),
        contentAlignment = Alignment.Center,
    ) {
        if (coverPath != null) {
            AsyncImage(
                model = "file://$coverPath",
                contentDescription = stringResource(R.string.lp_change_cover),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(132.dp),
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Rounded.AddPhotoAlternate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(34.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.lp_choose_cover),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A single-line text field in the style of the existing playlist forms. */
@Composable
private fun NameField(
    name: String,
    onNameChange: (String) -> Unit,
    onDone: () -> Unit,
    focusRequester: FocusRequester,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 16.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(11.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (name.isEmpty()) {
                Text(
                    text = stringResource(R.string.playlist_name),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = name,
                onValueChange = onNameChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }
    }
}

/** Name and cover photo for a playlist that lives on this device. */
@Composable
private fun NewLocalPlaylistForm(
    onBack: (() -> Unit)?,
    onCreate: (title: String, coverPath: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var coverPath by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val submit: () -> Unit = {
        if (name.isNotBlank()) {
            focusManager.clearFocus()
            onCreate(name, coverPath)
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (onBack != null) 8.dp else 22.dp, end = 22.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onBack?.let {
                IconButton(onClick = it) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            Text(
                text = stringResource(R.string.new_playlist),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
        Box(Modifier.fillMaxWidth().padding(top = 18.dp), contentAlignment = Alignment.Center) {
            CoverPicker(
                coverPath = coverPath,
                onPicked = { picked ->
                    // A second pick replaces the first; the first was never attached to anything.
                    coverPath?.let { runCatching { File(it).delete() } }
                    coverPath = picked
                },
            )
        }
        NameField(name = name, onNameChange = { name = it }, onDone = submit, focusRequester = focusRequester)
        Button(
            onClick = submit,
            enabled = name.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
        ) {
            Text(stringResource(R.string.lp_create))
        }
        Spacer(Modifier.height(28.dp))
    }
}

/**
 * Edits a playlist on this device: name, cover, and its songs (reorder or
 * remove). Everything applies as it is done; the name is saved on [onDone].
 */
@Composable
fun LocalPlaylistEditSheet(
    playlist: LocalPlaylist,
    onRename: (String) -> Unit,
    onCoverPicked: (String) -> Unit,
    onRemoveCover: () -> Unit,
    onMoveSong: (from: Int, to: Int) -> Unit,
    onRemoveSong: (index: Int) -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember(playlist.id) { mutableStateOf(playlist.title) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    val finish: () -> Unit = {
        focusManager.clearFocus()
        if (name.isNotBlank() && name.trim() != playlist.title) onRename(name)
        onDone()
    }

    Column(
        modifier
            .fillMaxWidth()
            .imePadding(),
    ) {
        Text(
            text = stringResource(R.string.lp_edit_playlist),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 8.dp),
        )
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)

        LazyColumn(Modifier.heightIn(max = 520.dp)) {
            item {
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().padding(top = 18.dp), contentAlignment = Alignment.Center) {
                        CoverPicker(coverPath = playlist.coverPath, onPicked = onCoverPicked)
                    }
                    if (playlist.coverPath != null) {
                        ActionRow(
                            icon = Icons.Rounded.Image,
                            label = stringResource(R.string.lp_remove_cover),
                            onClick = onRemoveCover,
                        )
                    }
                    NameField(
                        name = name,
                        onNameChange = { name = it },
                        onDone = finish,
                        focusRequester = focusRequester,
                    )
                    SheetHeading(stringResource(R.string.lp_songs).uppercase(Locale.getDefault()))
                    if (playlist.songs.isEmpty()) {
                        Text(
                            text = stringResource(R.string.lp_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                        )
                    }
                }
            }
            itemsIndexed(playlist.songs, key = { index, song -> "${song.videoId}-$index" }) { index, song ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 22.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverBox(url = song.thumbnailUrl.artworkAt(ROW_ART_PX), size = 40, corner = 6)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { onMoveSong(index, index - 1) }, enabled = index > 0) {
                        Icon(
                            Icons.Rounded.ArrowUpward,
                            contentDescription = stringResource(R.string.lp_move_up),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (index > 0) 1f else 0.3f),
                        )
                    }
                    IconButton(
                        onClick = { onMoveSong(index, index + 1) },
                        enabled = index < playlist.songs.lastIndex,
                    ) {
                        Icon(
                            Icons.Rounded.ArrowDownward,
                            contentDescription = stringResource(R.string.lp_move_down),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (index < playlist.songs.lastIndex) 1f else 0.3f,
                            ),
                        )
                    }
                    IconButton(onClick = { onRemoveSong(index) }) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.lp_remove_song),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(8.dp))
                    if (confirmingDelete) {
                        ActionRow(
                            icon = Icons.Rounded.DeleteForever,
                            label = stringResource(R.string.lp_delete_confirm, playlist.title),
                            tint = MaterialTheme.colorScheme.error,
                            onClick = onDelete,
                        )
                    } else {
                        ActionRow(
                            icon = Icons.Rounded.Delete,
                            label = stringResource(R.string.lp_delete_playlist),
                            onClick = { confirmingDelete = true },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = finish,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
        ) {
            Text(stringResource(R.string.lp_done))
        }
        Spacer(Modifier.height(24.dp))
    }
}
