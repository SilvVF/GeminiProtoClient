package ios.silv.gembrowse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer

@Serializable
sealed interface Route : NavKey

@Serializable
data object SettingsRoute : Route

@Serializable
data object HomeRoute : Route

@Serializable
data class TabRoute(val id: Long) : Route

@Serializable
data object HistoryRoute: Route

@Composable
inline fun <reified Item : Any> rememberStateStack(
    items: List<Item>,
    minSize: Int = 0
): SnapshotStateStack<Item> = rememberSerializable(serializer = snapshotStateStackSerializer()) {
    SnapshotStateStack(items, minSize)
}

public inline fun <reified T> snapshotStateStackSerializer(): SnapshotStateStackSerializer<T> {
    return SnapshotStateStackSerializer(serializer())
}

public class SnapshotStateStackSerializer<T>(elementSerializer: KSerializer<T>) :
    KSerializer<SnapshotStateStack<T>> {

    private val base = ListSerializer(elementSerializer)

    override val descriptor: SerialDescriptor =
        SerialDescriptor("ios.silv.gembrowse.SnapshotStateStack", base.descriptor)

    override fun serialize(encoder: Encoder, value: SnapshotStateStack<T>) {
        encoder.encodeSerializableValue(base, value.items)
    }

    override fun deserialize(decoder: Decoder): SnapshotStateStack<T> {
        val deserialized = decoder.decodeSerializableValue(base)
        return SnapshotStateStack<T>(deserialized.toList())
    }
}


public inline fun <reified I : Item, Item> Stack<Item>.popUntil(): Boolean =
    popUntil { item -> item is I }

public enum class StackEvent {
    Push,
    Replace,
    Pop,
    Idle
}

public interface Stack<Item> {

    public val items: List<Item>

    public val lastEvent: StackEvent

    public val lastItemOrNull: Item?

    public val size: Int

    public val isEmpty: Boolean

    public val canPop: Boolean

    public infix fun push(item: Item)

    public infix fun push(items: List<Item>)

    public infix fun replace(item: Item)

    public infix fun replaceAll(item: Item)

    public infix fun replaceAll(items: List<Item>)

    public fun pop(): Boolean

    public fun popAll()

    public infix fun popUntil(predicate: (Item) -> Boolean): Boolean

    public operator fun plusAssign(item: Item)

    public operator fun plusAssign(items: List<Item>)

    public fun clearEvent()
}


public class SnapshotStateStack<Item>(
    items: List<Item>,
    minSize: Int = 0
) : Stack<Item> {

    public constructor(
        vararg items: Item,
        minSize: Int = 0
    ) : this(
        items = items.toList(),
        minSize = minSize
    )

    init {
        require(minSize >= 0) { "Min size $minSize is less than zero" }
        require(items.size >= minSize) { "Stack size ${items.size} is less than the min size $minSize" }
    }

    @PublishedApi
    internal val stateStack: SnapshotStateList<Item> = items.toMutableStateList()

    public override var lastEvent: StackEvent by mutableStateOf(StackEvent.Idle, neverEqualPolicy())
        private set

    public override val items: List<Item> by derivedStateOf {
        stateStack.toList()
    }

    public override val lastItemOrNull: Item? by derivedStateOf {
        stateStack.lastOrNull()
    }

    public override val size: Int by derivedStateOf {
        stateStack.size
    }

    public override val isEmpty: Boolean by derivedStateOf {
        stateStack.isEmpty()
    }

    public override val canPop: Boolean by derivedStateOf {
        stateStack.size > minSize
    }

    public override infix fun push(item: Item) {
        stateStack += item
        lastEvent = StackEvent.Push
    }

    public override infix fun push(items: List<Item>) {
        stateStack += items
        lastEvent = StackEvent.Push
    }

    public override infix fun replace(item: Item) {
        if (stateStack.isEmpty()) {
            push(item)
        } else {
            stateStack[stateStack.lastIndex] = item
        }
        lastEvent = StackEvent.Replace
    }

    public override infix fun replaceAll(item: Item) {
        Snapshot.withMutableSnapshot {
            stateStack.clear()
            stateStack += item
            lastEvent = StackEvent.Replace
        }
    }

    public override infix fun replaceAll(items: List<Item>) {
        Snapshot.withMutableSnapshot {
            stateStack.clear()
            stateStack += items
            lastEvent = StackEvent.Replace
        }
    }

    public override fun pop(): Boolean =
        if (canPop) {
            stateStack.removeLast()
            lastEvent = StackEvent.Pop
            true
        } else {
            false
        }

    public override fun popAll() {
        popUntil { false }
    }

    public override infix fun popUntil(predicate: (Item) -> Boolean): Boolean {
        var success = false
        val shouldPop = {
            lastItemOrNull
                ?.let(predicate)
                ?.also { success = it }
                ?.not()
                ?: false
        }

        while (canPop && shouldPop()) {
            stateStack.removeLast()
        }

        lastEvent = StackEvent.Pop

        return success
    }

    public override operator fun plusAssign(item: Item) {
        push(item)
    }

    public override operator fun plusAssign(items: List<Item>) {
        push(items)
    }

    override fun clearEvent() {
        lastEvent = StackEvent.Idle
    }
}