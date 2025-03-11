package ios.silv.core_android

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

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

public class StateFlowStack<Item: Any>(
    items: List<Item>,
    private val minSize: Int = 0
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

    private val _stateStack = MutableStateFlowList(items.toList())

    fun asStateFlow() = _stateStack.asStateFlow()
    val stateStack get() = _stateStack.value

    private val _lastEvent = MutableStateFlow(StackEvent.Idle)
    val lastEventFlow get() = _lastEvent.asStateFlow()

    override val lastEvent get() = _lastEvent.value

    override val items: List<Item> get() = _stateStack.value
    override val lastItemOrNull: Item? get() = _stateStack.value.lastOrNull()
    val lastItemOrNullFlow: Flow<Item?> get() = _stateStack.map { it.lastOrNull() }

    override val size: Int get() = _stateStack.value.size
    override val isEmpty: Boolean get() = _stateStack.value.isEmpty()
    override val canPop: Boolean get() = _stateStack.value.size > minSize

    override infix fun push(item: Item) {
        _stateStack.update { it + item }
        _lastEvent.value = StackEvent.Push
    }

    override infix fun push(items: List<Item>) {
        _stateStack.update { it + items }
        _lastEvent.value = StackEvent.Push
    }

    override infix fun replace(item: Item) {
        _stateStack.update { current ->
            if (current.isEmpty()) listOf(item) else current.dropLast(1) + item
        }
        _lastEvent.value = StackEvent.Replace
    }

    override infix fun replaceAll(item: Item) {
        _stateStack.value = listOf(item)
        _lastEvent.value = StackEvent.Replace
    }

    override infix fun replaceAll(items: List<Item>) {
        _stateStack.value = items
        _lastEvent.value = StackEvent.Replace
    }

    override fun pop(): Boolean {
        if (!canPop) return false
        _stateStack.update { it.dropLast(1) }
        _lastEvent.value = StackEvent.Pop
        return true
    }

    override fun popAll() {
        popUntil { false }
    }

    override infix fun popUntil(predicate: (Item) -> Boolean): Boolean {
        var success = false
        _stateStack.update { stack ->
            val newStack = stack.toMutableList()
            while (newStack.size > minSize && (newStack.lastOrNull()?.let(predicate)?.not() == true)) {
                newStack.removeAt(newStack.lastIndex)
            }
            success = newStack.lastOrNull()?.let(predicate) ?: false
            newStack
        }
        _lastEvent.value = StackEvent.Pop
        return success
    }

    override operator fun plusAssign(item: Item) {
        push(item)
    }

    override operator fun plusAssign(items: List<Item>) {
        push(items)
    }

    override fun clearEvent() {
        _lastEvent.value = StackEvent.Idle
    }
}
