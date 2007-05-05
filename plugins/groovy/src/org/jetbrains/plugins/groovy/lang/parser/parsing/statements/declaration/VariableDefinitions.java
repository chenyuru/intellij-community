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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.NlsWarn;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.ThrowClause;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.annotations.Annotation;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.parameters.ParameterDeclarationList;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.AssignmentExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.ConditionalExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */

/*
 * variableDefinitions ::=
 *      variableDeclarator ( COMMA nls variableDeclarator )*
 *    |	(	IDENT |	STRING_LITERAL )
 *      LPAREN parameterDeclarationList RPAREN
 *      (throwsClause | )
 *      (	( nlsWarn openBlock ) | )
 */

public class VariableDefinitions implements GroovyElementTypes {
  public static GroovyElementType parse(PsiBuilder builder) {
    return parseDefinitions(builder, false, false);
  }

  public static GroovyElementType parseDefinitions(PsiBuilder builder, boolean isEnumConstantMember, boolean isAnnotationMember) {
    if (!(ParserUtils.lookAhead(builder, mIDENT) || ParserUtils.lookAhead(builder, mSTRING_LITERAL) || ParserUtils.lookAhead(builder, mGSTRING_LITERAL))) {
      builder.error(GroovyBundle.message("indentifier.or.string.literal.expected"));
      return WRONGWAY;
    }

    PsiBuilder.Marker varMarker = builder.mark();
    boolean isStringName = ParserUtils.lookAhead(builder, mSTRING_LITERAL) || ParserUtils.lookAhead(builder, mGSTRING_LITERAL);

    if (isAnnotationMember && isStringName) {
      builder.error(GroovyBundle.message("string.name.unexpected"));
    }

    //eaten one of these tokens
    boolean eaten = ParserUtils.getToken(builder, mIDENT) || ParserUtils.getToken(builder, mSTRING_LITERAL) || ParserUtils.getToken(builder, mGSTRING_LITERAL);

    if (!eaten) return WRONGWAY;

    if (ParserUtils.getToken(builder, mLPAREN)) {
      GroovyElementType paramDeclList = ParameterDeclarationList.parse(builder, mRPAREN);

      if (isEnumConstantMember && !isStringName) {
        builder.error(GroovyBundle.message("string.name.unexpected"));
      }

      if (isAnnotationMember && !NONE.equals(paramDeclList)) {
        builder.error(GroovyBundle.message("empty.parameter.list.expected"));
      }

      boolean isEmptyParamDeclList = NONE.equals(paramDeclList);

      ParserUtils.getToken(builder, mNLS);
      if (!ParserUtils.getToken(builder, mRPAREN)) {
        ParserUtils.waitNextRCurly(builder);
        builder.error(GroovyBundle.message("rparen.expected"));
      }

      if (!isStringName && isEmptyParamDeclList && ParserUtils.getToken(builder, kDEFAULT)) {
        ParserUtils.getToken(builder, mNLS);

        if (parseAnnotationMemberValueInitializer(builder)) {
          varMarker.done(DEFAULT_ANNOTATION_MEMBER);
          return DEFAULT_ANNOTATION_MEMBER;
        }
      }

      ThrowClause.parse(builder);

      //if there is no OpenOrClosableBlock, nls haven'to be eaten
      PsiBuilder.Marker nlsMarker = builder.mark();
      if (mNLS.equals(NlsWarn.parse(builder)) && !ParserUtils.lookAhead(builder, mLPAREN)) {
        nlsMarker.rollbackTo();
      } else {
        nlsMarker.drop();
      }

//      ParserUtils.getToken(builder, mNLS);

      OpenOrClosableBlock.parseOpenBlock(builder);

      varMarker.drop();
      return METHOD_DEFINITION;
    } else {
      varMarker.rollbackTo();

      // a = b, c = d
      PsiBuilder.Marker varAssMarker = builder.mark();
      if (ParserUtils.getToken(builder, mIDENT)) {

        if (parseAssignment(builder)) { // a = b, c = d
          varAssMarker.done(VARIABLE);
          while (ParserUtils.getToken(builder, mCOMMA)) {
            ParserUtils.getToken(builder, mNLS);

            if (WRONGWAY.equals(parseVariableDeclarator(builder))) return VARIABLE_DEFINITION_ERROR; //parse b = d
          }
          return VARIABLE_DEFINITION;
        } else {
          varAssMarker.done(VARIABLE);
//          varAssMarker.drop();
          boolean isManyDef = false;
          while (ParserUtils.getToken(builder, mCOMMA)) {// a, b = d, c = d
            ParserUtils.getToken(builder, mNLS);

            if (WRONGWAY.equals(parseVariableDeclarator(builder))) return VARIABLE_DEFINITION_ERROR;
            isManyDef = true;
          }

          if (isManyDef) {
            return VARIABLE_DEFINITION;
          } else {
            return IDENTIFIER;
          }

//          return VARIABLE_DEFINITION_OR_METHOD_CALL;
        }
      } else {
        varAssMarker.drop();
        builder.error(GroovyBundle.message("identifier.expected"));
        return VARIABLE_DEFINITION_ERROR;
      }

    }
  }

  //a, a = b
  private static GroovyElementType parseVariableDeclarator(PsiBuilder builder) {
    PsiBuilder.Marker varAssMarker = builder.mark();
    if (ParserUtils.getToken(builder, mIDENT)) {
      parseAssignment(builder);
      varAssMarker.done(VARIABLE);
      return VARIABLE;
    } else {
      varAssMarker.drop();
      return WRONGWAY;
    }
  }

  private static boolean parseAssignment(PsiBuilder builder) {
    if (ParserUtils.getToken(builder, mASSIGN)) {
      ParserUtils.getToken(builder, mNLS);

      if (WRONGWAY.equals(AssignmentExpression.parse(builder))) {
        builder.error(GroovyBundle.message("expression.expected"));
        return false;
      } else {
        return true;
      }
    }
    return false;
  }

  private static boolean parseAnnotationMemberValueInitializer(PsiBuilder builder) {
    return !WRONGWAY.equals(Annotation.parse(builder)) || !WRONGWAY.equals(ConditionalExpression.parse(builder));
  }

  public static GroovyElementType parseAnnotationMember(PsiBuilder builder) {
    return parseDefinitions(builder, false, true);
  }
}
