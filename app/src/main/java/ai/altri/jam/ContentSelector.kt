package ai.altri.jam

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

private const val TAG = "ContentSelector"
private const val SENTENCE_SPLIT_REGEX = "[.!?]\\s+"

class ContentSelector(private val context: Context) {
    private var currentJob: Job? = null

    fun cancelOngoing() {
        currentJob?.cancel()
        currentJob = null
    }

    private val textEmbedder = TextEmbedderHelper(context)

    /**
     * Splits text into sentences and returns most relevant ones based on query
     * @param fullContent The complete document content
     * @param query The text to compare against (e.g., user's questions)
     * @return Most relevant content that fits within token limit
     */
    suspend fun selectRelevantContent(
        fullContent: String, query: String, inferenceModel: InferenceModel
    ): String {
        currentJob?.cancel() // Cancel any previous operation
        return withContext(IO) {
            currentJob = coroutineContext[Job]
            try {
                // Split into sentences and clean up
                val sentences = fullContent.split(SENTENCE_SPLIT_REGEX.toRegex()).map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { "$it." } // Add back the period that was removed by split

                Log.d(TAG, "Found ${sentences.size} sentences, computing relevance...")
                // Get similarity scores for each sentence
                val scoredSentences = sentences.mapNotNull { sentence ->
                    textEmbedder.compare(sentence, query)?.let { result ->
                        ScoredSentence(sentence, result.similarity)
                    }
                }.sortedByDescending { it.score }
                Log.d(TAG, "Relevance computation complete for ${scoredSentences.size} sentences.")

                // Build relevant content by adding sentences until we hit the token limit
                val relevantContent = StringBuilder()
                var currentTokens = 0

                for (scored in scoredSentences) {
                    val sentenceTokens = inferenceModel.estimateTokenSize(scored.sentence)
                    val remainingTokens = inferenceModel.estimateTokensRemaining(scored.sentence)
                    if (currentTokens + sentenceTokens > remainingTokens) {
                        break
                    }

                    if (relevantContent.isNotEmpty()) {
                        relevantContent.append(" ")
                    }
                    relevantContent.append(scored.sentence)
                    currentTokens += sentenceTokens

                    Log.d(
                        TAG, "Added sentence with score ${scored.score}, "
                                + "tokens: $currentTokens/$remainingTokens, "
                                + "Sentence: ${scored.sentence}, "
                    )
                }

                relevantContent.toString()
            } finally {
                currentJob = null
            }
        }
    }

    private data class ScoredSentence(val sentence: String, val score: Double)

    fun cleanup() {
        textEmbedder.clearTextEmbedder()
    }
}
