package ios.silv.gembrowse

/*
* MIT License
*
* Copyright (c) 2022 Shreyas Patil
*
* Permission is hereby granted, free of charge, to any person obtaining a copy
* of this software and associated documentation files (the "Software"), to deal
* in the Software without restriction, including without limitation the rights
* to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the Software is
* furnished to do so, subject to the following conditions:
*
* The above copyright notice and this permission notice shall be included in all
* copies or substantial portions of the Software.
*
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
* IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
* FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
* AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
* LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
* OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
* SOFTWARE.
*
*/
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawModifierNode
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.toBitmap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Controller for capturing [Composable] content.
 * @see dev.shreyaspatil.capturable.Capturable for implementation details.
 */
class CaptureController(internal val graphicsLayer: GraphicsLayer) {

    /**
     * Medium for providing capture requests
     *
     * Earlier, we were using `MutableSharedFlow` here but it was incapable of serving requests
     * which are created as soon as composition starts because this flow was collected later
     * underneath. So Channel with UNLIMITED capacity just works here and solves the issue as well.
     * See issue: https://github.com/PatilShreyas/Capturable/issues/202
     */
    @Suppress("ktlint")
    private val _captureRequests = Channel<CaptureRequest>(capacity = Channel.UNLIMITED)
    internal val captureRequests = _captureRequests.receiveAsFlow()

    /**
     * Creates and requests for a Bitmap capture with specified [config] and returns
     * an [ImageBitmap] asynchronously.
     *
     * This method is safe to be called from the "main" thread directly.
     *
     * Make sure to call this method as a part of callback function and not as a part of the
     * [Composable] function itself.
     *
     * @param config Bitmap config of the desired bitmap. Defaults to [Bitmap.Config.ARGB_8888]
     */
    fun captureAsync(): Deferred<ImageBitmap> {
        val deferredImageBitmap = CompletableDeferred<ImageBitmap>()
        return deferredImageBitmap.also {
            _captureRequests.trySend(CaptureRequest(imageBitmapDeferred = it))
        }
    }

    /**
     * Holds information of capture request
     */
    internal class CaptureRequest(val imageBitmapDeferred: CompletableDeferred<ImageBitmap>)
}

/**
 * Creates [CaptureController] and remembers it.
 */
@Composable
fun rememberCaptureController(): CaptureController {
    val graphicsLayer = rememberGraphicsLayer()
    return remember(graphicsLayer) { CaptureController(graphicsLayer) }
}

/**
 * Adds a capture-ability on the Composable which can draw Bitmap from the Composable component.
 *
 * Example usage:
 *
 * ```
 *  val captureController = rememberCaptureController()
 *  val uiScope = rememberCoroutineScope()
 *
 *  // The content to be captured in to Bitmap
 *  Column(
 *      modifier = Modifier.capturable(captureController),
 *  ) {
 *      // Composable content
 *  }
 *
 *  Button(onClick = {
 *      // Capture content
 *      val bitmapAsync = captureController.captureAsync()
 *      try {
 *          val bitmap = bitmapAsync.await()
 *          // Do something with `bitmap`.
 *      } catch (error: Throwable) {
 *          // Error occurred, do something.
 *      }
 *  }) { ... }
 * ```
 *
 * @param controller A [CaptureController] which gives control to capture the Composable content.
 */
fun Modifier.capturable(controller: CaptureController): Modifier {
    return this then CapturableModifierNodeElement(controller)
}

/**
 * Modifier implementation of Capturable
 */
private data class CapturableModifierNodeElement(
    private val controller: CaptureController
) : ModifierNodeElement<CapturableModifierNode>() {
    override fun create(): CapturableModifierNode {
        return CapturableModifierNode(controller)
    }

    override fun update(node: CapturableModifierNode) {
        node.updateController(controller)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "capturable"
        properties["controller"] = controller
    }
}

/**
 * Capturable Modifier node which delegates task to the [CacheDrawModifierNode] for drawing in
 * runtime when content capture is requested
 * [CacheDrawModifierNode] is used for drawing Composable UI from Canvas to the Picture and then
 * this node converts picture into a Bitmap.
 *
 * @param controller A [CaptureController] which gives control to capture the Composable content.
 */
@Suppress("unused")
private class CapturableModifierNode(
    controller: CaptureController
) : Modifier.Node(), DrawModifierNode {

    /**
     * State to hold the current [CaptureController] instance.
     * This can be updated via [updateController] method.
     */
    private val currentController = MutableStateFlow(controller)

    private val currentGraphicsLayer
        get() = currentController.value.graphicsLayer

    override fun onAttach() {
        super.onAttach()
        coroutineScope.launch {
            observeCaptureRequestsAndServe()
        }
    }

    /**
     * Sets new [CaptureController]
     */
    fun updateController(newController: CaptureController) {
        currentController.value = newController
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun observeCaptureRequestsAndServe() {
        currentController
            .flatMapLatest { it.captureRequests }
            .collect { request ->
                val completable = request.imageBitmapDeferred
                try {
                    completable.complete(currentGraphicsLayer.toImageBitmap())
                } catch (error: Throwable) {
                    completable.completeExceptionally(error)
                }
            }
    }

    // Ref:
    // https://developer.android.com/develop/ui/compose/graphics/draw/modifiers#composable-to-bitmap
    override fun ContentDrawScope.draw() {
        currentGraphicsLayer.record {
            // draw the contents of the composable into the graphics layer
            this@draw.drawContent()
        }
        // draw the graphics layer on the visible canvas
        drawLayer(currentGraphicsLayer)
    }
}