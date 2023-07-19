/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.fhir.datacapture.validation

import android.content.Context
import com.google.android.fhir.datacapture.R
import com.google.android.fhir.datacapture.extensions.asStringValue
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import org.hl7.fhir.r4.model.Extension
import org.hl7.fhir.r4.model.PrimitiveType
import org.hl7.fhir.r4.model.QuestionnaireResponse
import timber.log.Timber

/**
 * A validator to check if the answer matches a given regular expression.
 *
 * <p>Only primitive types permitted in questionnaires response are subjected to this validation.
 * See https://www.hl7.org/fhir/valueset-item-type.html#expansion
 */
internal object RegexValidator :
  AnswerExtensionConstraintValidator(
    url = REGEX_EXTENSION_URL,
    predicate =
      predicate@{
        extension: Extension,
        answer: QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent ->
        if (!extension.value.isPrimitive || !answer.value.isPrimitive) {
          return@predicate false
        }
        try {
          val pattern = Pattern.compile((extension.value as PrimitiveType<*>).asStringValue())
          !pattern.matcher(answer.value.asStringValue()).matches()
        } catch (e: PatternSyntaxException) {
          Timber.w("Can't parse regex: " + extension.value, e)
          false
        }
      },
    messageGenerator = { extension: Extension, context: Context ->
      context.getString(R.string.regex_validation_error_msg, extension.value.primitiveValue())
    }
  )

internal const val REGEX_EXTENSION_URL = "http://hl7.org/fhir/StructureDefinition/regex"
