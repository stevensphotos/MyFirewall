package com.fw.myfirewall.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fw.myfirewall.data.AppDatabase
import com.fw.myfirewall.data.BlockListConfig
import com.fw.myfirewall.data.BlockListImporter
import com.fw.myfirewall.data.BlockRuleCategory
import com.fw.myfirewall.util.HostsGenerator
import kotlinx.coroutines.flow.first

class BlockListUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            Log.i("BlockListUpdateWorker", "Starting scheduled block list update")
            val db = AppDatabase.getDatabase(applicationContext)
            
            // Update Advertising if enabled and present
            val adCount = db.blockRuleDao().countByCategory(BlockRuleCategory.ADVERTISING).first()
            if (adCount > 0) {
                // Means it was enabled/downloaded previously.
                // We re-download to update.
                Log.i("BlockListUpdateWorker", "Updating Advertising list")
                BlockListImporter.importFromUrl(applicationContext, BlockListConfig.URL_ADVERTISING, BlockRuleCategory.ADVERTISING)
            }

            // Update Annoyances/Trackers
            val annoyCount = db.blockRuleDao().countByCategory(BlockRuleCategory.ANNOYANCES).first()
            if (annoyCount > 0) {
                Log.i("BlockListUpdateWorker", "Updating Annoyances list")
                BlockListImporter.importFromUrl(applicationContext, BlockListConfig.URL_ANNOYANCES, BlockRuleCategory.ANNOYANCES)
            }

            // Update Social
            val socialCount = db.blockRuleDao().countByCategory(BlockRuleCategory.SOCIAL_MEDIA).first()
            if (socialCount > 0) {
                Log.i("BlockListUpdateWorker", "Updating Social list")
                BlockListImporter.importFromUrl(applicationContext, BlockListConfig.URL_SOCIAL, BlockRuleCategory.SOCIAL_MEDIA)
            }

            // Regenerate hosts file after updates
            Log.i("BlockListUpdateWorker", "Regenerating hosts file")
            val hostsGenerator = HostsGenerator(db.blockRuleDao(), applicationContext)
            hostsGenerator.generateHostsFile()

            Log.i("BlockListUpdateWorker", "Block list update completed")
            Result.success()
        } catch (e: Exception) {
            Log.e("BlockListUpdateWorker", "Error updating block list", e)
            Result.retry()
        }
    }
}
