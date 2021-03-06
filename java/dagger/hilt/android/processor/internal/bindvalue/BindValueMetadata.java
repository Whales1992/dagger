/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.android.processor.internal.bindvalue;

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.Processors;
import java.util.Collection;
import java.util.Optional;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

/**
 * Represents metadata for a test class that has {@code BindValue} fields.
 */
@AutoValue
abstract class BindValueMetadata {
  /** @return the {@code TestRoot} annotated class's name. */
  abstract TypeElement testElement();

  /** @return a {@link ImmutableSet} of elements annotated with @BindValue. */
  abstract ImmutableSet<BindValueElement> bindValueElements();

  /** @return a new BindValueMetadata instance. */
  static BindValueMetadata create(TypeElement testElement, Collection<Element> bindValueElements) {

    ImmutableSet.Builder<BindValueElement> elements = ImmutableSet.builder();
    for (Element element : bindValueElements) {
      elements.add(BindValueElement.create(element));
    }

    return new AutoValue_BindValueMetadata(testElement, elements.build());
  }

  @AutoValue
  abstract static class BindValueElement {
    abstract VariableElement variableElement();

    abstract ClassName annotationName();

    abstract Optional<AnnotationMirror> qualifier();

    abstract Optional<AnnotationMirror> mapKey();

    static BindValueElement create(Element element) {
      ImmutableList<ClassName> bindValues = BindValueProcessor.getBindValueAnnotations(element);
      ProcessorErrors.checkState(
          bindValues.size() == 1,
          element,
          "Fields can be annotated with only one of @BindValue, @BindValueIntoMap,"
              + " @BindElementsIntoSet, @BindValueIntoSet. Found: %s",
          bindValues.stream().map(m -> "@" + m.simpleName()).collect(toImmutableList()));
      ClassName annotationClassName = bindValues.get(0);

      ProcessorErrors.checkState(
          element.getKind() == ElementKind.FIELD,
          element,
          "@%s can only be used with fields. Found: %s",
          annotationClassName.simpleName(),
          element);

      ProcessorErrors.checkState(
          !element.getModifiers().contains(Modifier.PRIVATE),
          element,
          "@%s fields cannot be private. Found: %s",
          annotationClassName.simpleName(),
          element);

      ProcessorErrors.checkState(
          !Processors.hasAnnotation(element, Inject.class),
          element,
          "@%s fields cannot be used with @Inject annotation. Found %s",
          annotationClassName.simpleName(),
          element);

      ImmutableList<AnnotationMirror> qualifiers = Processors.getQualifierAnnotations(element);
      ProcessorErrors.checkState(
          qualifiers.size() <= 1,
          element,
          "@%s fields cannot have more than one qualifier. Found %s",
          annotationClassName.simpleName(),
          qualifiers);

      ImmutableList<AnnotationMirror> mapKeys = Processors.getMapKeyAnnotations(element);
      Optional<AnnotationMirror> optionalMapKeys;
      if (annotationClassName.equals(ClassNames.BIND_VALUE_INTO_MAP)
      ) {
        ProcessorErrors.checkState(
            mapKeys.size() == 1,
            element,
            "@BindValueIntoMap fields must have exactly one @MapKey. Found %s",
            mapKeys);
        optionalMapKeys = Optional.of(mapKeys.get(0));
      } else {
        ProcessorErrors.checkState(
            mapKeys.isEmpty(),
            element,
            "@MapKey can only be used on @BindValueIntoMap fields, not @%s fields",
            annotationClassName.simpleName());
        optionalMapKeys = Optional.empty();
      }

      ImmutableList<AnnotationMirror> scopes = Processors.getScopeAnnotations(element);
      ProcessorErrors.checkState(
          scopes.isEmpty(),
          element,
          "@%s fields cannot be scoped. Found %s",
          annotationClassName.simpleName(),
          scopes);

      return new AutoValue_BindValueMetadata_BindValueElement(
          (VariableElement) element,
          annotationClassName,
          qualifiers.isEmpty()
              ? Optional.<AnnotationMirror>empty()
              : Optional.<AnnotationMirror>of(qualifiers.get(0)),
          optionalMapKeys);
    }
  }
}
