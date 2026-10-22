package com.radiowave.app

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/** Long-press dragging shared by the phone and tablet station lists. */
@Stable
internal class FavoriteReorderState(val listState: LazyListState) {
    var draggedId by mutableStateOf<String?>(null)
        private set
    private var centerY by mutableFloatStateOf(0f)
    var pointerY by mutableFloatStateOf(0f)
        private set

    fun start(y: Float, stationIds: List<String>) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            y >= it.offset && y < it.offset + it.size && it.key in stationIds
        } ?: return
        draggedId = item.key as String
        centerY = item.offset + item.size / 2f
        pointerY = y
    }

    fun drag(delta: Float) {
        pointerY += delta
        val layout = listState.layoutInfo
        centerY = (centerY + delta).coerceIn(
            layout.viewportStartOffset.toFloat(), layout.viewportEndOffset.toFloat())
    }

    fun stop() { draggedId = null }

    fun moveIfNeeded(stationIds: List<String>, onMove: (String, String) -> Unit) {
        val id = draggedId ?: return
        val items = listState.layoutInfo.visibleItemsInfo
        val current = items.firstOrNull { it.key == id } ?: return
        // Wait for the layout to reflect the previous move before moving again.
        if (stationIds.indexOf(id) != current.index) return
        val target = items.filter {
            it.key != id && it.key in stationIds &&
                (if (it.index > current.index) centerY > it.offset + it.size / 2f
                    else centerY < it.offset + it.size / 2f)
        }.minByOrNull { kotlin.math.abs(centerY - it.offset - it.size / 2f) } ?: return
        if (current.index == listState.firstVisibleItemIndex || target.index == listState.firstVisibleItemIndex) {
            listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
        onMove(id, target.key as String)
    }

    fun itemModifier(id: String): Modifier = Modifier
        .zIndex(if (draggedId == id) 1f else 0f)
        .graphicsLayer {
            val dragging = draggedId == id
            val item = if (dragging) listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } else null
            translationY = if (item != null) centerY - item.offset - item.size / 2f else 0f
            shadowElevation = if (dragging) 8.dp.toPx() else 0f
        }
}

@Composable
internal fun rememberFavoriteReorderState(): FavoriteReorderState {
    val listState = rememberLazyListState()
    return remember(listState) { FavoriteReorderState(listState) }
}

@Composable
internal fun favoriteReorderModifier(
    state: FavoriteReorderState,
    enabled: Boolean,
    stationIds: List<String>,
    onMove: (String, String) -> Unit
): Modifier {
    val currentIds by rememberUpdatedState(stationIds)
    val move by rememberUpdatedState(onMove)
    val edge = with(LocalDensity.current) { 64.dp.toPx() }
    LaunchedEffect(state, enabled, state.draggedId) {
        if (!enabled) state.stop()
        var previousFrame = withFrameNanos { it }
        while (enabled && state.draggedId != null) {
            val frame = withFrameNanos { it }
            val seconds = ((frame - previousFrame) / 1_000_000_000f).coerceAtMost(0.05f)
            previousFrame = frame
            val layout = state.listState.layoutInfo
            val top = layout.viewportStartOffset + edge
            val bottom = layout.viewportEndOffset - edge
            val speed = when {
                state.pointerY < top -> -((top - state.pointerY) / edge).coerceIn(0f, 1f)
                state.pointerY > bottom -> ((state.pointerY - bottom) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (speed != 0f) state.listState.scrollBy(speed * edge * 10f * seconds)
            state.moveIfNeeded(currentIds, move)
        }
    }
    return if (!enabled) Modifier else Modifier.pointerInput(state) {
        detectDragGesturesAfterLongPress(
            onDragStart = { state.start(it.y, currentIds) },
            onDragEnd = { state.stop() },
            onDragCancel = { state.stop() },
            onDrag = { change, amount ->
                if (state.draggedId != null) {
                    change.consume()
                    state.drag(amount.y)
                    state.moveIfNeeded(currentIds, move)
                }
            }
        )
    }
}
