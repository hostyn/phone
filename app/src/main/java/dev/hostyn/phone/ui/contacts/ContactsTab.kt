@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.hostyn.phone.ui.contacts

import dev.hostyn.phone.data.NameOrder
import dev.hostyn.phone.PhoneApp
import androidx.compose.ui.platform.LocalContext
import dev.hostyn.phone.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hostyn.phone.data.ContactSummary
import dev.hostyn.phone.data.ContactsRepository
import dev.hostyn.phone.ui.components.ContactAvatar
import dev.hostyn.phone.ui.components.EmptyState
import dev.hostyn.phone.ui.components.foldAccents
import dev.hostyn.phone.ui.components.initialOf
import dev.hostyn.phone.ui.components.segmentedColors
import dev.hostyn.phone.ui.theme.LocalTransformOrigin
import dev.hostyn.phone.ui.theme.containerTransform
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.SegmentedListItem
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private fun ContactSummary.matches(q: String): Boolean {
    if (name.foldAccents().contains(q)) return true
    val digits = q.filter { it.isDigit() }
    return digits.length >= 3 && numbers.any { n -> n.filter { it.isDigit() }.contains(digits) }
}

private sealed interface ListRow {
    val key: Any
    data class Header(val letter: String) : ListRow { override val key = "h_$letter" }
    data class Item(val contact: ContactSummary, val index: Int, val count: Int) : ListRow { override val key = contact.id }
}

private fun groupRows(contacts: List<ContactSummary>): List<ListRow> {
    val rows = mutableListOf<ListRow>()
    contacts.groupBy { initialOf(it.sortKey).foldAccents().uppercase().let { l -> if (l.first().isLetter()) l else "#" } }
        .forEach { (letter, group) ->
            rows += ListRow.Header(letter)
            group.forEachIndexed { i, c -> rows += ListRow.Item(c, i, group.size) }
        }
    return rows
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactsTab(
    repo: ContactsRepository,
    query: String,
    contentPadding: PaddingValues,
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
    header: @Composable () -> Unit = {},
) {
    val contacts by repo.contacts.collectAsState()
    val all = contacts
    val q = query.trim().foldAccents()
    val filtered = remember(all, q) { all?.let { list -> if (q.isEmpty()) list else list.filter { it.matches(q) } } }
    val favorites = remember(all) { all.orEmpty().filter { it.starred } }
    val rows = remember(filtered) { groupRows(filtered.orEmpty()) }

    if (all == null) {
        Box(Modifier.fillMaxSize().padding(contentPadding), Alignment.Center) { LoadingIndicator() }
        return
    }

    val listState = rememberLazyListState()
    // Items the LazyColumn shows before the first row of [rows].
    val leading = when {
        q.isNotEmpty() -> if (rows.isEmpty()) 1 else 0
        else -> 3 + (if (favorites.isNotEmpty()) 2 else 0)
    }
    val sections = remember(rows) {
        rows.mapIndexedNotNull { i, r -> (r as? ListRow.Header)?.let { it.letter to i } }
    }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 112.dp,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (q.isEmpty()) {
            item(key = "header") { header() }
            item(key = "create") { CreateRow(onCreate) }
            if (favorites.isNotEmpty()) {
                item(key = "fav_title") { SectionTitle(stringResource(R.string.favorites)) }
                item(key = "fav_row") { FavoritesRow(favorites, onOpen) }
            }
            if (all.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        Icons.Rounded.PersonSearch,
                        stringResource(R.string.no_contacts_title),
                        stringResource(R.string.no_contacts_body),
                        Modifier.fillMaxWidth().padding(top = 48.dp),
                    )
                }
            } else {
                item(key = "all_title") { SectionTitle(stringResource(R.string.all_count, all.size), Modifier.padding(top = 8.dp)) }
            }
        } else if (rows.isEmpty()) {
            item(key = "no_results") {
                EmptyState(
                    Icons.Rounded.PersonSearch,
                    stringResource(R.string.no_results),
                    stringResource(R.string.no_results_body, query.trim()),
                    Modifier.fillMaxWidth().padding(top = 48.dp),
                )
            }
        }
        items(rows, key = { it.key }) { row ->
            when (row) {
                is ListRow.Header -> Text(
                    row.letter,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.animateItem().padding(start = 32.dp, top = 16.dp, bottom = 8.dp),
                )
                is ListRow.Item -> ContactRow(row, onOpen, Modifier.animateItem())
            }
        }
    }
    if (q.isEmpty() && sections.size > 1 && rows.size > 20) {
        FastScroller(
            sections = sections,
            leading = leading,
            listState = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding()),
        )
    }
    }
}

/** Strip of section letters on the right edge: tap or drag to jump to a letter. */
@Composable
private fun FastScroller(
    sections: List<Pair<String, Int>>,
    leading: Int,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var active by remember { mutableStateOf(false) }
    var selected by remember { mutableIntStateOf(-1) }
    val current by remember(sections, leading) {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex - leading
            sections.indexOfLast { it.second <= first }.coerceAtLeast(0)
        }
    }

    BoxWithConstraints(modifier) {
        val letterHeight = min(20.dp, maxHeight / sections.size)
        val letterPx = with(density) { letterHeight.toPx() }
        val shown = if (active && selected >= 0) selected else current

        Row(verticalAlignment = Alignment.Top) {
            // Bubble with the letter under the finger.
            Box(Modifier.width(72.dp).height(letterHeight * sections.size)) {
                if (active && selected >= 0) {
                    val y = with(density) { (letterPx * selected + letterPx / 2 - 28.dp.toPx()).toInt() }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp).offset { IntOffset(0, y) }.size(56.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                sections[selected].first,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(20.dp)
                    .clip(CircleShape)
                    .then(if (active) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest) else Modifier)
                    .pointerInput(sections, leading) {
                        fun select(y: Float) {
                            val i = (y / letterPx).toInt().coerceIn(0, sections.lastIndex)
                            if (i != selected) {
                                selected = i
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch { listState.scrollToItem(leading + sections[i].second) }
                            }
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            active = true
                            selected = -1
                            select(down.position.y)
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { c -> if (c.pressed) { select(c.position.y); c.consume() } }
                            } while (event.changes.any { it.pressed })
                            active = false
                        }
                    },
            ) {
                sections.forEachIndexed { i, (letter, _) ->
                    Box(Modifier.height(letterHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            letter,
                            fontSize = 11.sp,
                            fontWeight = if (i == shown) FontWeight.Bold else FontWeight.Medium,
                            color = if (i == shown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateRow(onCreate: () -> Unit) {
    SegmentedListItem(
        onClick = onCreate,
        shapes = ListItemDefaults.segmentedShapes(0, 1),
        colors = ListItemDefaults.segmentedColors(containerColor = Color.Transparent),
        leadingContent = {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PersonAdd, null, tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(stringResource(R.string.create_contact), color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 24.dp, top = 12.dp, bottom = 8.dp),
    )
}

@Composable
private fun FavoritesRow(favorites: List<ContactSummary>, onOpen: (Long) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(favorites, key = { it.id }) { c ->
            val origin = LocalTransformOrigin.current
            val key = "fav-${c.id}"
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(84.dp)
                    .containerTransform(key, MaterialTheme.shapes.largeIncreased)
                    .clip(MaterialTheme.shapes.largeIncreased)
                    .clickable { origin.key = key; onOpen(c.id) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
            ) {
                ContactAvatar(c.name, c.photoUri, c.id, size = 60.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    shortName(c.name, (LocalContext.current.applicationContext as PhoneApp).settings.nameFormat),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ContactRow(row: ListRow.Item, onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    val origin = LocalTransformOrigin.current
    // The contact's page grows out of this row (container transform).
    val key = "contact-${row.contact.id}"
    val shapes = ListItemDefaults.segmentedShapes(row.index, row.count)
    SegmentedListItem(
        onClick = { origin.key = key; onOpen(row.contact.id) },
        shapes = shapes,
        colors = segmentedColors(),
        leadingContent = { ContactAvatar(row.contact.name, row.contact.photoUri, row.contact.id, size = 40.dp) },
        modifier = modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = if (row.index == row.count - 1) 0.dp else ListItemDefaults.SegmentedGap)
            .containerTransform(key, shapes.shape),
    ) {
        Text(row.contact.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** First word of the given name, for the favorites row ("Díaz, Bruno" → "Bruno" when shown family name first). */
private fun shortName(name: String, format: NameOrder): String {
    val given = if (format == NameOrder.FamilyFirst && ", " in name) name.substringAfter(", ") else name
    return given.substringBefore(' ').removeSuffix(",")
}
