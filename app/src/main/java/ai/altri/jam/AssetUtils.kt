package ai.altri.jam

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object AssetUtils {
    private const val TAG = "AssetUtils"
    const val BUNDLED_MODEL_NAME = "bundled_model.task"

    /**
     * Copies a file from the assets folder to the app's internal storage
     * @param context Android context
     * @param assetName name of the asset file to copy
     * @param destFile destination file
     * @return true if successful, false otherwise
     */
    fun copyAssetToFile(context: Context, assetName: String, destFile: File): Boolean {
        try {
            if (destFile.exists()) {
                Log.d(TAG, "File already exists at ${destFile.absolutePath}")
                return true
            }

            // Check if the file size has changed
            val assetFileDescriptor = context.assets.openFd(assetName)
            val assetSize = assetFileDescriptor.length
            assetFileDescriptor.close()
            
            // Using a larger buffer for faster copying
            context.assets.open(assetName).use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    val buffer = ByteArray(8 * 1024 * 1024) // 8MB buffer for faster copying
                    var read: Int
                    var totalBytes = 0L
                    val startTime = System.currentTimeMillis()
                    
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                        totalBytes += read
                        
                        // Log progress for large files
                        if (assetSize > 100 * 1024 * 1024 && totalBytes % (50 * 1024 * 1024) < buffer.size) { // Log every ~50MB
                            val progress = (totalBytes * 100 / assetSize).toInt()
                            val elapsed = (System.currentTimeMillis() - startTime) / 1000
                            Log.d(TAG, "Extracting $assetName: $progress% ($totalBytes/$assetSize bytes), elapsed: ${elapsed}s")
                        }
                    }
                    
                    val totalTime = (System.currentTimeMillis() - startTime) / 1000
                    Log.d(TAG, "Extracted $assetName to ${destFile.absolutePath} in ${totalTime}s")
                    outputStream.flush()
                }
            }
            Log.d(TAG, "Successfully copied asset $assetName to ${destFile.absolutePath}")
            return true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy asset $assetName to ${destFile.absolutePath}", e)
            return false
        }
    }

    /**
     * Extracts the bundled model from assets to the app's internal storage if it doesn't exist
     * @param context Android context
     * @return File pointing to the extracted model, or null if extraction failed
     */
    fun extractBundledModel(context: Context): File? {
        val modelFile = File(context.filesDir, BUNDLED_MODEL_NAME)
        
        if (BuildConfig.USE_LOCAL_MODEL && BuildConfig.BUNDLED_MODEL_ASSET_NAME.isNotEmpty()) {
            val assetName = BuildConfig.BUNDLED_MODEL_ASSET_NAME
            if (copyAssetToFile(context, assetName, modelFile)) {
                return modelFile
            }
        }
        
        return null
    }
}