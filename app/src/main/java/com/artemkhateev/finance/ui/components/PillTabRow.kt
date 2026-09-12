package com.artemkhateev.finance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.artemkhateev.finance.ui.theme.FinanceTheme
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * Лента разделов: выбранный пункт всегда по центру. Боковые отступы в полэкрана
 * позволяют отцентровать даже крайние пункты.
 */
@Composable
fun PillTabRow(
    titles: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    val listState = rememberLazyListState()
    var centeredOnce by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = maxWidth / 2),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(titles) { index, title ->
                val selected = index == selectedIndex
                Text(
                    text = title,
                    style = typography.tab,
                    color = if (selected) colors.accent else colors.textInactive,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(CircleShape)
                        .then(
                            if (selected) {
                                Modifier.background(colors.pill).border(1.dp, colors.border, CircleShape)
                            } else {
                                Modifier
                            },
                        )
                        .clickable { onSelect(index) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }

    LaunchedEffect(selectedIndex) {
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == selectedIndex }) {
            listState.scrollToItem(selectedIndex)
        }
        val item = snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
        }.filterNotNull().first()
        val info = listState.layoutInfo
        val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
        val delta = (item.offset + item.size / 2 - viewportCenter).toFloat()
        // Первое центрирование без анимации: при запуске лента не должна ехать.
        if (centeredOnce) listState.animateScrollBy(delta) else listState.scrollBy(delta)
        centeredOnce = true
    }
}
