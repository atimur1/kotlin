/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.kotlin.analyzer.EmptyResolverForProject
import org.jetbrains.kotlin.analyzer.ResolverForModule
import org.jetbrains.kotlin.context.GlobalContextImpl
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.firstNotNullResult

internal class ProjectResolutionFacade(
    private val debugString: String,
    private val resolverDebugName: String,
    val project: Project,
    val globalContext: GlobalContextImpl,
    val settings: PlatformAnalysisSettings,
    val reuseDataFrom: ProjectResolutionFacade?,
    val moduleFilter: (IdeaModuleInfo) -> Boolean,
    val dependencies: List<Any>,
    private val invalidateOnOOCB: Boolean = true,
    val syntheticFiles: Collection<KtFile> = listOf(),
    val allModules: Collection<IdeaModuleInfo>? = null // null means create resolvers for modules from idea model
) {
    private val cachedValue = CachedValuesManager.getManager(project).createCachedValue(
        {
            val resolverProvider = computeModuleResolverProvider()
            CachedValueProvider.Result.create(resolverProvider, resolverProvider.cacheDependencies)
        },
        /* trackValue = */ false
    )

    private fun computeModuleResolverProvider(): ModuleResolverProvider {
        val delegateResolverProvider = reuseDataFrom?.moduleResolverProvider
        val delegateResolverForProject = delegateResolverProvider?.resolverForProject ?: EmptyResolverForProject()
        return createModuleResolverProvider(
            resolverDebugName,
            project,
            globalContext,
            settings,
            syntheticFiles = syntheticFiles,
            delegateResolver = delegateResolverForProject, moduleFilter = moduleFilter,
            allModules = allModules,
            providedBuiltIns = delegateResolverProvider?.builtIns,
            dependencies = dependencies,
            invalidateOnOOCB = invalidateOnOOCB
        )
    }

    private val moduleResolverProvider: ModuleResolverProvider
        get() = globalContext.storageManager.compute { cachedValue.value }

    private val resolverForProject get() = moduleResolverProvider.resolverForProject

    private val fileAnalysisCache = FileAnalysisCache(project) { moduleResolverProvider }

    fun resolverForModuleInfo(moduleInfo: IdeaModuleInfo) = resolverForProject.resolverForModule(moduleInfo)

    fun resolverForElement(element: PsiElement): ResolverForModule {
        val infos = element.getModuleInfos()
        return infos.asIterable().firstNotNullResult { resolverForProject.tryGetResolverForModule(it) }
                ?: resolverForProject.tryGetResolverForModule(NotUnderContentRootModuleInfo)
                ?: resolverForProject.diagnoseUnknownModuleInfo(infos.toList())
    }

    fun resolverForDescriptor(moduleDescriptor: ModuleDescriptor) = resolverForProject.resolverForModuleDescriptor(moduleDescriptor)

    fun findModuleDescriptor(ideaModuleInfo: IdeaModuleInfo) = fileAnalysisCache.findModuleDescriptor(ideaModuleInfo)

    fun getAnalysisResultsForElements(elements: Collection<KtElement>) = fileAnalysisCache.getAnalysisResultsForElements(elements)

    override fun toString(): String {
        return "$debugString@${Integer.toHexString(hashCode())}"
    }
}
