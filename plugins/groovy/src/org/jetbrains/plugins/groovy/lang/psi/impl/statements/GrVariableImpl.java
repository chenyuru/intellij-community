/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 11.04.2007
 */
public class GrVariableImpl extends GroovyPsiElementImpl implements GrVariable {
  public GrVariableImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Variable";
  }

  public String getName() {
    PsiElement nameElement = findChildByType(GroovyTokenTypes.mIDENT);
    return nameElement == null ? null : nameElement.getText();
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("NIY");
  }
}
