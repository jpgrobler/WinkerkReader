package za.co.jpsoft.winkerkreader.data.pastoral.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.data.pastoral.PastoralDatabase
import za.co.jpsoft.winkerkreader.data.pastoral.PastoralDatabaseInitializer
import za.co.jpsoft.winkerkreader.data.pastoral.entities.ReminderTemplateEntity
import za.co.jpsoft.winkerkreader.data.pastoral.entities.TemplateStepEntity
import za.co.jpsoft.winkerkreader.data.pastoral.model.ScheduleType
import za.co.jpsoft.winkerkreader.data.pastoral.model.TemplateWithSteps
import java.util.UUID

/**
 * Manages reminder templates and their steps.
 */
class TemplateRepository(
    private val database: PastoralDatabase
) {
    private val templateDao = database.reminderTemplateDao()

    fun observeTemplates(): Flow<List<TemplateWithSteps>> =
        templateDao.observeTemplatesWithSteps().flowOn(Dispatchers.IO)

    fun observeAllTemplates(): Flow<List<TemplateWithSteps>> =
        templateDao.observeAllTemplatesWithSteps().flowOn(Dispatchers.IO)

    suspend fun createTemplate(titleAf: String, descriptionAf: String?): String =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val templateId = "custom-${UUID.randomUUID()}"
            templateDao.insertTemplate(
                ReminderTemplateEntity(
                    templateId = templateId,
                    code = templateId,
                    titleAf = titleAf.trim(),
                    descriptionAf = descriptionAf?.trim()?.ifBlank { null },
                    isSystem = false,
                    isActive = true,
                    sortOrder = templateDao.nextTemplateSortOrder(),
                    createdAt = now,
                    updatedAt = now
                )
            )
            templateId
        }

    suspend fun updateTemplateMeta(
        templateId: String,
        titleAf: String,
        descriptionAf: String?,
        symbol: String?
    ) = withContext(Dispatchers.IO) {
        val template = templateDao.getTemplateById(templateId)
            ?: throw IllegalArgumentException("Template not found: $templateId")
        templateDao.updateTemplate(
            template.copy(
                titleAf = titleAf.trim(),
                descriptionAf = descriptionAf?.trim()?.ifBlank { null },
                symbol = symbol?.trim()?.ifBlank { null },
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun setTemplateActive(templateId: String, isActive: Boolean) =
        withContext(Dispatchers.IO) {
            templateDao.setActive(templateId, isActive, System.currentTimeMillis())
        }

    suspend fun deleteTemplatePermanently(templateId: String) =
        withContext(Dispatchers.IO) {
            val template = templateDao.getTemplateById(templateId) ?: return@withContext
            check(!template.isSystem) {
                "System templates cannot be permanently deleted — use setTemplateActive(false) instead"
            }
            templateDao.deleteTemplate(templateId)
        }

    suspend fun resetTemplateToDefault(templateId: String) =
        withContext(Dispatchers.IO) {
            val template = templateDao.getTemplateById(templateId)
                ?: throw IllegalArgumentException("Template not found: $templateId")
            check(template.isSystem) { "resetTemplateToDefault is only valid for system templates" }
            val now = System.currentTimeMillis()
            val originalSteps = PastoralDatabaseInitializer.originalStepsFor(template.code, now)
                ?: throw IllegalStateException("No original definition found for code ${template.code}")
            templateDao.deleteAllStepsForTemplate(templateId)
            originalSteps.forEach { templateDao.insertStep(it) }
        }

    suspend fun addStep(
        templateId: String,
        offsetDays: Int,
        offsetMonths: Int,
        defaultTitleAf: String,
        defaultNoteAf: String?,
        scheduleType: ScheduleType,
        defaultHour: Int? = 8,
        defaultMinute: Int? = 0
    ): String = withContext(Dispatchers.IO) {
        val stepId = UUID.randomUUID().toString()
        templateDao.insertStep(
            TemplateStepEntity(
                stepId = stepId,
                templateId = templateId,
                stepOrder = templateDao.nextStepOrder(templateId),
                offsetDays = offsetDays,
                offsetMonths = offsetMonths,
                defaultTitleAf = defaultTitleAf.trim(),
                defaultNoteAf = defaultNoteAf?.trim()?.ifBlank { null },
                scheduleType = scheduleType.name,
                defaultHour = defaultHour,
                defaultMinute = defaultMinute
            )
        )
        stepId
    }

    suspend fun updateStep(step: TemplateStepEntity) =
        withContext(Dispatchers.IO) {
            templateDao.updateStep(step)
        }

    suspend fun deleteStep(stepId: String) =
        withContext(Dispatchers.IO) {
            templateDao.deleteStep(stepId)
        }

    suspend fun reorderSteps(orderedSteps: List<TemplateStepEntity>) =
        withContext(Dispatchers.IO) {
            orderedSteps.forEachIndexed { index, step ->
                templateDao.updateStep(step.copy(stepOrder = index + 1))
            }
        }

    suspend fun getTemplateWithSteps(templateId: String): TemplateWithSteps? =
        withContext(Dispatchers.IO) {
            val template = templateDao.getTemplateById(templateId) ?: return@withContext null
            val steps = templateDao.getStepsForTemplate(templateId)
            TemplateWithSteps(template, steps)
        }

    suspend fun ensureSystemTemplates() =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val existingTemplates = templateDao.getTemplatesWithSteps()
            val existingIds = existingTemplates.map { it.template.templateId }.toSet()
            val allSystemTemplates = PastoralDatabaseInitializer.buildSystemTemplates(now)
            val missing = allSystemTemplates.filter { it.template.templateId !in existingIds }
            if (missing.isNotEmpty()) {
                missing.forEach { seed ->
                    templateDao.insertTemplate(seed.template)
                    templateDao.insertSteps(seed.steps)
                }
            }
        }
}