/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.core.script

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.Result
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManager
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.isProjectOrWorkspaceFile
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.MergingUpdateQueue.ANY_COMPONENT
import com.intellij.util.ui.update.Update
import org.jetbrains.kotlin.script.ScriptDefinitionProvider
import org.jetbrains.plugins.gradle.service.project.GradleAutoImportAware

class ScriptModificationListener(
    private val project: Project,
    private val scriptDefinitionProvider: ScriptDefinitionProvider
) {
    private val changedDocuments = HashSet<Document>()
    private val changedDocumentsQueue = MergingUpdateQueue("ScriptModificationListener: Scripts queue", 1000, false, ANY_COMPONENT, project)

    init {
        project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener.Adapter() {
            val projectFileIndex = ProjectRootManager.getInstance(project).fileIndex
            val application = ApplicationManager.getApplication()

            override fun after(events: List<VFileEvent>) {
                if (application.isUnitTestMode) return

                val modifiedScripts = events.mapNotNull {
                    // The check is partly taken from the BuildManager.java
                    it.file?.takeIf {
                        // the isUnitTestMode check fixes ScriptConfigurationHighlighting & Navigation tests, since they are not trigger proper update mechanims
                        // TODO: find out the reason, then consider to fix tests and remove this check
                        (application.isUnitTestMode ||
                                scriptDefinitionProvider.isScript(it.name) && projectFileIndex.isInContent(it)) && !isProjectOrWorkspaceFile(
                            it
                        )
                    }
                }

                // Workaround for IDEA-182367 (fixed in IDEA 181.3666)
                if (modifiedScripts.isNotEmpty()) {
                    if (modifiedScripts.any {
                            GradleAutoImportAware().getAffectedExternalProjectPath(it.path, project) != null
                        }) {
                        return
                    }
                    ExternalProjectsManager.getInstance(project).externalProjectsWatcher.markDirty(project.basePath)
                }
            }
        })

        // partially copied from ExternalSystemProjectsWatcherImpl before fix will be implemented in IDEA:
        // "Gradle projects need to be imported" notification should be shown when kotlin script is modified
        val busConnection = project.messageBus.connect(changedDocumentsQueue)
        changedDocumentsQueue.activate()

        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (project.isDisposed) return

                val doc = event.document
                val virtualFile = FileDocumentManager.getInstance().getFile(doc) ?: return
                if (!ProjectRootManager.getInstance(project).fileIndex.isInContent(virtualFile)) return

                val scriptDef = ScriptDefinitionContributor.find<GradleScriptDefinitionsContributor>(project)?.getDefinitions() ?: return
                if (scriptDef.any { it.isScript(virtualFile.name) }) {
                    synchronized(changedDocuments) {
                        changedDocuments.add(doc)
                    }

                    changedDocumentsQueue.queue(object : Update(this) {
                        override fun run() {
                            var copy: Array<Document> = emptyArray()

                            synchronized(changedDocuments) {
                                copy = changedDocuments.toTypedArray()
                                changedDocuments.clear()
                            }

                            ExternalSystemUtil.invokeLater(project) {
                                object : WriteAction<Any>() {
                                    override fun run(result: Result<Any>) {
                                        for (each in copy) {
                                            PsiDocumentManager.getInstance(project).commitDocument(each)
                                            (FileDocumentManager.getInstance() as? FileDocumentManagerImpl)?.saveDocument(each, false)
                                        }
                                    }
                                }.execute()
                            }
                        }
                    })
                }
            }
        }, busConnection)
    }
}
