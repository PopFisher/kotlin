/*
 * Copyright 2010-2015 JetBrains s.r.o.
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

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.util.isValidOperator
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.resolvedCallUtil.getImplicitReceiverValue


abstract class ExclExclCallFix(psiElement: PsiElement) : KotlinQuickFixAction<PsiElement>(psiElement) {
    override fun getFamilyName(): String = text

    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile) = file is KtFile
}

class RemoveExclExclCallFix(psiElement: PsiElement) : ExclExclCallFix(psiElement), CleanupFix {
    override fun getText(): String = KotlinBundle.message("remove.unnecessary.non.null.assertion")

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean
        = super.isAvailable(project, editor, file) && getExclExclPostfixExpression() != null

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        if (!FileModificationService.getInstance().prepareFileForWrite(file)) return

        val postfixExpression = getExclExclPostfixExpression() ?: return
        val expression = KtPsiFactory(project).createExpression(postfixExpression.baseExpression!!.text)
        postfixExpression.replace(expression)
    }

    private fun getExclExclPostfixExpression(): KtPostfixExpression? {
        val operationParent = element?.parent
        if (operationParent is KtPostfixExpression && operationParent.baseExpression != null) {
            return operationParent
        }
        return null
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction
            = RemoveExclExclCallFix(diagnostic.psiElement)
    }
}

class AddExclExclCallFix(psiElement: PsiElement, val checkImplicitReceivers: Boolean) : ExclExclCallFix(psiElement) {
    constructor(psiElement: PsiElement) : this(psiElement, true)

    override fun getText() = KotlinBundle.message("introduce.non.null.assertion")

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean
            = super.isAvailable(project, editor, file) &&
              getExpressionForIntroduceCall() != null

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        if (!FileModificationService.getInstance().prepareFileForWrite(file)) return

        val expr = getExpressionForIntroduceCall() ?: return
        val modifiedExpression = expr.expression
        val exclExclExpression = if (expr.implicitReceiver) {
            KtPsiFactory(project).createExpressionByPattern("this!!.$0", modifiedExpression)
        } else {
            KtPsiFactory(project).createExpressionByPattern("$0!!", modifiedExpression)
        }
        modifiedExpression.replace(exclExclExpression)
    }

    private class ExpressionForCall(val expression: KtExpression, val implicitReceiver: Boolean)

    private fun KtExpression?.expressionForCall(implicitReceiver: Boolean = false) = this?.let { ExpressionForCall(it, implicitReceiver) }

    private fun getExpressionForIntroduceCall(): ExpressionForCall? {
        val psiElement = element ?: return null
        return if (psiElement is LeafPsiElement && psiElement.elementType == KtTokens.DOT) {
            (psiElement.prevSibling as? KtExpression).expressionForCall()
        }
        else if (psiElement is KtArrayAccessExpression) {
            psiElement.arrayExpression.expressionForCall()
        }
        else if (psiElement is KtOperationReferenceExpression) {
            val parent = psiElement.parent
            when (parent) {
                is KtUnaryExpression -> parent.baseExpression.expressionForCall()
                is KtBinaryExpression -> parent.left.expressionForCall()
                else -> null
            }
        }
        else if (psiElement is KtExpression) {
            if (checkImplicitReceivers && psiElement.getResolvedCall(psiElement.analyze())?.getImplicitReceiverValue() != null) {
                val expressionToReplace = psiElement.parent as? KtCallExpression ?: psiElement
                expressionToReplace.expressionForCall(implicitReceiver = true)
            }
            else psiElement.expressionForCall()
        }
        else {
            null
        }
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction = AddExclExclCallFix(diagnostic.psiElement)
    }
}

object SmartCastImpossibleExclExclFixFactory: KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        if (diagnostic.factory !== Errors.SMARTCAST_IMPOSSIBLE) return null
        val element = diagnostic.psiElement as? KtExpression ?: return null

        val analyze = element.analyze(BodyResolveMode.PARTIAL)
        val type = analyze.getType(element)
        if (type == null || !TypeUtils.isNullableType(type)) return null

        val diagnosticWithParameters = Errors.SMARTCAST_IMPOSSIBLE.cast(diagnostic)
        val expectedType = diagnosticWithParameters.a
        if (TypeUtils.isNullableType(expectedType)) return null
        val nullableExpectedType = TypeUtils.makeNullable(expectedType)
        if (!type.isSubtypeOf(nullableExpectedType)) return null

        return AddExclExclCallFix(element, checkImplicitReceivers = false)
    }
}

object MissingIteratorExclExclFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val element = diagnostic.psiElement as? KtExpression ?: return null

        val analyze = element.analyze(BodyResolveMode.PARTIAL)
        val type = analyze.getType(element)
        if (type == null || !TypeUtils.isNullableType(type)) return null
        
        val descriptor = type.constructor.declarationDescriptor

        fun hasIteratorFunction(classifierDescriptor: ClassifierDescriptor?) : Boolean {
            if (classifierDescriptor !is ClassDescriptor) return false

            val memberScope = classifierDescriptor.unsubstitutedMemberScope
            val functions = memberScope.getContributedFunctions(OperatorNameConventions.ITERATOR, NoLookupLocation.FROM_IDE)

            return functions.any { it.isValidOperator() }
        }

        when (descriptor) {
            is TypeParameterDescriptor -> {
                if (descriptor.upperBounds.none { hasIteratorFunction(it.constructor.declarationDescriptor) }) return null
            }
            is ClassifierDescriptor -> {
                if (!hasIteratorFunction(descriptor)) return null
            }
            else -> return null
        }

        return AddExclExclCallFix(element)
    }
}