package `in`.paanagam.business

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.paanagam.business.data.PaanagamDatabase
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileNotFoundException

/**
 * Migrates every exported schema version to the latest and lets Room validate tables,
 * columns, DEFAULT values and indices against the current entities. A failure here is the
 * kind of failure a real shop device would hit after an update.
 * Requires app/schemas/*.json to be committed (exportSchema = true) and room-testing.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "paanagam-migration-test"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PaanagamDatabase::class.java)

    @Test
    fun everyExportedSchemaMigratesToLatest() {
        var tested = 0
        for (from in 1 until PAANAGAM_DATABASE_SCHEMA_VERSION) {
            try {
                helper.createDatabase(dbName, from).close()
            } catch (_: FileNotFoundException) {
                continue // no exported JSON for this version
            }
            helper.runMigrationsAndValidate(dbName, PAANAGAM_DATABASE_SCHEMA_VERSION, true, *ALL_MIGRATIONS).close()
            InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(dbName)
            tested++
        }
        assertTrue("No exported schema JSON found in app/schemas.", tested > 0)
    }
}
