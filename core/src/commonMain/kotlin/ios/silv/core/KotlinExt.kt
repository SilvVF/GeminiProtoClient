package ios.silv.core

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@Suppress("REDUNDANT_INLINE_SUSPEND_FUNCTION_TYPE")
@OptIn(ExperimentalContracts::class)
public suspend inline fun <T, R> T.let(block: suspend (T) -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return block(this)
}
