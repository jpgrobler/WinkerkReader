package za.co.jpsoft.winkerkreader.data.pastoral.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import za.co.jpsoft.winkerkreader.data.pastoral.entities.ReminderTemplateEntity
import za.co.jpsoft.winkerkreader.data.pastoral.entities.TemplateStepEntity
import za.co.jpsoft.winkerkreader.data.pastoral.model.TemplateWithSteps

@Dao
interface ReminderTemplateDao {

    @Transaction
    @Query("SELECT * FROM reminder_templates WHERE isActive = 1 ORDER BY sortOrder")
    fun observeTemplatesWithSteps(): Flow<List<TemplateWithSteps>>

    @Transaction
    @Query("SELECT * FROM reminder_templates WHERE isActive = 1 ORDER BY sortOrder")
    suspend fun getTemplatesWithSteps(): List<TemplateWithSteps>

    @Query("SELECT * FROM reminder_templates WHERE templateId = :templateId LIMIT 1")
    suspend fun getTemplateById(templateId: String): ReminderTemplateEntity?

    @Query("SELECT * FROM reminder_templates WHERE code = :code LIMIT 1")
    suspend fun getTemplateByCode(code: String): ReminderTemplateEntity?

    @Query("SELECT * FROM template_steps WHERE templateId = :templateId ORDER BY stepOrder")
    suspend fun getStepsForTemplate(templateId: String): List<TemplateStepEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: ReminderTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplates(templates: List<ReminderTemplateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSteps(steps: List<TemplateStepEntity>)

    @Update
    suspend fun updateTemplate(template: ReminderTemplateEntity)

    @Transaction
    @Query("SELECT * FROM reminder_templates ORDER BY isActive DESC, sortOrder")
    fun observeAllTemplatesWithSteps(): Flow<List<TemplateWithSteps>>

    @Query("UPDATE reminder_templates SET isActive = :isActive, updatedAt = :now WHERE templateId = :templateId")
    suspend fun setActive(templateId: String, isActive: Boolean, now: Long)

    /** Hard delete — only ever called for isSystem = false templates (enforced in repository). */
    @Query("DELETE FROM reminder_templates WHERE templateId = :templateId")
    suspend fun deleteTemplate(templateId: String)

    /** Cascades automatically via TemplateStepEntity's ForeignKey(onDelete = CASCADE). */
    @Query("DELETE FROM template_steps WHERE templateId = :templateId")
    suspend fun deleteAllStepsForTemplate(templateId: String)

    @Query("DELETE FROM template_steps WHERE stepId = :stepId")
    suspend fun deleteStep(stepId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStep(step: TemplateStepEntity)

    @Update
    suspend fun updateStep(step: TemplateStepEntity)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM reminder_templates")
    suspend fun nextTemplateSortOrder(): Int

    @Query("SELECT COALESCE(MAX(stepOrder), 0) + 1 FROM template_steps WHERE templateId = :templateId")
    suspend fun nextStepOrder(templateId: String): Int


}
