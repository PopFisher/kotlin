/*
 * Copyright 2010-2012 JetBrains s.r.o.
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

package org.jetbrains.jet.lang.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author max
 */
public class JetNamespaceBody extends JetElement implements JetDeclarationContainer {
    public JetNamespaceBody(@NotNull ASTNode node) {
        super(node);
    }

    @NotNull
    @Override
    public List<JetDeclaration> getDeclarations() {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, JetDeclaration.class);
    }

    @Override
    public void accept(@NotNull JetVisitorVoid visitor) {
        visitor.visitNamespaceBody(this);
    }

    @Override
    public <R, D> R accept(@NotNull JetVisitor<R, D> visitor, D data) {
        return visitor.visitNamespaceBody(this, data);
    }
}
