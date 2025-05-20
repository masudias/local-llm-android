package ai.altri.jam

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.common.util.concurrent.ListenableFuture
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.LlmInferenceSessionOptions
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import java.io.File
import kotlin.math.max

/** The maximum number of tokens the model can process. */
var MAX_TOKENS = 1024

/**
 * An offset in tokens that we use to ensure that the model always has the ability to respond when
 * we compute the remaining context length.
 */
var DECODE_TOKEN_OFFSET = 256

class ModelLoadFailException :
    Exception("Failed to load model, please try again")

class ModelSessionCreateFailException :
    Exception("Failed to create model session, please try again")

class InferenceModel private constructor(context: Context) {
    private lateinit var llmInference: LlmInference
    private lateinit var llmInferenceSession: LlmInferenceSession

    val uiState = UiState(model.thinking)

    init {
        if (!modelExists(context)) {
            throw IllegalArgumentException("Model not found at path: ${model.path}")
        }

        createEngine(context)
        createSession()
    }

    fun close() {
        llmInferenceSession.close()
        llmInference.close()
    }

    fun resetSession() {
        llmInferenceSession.close()
        createSession()
    }

    private fun createEngine(context: Context) {
        val inferenceOptions = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath(context))
            .setMaxTokens(MAX_TOKENS)
            .apply { model.preferredBackend?.let { setPreferredBackend(it) } }
            .build()

        try {
            llmInference = LlmInference.createFromOptions(context, inferenceOptions)
        } catch (e: Exception) {
            Log.e(TAG, "Load model error: ${e.message}", e)
            throw ModelLoadFailException()
        }
    }

    private fun createSession() {
        val sessionOptions = LlmInferenceSessionOptions.builder()
            .setTemperature(model.temperature)
            .setTopK(model.topK)
            .setTopP(model.topP)
            .build()

        try {
            llmInferenceSession =
                LlmInferenceSession.createFromOptions(llmInference, sessionOptions)
        } catch (e: Exception) {
            Log.e(TAG, "LlmInferenceSession create error: ${e.message}", e)
            throw ModelSessionCreateFailException()
        }
    }

    fun generateResponseAsync(
        prompt: String,
        progressListener: ProgressListener<String>
    ): ListenableFuture<String> {
        llmInferenceSession.addQueryChunk(prompt)
        return llmInferenceSession.generateResponseAsync(progressListener)
    }

    fun estimateTokensRemaining(prompt: String): Int {
        val context = uiState.messages.joinToString { it.rawMessage } + prompt
        if (context.isEmpty()) return -1

        val sizeOfAllMessages = llmInferenceSession.sizeInTokens(context)
        val approximateControlTokens = uiState.messages.size * 3
        val remainingTokens =
            MAX_TOKENS - sizeOfAllMessages - approximateControlTokens - DECODE_TOKEN_OFFSET
        // Token size is approximate so, let's not return anything below 0
        return max(0, remainingTokens)
    }

    fun estimateTokenSize(sentences: String): Int {
        if (sentences.isEmpty()) return -1
        val sizeOfAllMessages = llmInferenceSession.sizeInTokens(sentences)
        // Token size is approximate so, let's not return anything below 0
        return max(0, sizeOfAllMessages)
    }

    companion object {
        private const val TAG = "InferenceModel"

        // Model will be set either by the SelectionScreen or from configuration
        var model: Model = Model.GEMMA_3_1B_IT_GPU  // Default model
        private var instance: InferenceModel? = null

        // Check for local model configuration
        init {
            if (BuildConfig.USE_LOCAL_MODEL) {
                // When using LOCAL model config, use the bundled model from assets
                model = Model.LOCAL_MODEL

                // Set the model path to point to the bundled model in app's internal storage
                // This will be copied from assets when needed
                model.path = AssetUtils.BUNDLED_MODEL_NAME
            }
        }

        fun getInstance(context: Context): InferenceModel {
            return if (instance != null) {
                instance!!
            } else {
                InferenceModel(context).also { instance = it }
            }
        }

        fun resetInstance(context: Context): InferenceModel {
            return InferenceModel(context).also { instance = it }
        }

        fun modelPathFromUrl(context: Context): String {
            // For LOCAL model, try to find it by filename in the app's files directory
            if (model == Model.LOCAL_MODEL) {
                // Extract the filename from the path, which will be the same as what was downloaded
                val fileName = File(model.path).name
                val localFile = File(context.filesDir, fileName)
                if (localFile.exists()) {
                    return localFile.absolutePath
                }
            }

            // For other models with a URL
            if (model.url.isNotEmpty()) {
                val urlFileName = Uri.parse(model.url).lastPathSegment
                if (!urlFileName.isNullOrEmpty()) {
                    return File(context.filesDir, urlFileName).absolutePath
                }
            }

            return ""
        }

        fun modelPath(context: Context): String {
            // For LOCAL model with USE_LOCAL_MODEL enabled, try to extract from assets
            if (model == Model.LOCAL_MODEL && BuildConfig.USE_LOCAL_MODEL) {
                // Try to extract the bundled model
                val bundledModelFile = AssetUtils.extractBundledModel(context)
                if (bundledModelFile != null && bundledModelFile.exists()) {
                    return bundledModelFile.absolutePath
                }
            }

            // First, check if the model file exists at the exact path specified
            val modelFile = File(model.path)
            if (modelFile.exists()) {
                return model.path
            }

            // If not found at the exact path, try to find it in the app's files directory
            val appDirPath = modelPathFromUrl(context)
            if (appDirPath.isNotEmpty()) {
                val appDirFile = File(appDirPath)
                if (appDirFile.exists()) {
                    return appDirPath
                }
            }

            // If we still don't have a valid path, return the original path
            // This will cause a "Model not found" error with the correct path in the message
            return model.path
        }

        fun modelExists(context: Context): Boolean {
            // For LOCAL model with USE_LOCAL_MODEL enabled, check if bundled model exists in assets first
            if (model == Model.LOCAL_MODEL && BuildConfig.USE_LOCAL_MODEL &&
                BuildConfig.BUNDLED_MODEL_ASSET_NAME.isNotEmpty()
            ) {
                try {
                    // Check if the asset exists
                    context.assets.open(BuildConfig.BUNDLED_MODEL_ASSET_NAME).close()
                    return true
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "Bundled model asset not found: ${BuildConfig.BUNDLED_MODEL_ASSET_NAME}",
                        e
                    )
                }
            }

            return File(modelPath(context)).exists()
        }
    }
}
