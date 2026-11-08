package dev.hostyn.phone.ui.contacts

import dev.hostyn.phone.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material3.CircularProgressIndicator
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
import dev.hostyn.phone.ui.components.segmentShape

private fun ContactSummary.matches(q: String): Boolean {
    if (name.foldAccents().contains(q)) return true
    val digits = q.filter { it.isDigit() }
    return digits.length >= 3 && numbers.any { n -> n.filter { it.isDigit() }.contains(digits) }
}

private sealed interface ListRow {
    val key: Any
    data class Header(val letter: String) : ListRow { override val key = "h_$letter" }
    data class Item(val contact: ContactSummary, val first: Boolean, val last: Boolean) : ListRow { override val key = contact.id }
}

private fun groupRows(contacts: List<ContactSummary>): List<ListRow> {
    val rows = mutableListOf<ListRow>()
    contacts.groupBy { initialOf(it.name).foldAccents().uppercase().let { l -> if (l.first().isLetter()) l else "#" } }
        .forEach { (letter, group) ->
            rows += ListRow.Header(letter)
            group.forEachIndexed { i, c -> rows += ListRow.Item(c, i == 0, i == group.lastIndex) }
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
    val contacts by remember { repo.observeContacts() }.collectAsState(initial = null)
    val all = contacts
    val q = query.trim().foldAccents()
    val filtered = remember(all, q) { all?.let { list -> if (q.isEmpty()) list else list.filter { it.matches(q) } } }
    val favorites = remember(all) { all.orEmpty().filter { it.starred } }
    val rows = remember(filtered) { groupRows(filtered.orEmpty()) }

    if (all == null) {
        Box(Modifier.fillMaxSize().padding(contentPadding), Alignment.Center) { CircularProgressIndicator() }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 88.dp,
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
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.animateItem().padding(start = 32.dp, top = 16.dp, bottom = 8.dp),
                )
                is ListRow.Item -> ContactRow(row, onOpen, Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun CreateRow(onCreate: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onCreate)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PersonAdd, null, tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(stringResource(R.string.create_contact), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(84.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onOpen(c.id) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
            ) {
                ContactAvatar(c.name, c.photoUri, c.id, size = 60.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    c.name.substringBefore(' '),
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
    val shape = segmentShape(row.first, row.last)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = if (row.last) 0.dp else 2.dp)
            .fillMaxWidth()
            .clip(shape)
            .clickable { onOpen(row.contact.id) },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            ContactAvatar(row.contact.name, row.contact.photoUri, row.contact.id, size = 44.dp)
            Spacer(Modifier.width(16.dp))
            Text(
                row.contact.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
