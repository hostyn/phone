package dev.hostyn.phone.ui.blocked

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.SegmentedListItem
import dev.hostyn.phone.ui.components.segmentedColors

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hostyn.phone.R
import dev.hostyn.phone.data.BlockedNumbersRepository
import dev.hostyn.phone.data.formatPhone
import dev.hostyn.phone.data.lookupNumber
import dev.hostyn.phone.ui.components.ContactAvatar
import dev.hostyn.phone.ui.components.EmptyState
import dev.hostyn.phone.ui.components.isDefaultDialer
import dev.hostyn.phone.ui.components.rememberDialerRoleRequest
import dev.hostyn.phone.ui.components.rememberOnResume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BlockedNumbersScreen(blocked: BlockedNumbersRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val canBlock = rememberOnResume { context.isDefaultDialer() && blocked.canBlock() }
    val requestRole = rememberDialerRoleRequest()
    val list by remember(canBlock) { blocked.observe() }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val unblockedMsg = stringResource(R.string.number_unblocked)
    val blockedMsg = stringResource(R.string.number_blocked)
    val errorMsg = stringResource(R.string.error_block)

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.blocked_numbers)) },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            if (canBlock) {
                MediumExtendedFloatingActionButton(
                    onClick = { adding = true },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text(stringResource(R.string.add_number)) },
                )
            }
        },
    ) { padding ->
        val items = list
        when {
            !canBlock -> EmptyState(
                Icons.Rounded.Block,
                stringResource(R.string.blocked_numbers),
                stringResource(R.string.blocking_need_default),
                Modifier.fillMaxSize().padding(padding),
            ) {
                Button(onClick = requestRole, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.set_default_phone_app)) }
            }
            items == null -> Box(Modifier.fillMaxSize())
            items.isEmpty() -> EmptyState(
                Icons.Rounded.Block,
                stringResource(R.string.no_blocked),
                stringResource(R.string.no_blocked_body),
                Modifier.fillMaxSize().padding(padding),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(items, key = { _, it -> it.id }) { i, item ->
                    val name by produceState<String?>(null, item.number) {
                        value = withContext(Dispatchers.IO) { context.contentResolver.lookupNumber(item.number)?.name }
                    }
                    val formatted = formatPhone(context, item.number)
                    SegmentedListItem(
                        shapes = ListItemDefaults.segmentedShapes(i, items.size),
                        colors = segmentedColors(),
                        leadingContent = { ContactAvatar(name.orEmpty(), null, item.number.hashCode().toLong(), size = 40.dp) },
                        supportingContent = if (name != null) ({ Text(formatted) }) else null,
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val ok = blocked.unblock(listOf(item.number))
                                        Toast.makeText(context, if (ok) unblockedMsg else errorMsg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(Icons.Rounded.Close, stringResource(R.string.unblock_number))
                            }
                        },
                        modifier = Modifier
                            .animateItem()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = if (i == items.lastIndex) 0.dp else ListItemDefaults.SegmentedGap),
                    ) {
                        Text(name ?: formatted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    if (adding) {
        var number by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            icon = { Icon(Icons.Rounded.Block, null) },
            title = { Text(stringResource(R.string.add_number)) },
            text = {
                OutlinedTextField(
                    value = number,
                    onValueChange = { v -> number = v.filter { it.isDigit() || it in "+*#" } },
                    label = { Text(stringResource(R.string.phone_number)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
            },
            confirmButton = {
                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    enabled = number.isNotBlank(),
                    onClick = {
                        adding = false
                        scope.launch {
                            val ok = blocked.block(listOf(number))
                            Toast.makeText(context, if (ok) blockedMsg else errorMsg, Toast.LENGTH_SHORT).show()
                        }
                    },
                ) { Text(stringResource(R.string.block)) }
            },
            dismissButton = { TextButton(onClick = { adding = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
