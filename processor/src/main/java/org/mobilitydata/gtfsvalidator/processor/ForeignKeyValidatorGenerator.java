/*
 * Copyright 2020 Google LLC
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

package org.mobilitydata.gtfsvalidator.processor;

import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.lang.model.element.Modifier;
import org.apache.commons.lang3.StringUtils;
import org.mobilitydata.gtfsvalidator.annotation.Generated;
import org.mobilitydata.gtfsvalidator.annotation.GtfsValidator;
import org.mobilitydata.gtfsvalidator.notice.ForeignKeyViolationNotice;
import org.mobilitydata.gtfsvalidator.notice.NoticeContainer;
import org.mobilitydata.gtfsvalidator.validator.FileValidator;

/**
 * Generates a validator class to check reference integrity for a foreign key.
 *
 * <p>A foreign key constraint is added with {@code @ForeignKey} annotation in GTFS schema.
 */
public class ForeignKeyValidatorGenerator {

  private static final String VALIDATOR_PACKAGE_NAME = "org.mobilitydata.gtfsvalidator.validator";

  private final Map<String, GtfsFileDescriptor> fileDescriptors = new HashMap<>();

  public ForeignKeyValidatorGenerator(List<GtfsFileDescriptor> fileDescriptors) {
    for (GtfsFileDescriptor descriptor : fileDescriptors) {
      this.fileDescriptors.put(descriptor.filename(), descriptor);
    }
  }

  public List<JavaFile> generateValidatorFiles() {
    List<JavaFile> validators = new ArrayList<>();
    for (GtfsFileDescriptor childFile : fileDescriptors.values()) {
      for (GtfsFieldDescriptor childField : childFile.fields()) {
        if (childField.foreignKey().isEmpty()) {
          continue;
        }
        ForeignKeyDescriptor foreignKey = childField.foreignKey().get();
        GtfsFileDescriptor parentFile = fileDescriptors.get(foreignKey.table());
        if (parentFile == null) {
          reportWarning("Cannot find table " + foreignKey.table());
          continue;
        }
        GtfsFieldDescriptor parentField = parentFile.getFieldByName(foreignKey.field());
        if (parentField == null) {
          reportWarning(
              "Cannot find field "
                  + FieldNameConverter.gtfsColumnName(foreignKey.field())
                  + " in table "
                  + foreignKey.table());
          continue;
        }
        validators.add(generateValidator(childFile, childField, parentFile, parentField));
      }
    }
    return validators;
  }

  private JavaFile generateValidator(
      GtfsFileDescriptor childFile,
      GtfsFieldDescriptor childField,
      GtfsFileDescriptor parentFile,
      GtfsFieldDescriptor parentField) {
    GtfsEntityClasses childClasses = new GtfsEntityClasses(childFile);
    GtfsEntityClasses parentClasses = new GtfsEntityClasses(parentFile);
    TypeSpec.Builder typeSpec =
        TypeSpec.classBuilder(validatorName(childFile, childField))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(Generated.class)
            .addAnnotation(GtfsValidator.class)
            .superclass(FileValidator.class);

    typeSpec.addField(
        parentClasses.tableContainerTypeName(),
        "parentContainer",
        Modifier.PRIVATE,
        Modifier.FINAL);
    typeSpec.addField(
        childClasses.tableContainerTypeName(), "childContainer", Modifier.PRIVATE, Modifier.FINAL);

    typeSpec.addMethod(
        MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Inject.class)
            .addParameter(parentClasses.tableContainerTypeName(), "parentContainer")
            .addParameter(childClasses.tableContainerTypeName(), "childContainer")
            .addStatement("this.parentContainer = parentContainer")
            .addStatement("this.childContainer = childContainer")
            .build());

    MethodSpec.Builder validateMethod =
        MethodSpec.methodBuilder("validate")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .returns(void.class)
            .addParameter(NoticeContainer.class, "noticeContainer")
            .beginControlFlow(
                "for ($T childEntity: childContainer.getEntities())",
                childClasses.entityImplementationTypeName())
            .beginControlFlow(
                "if (!childEntity.$L())", FieldNameConverter.hasMethodName(childField.name()))
            .addStatement("continue")
            .endControlFlow()
            .addStatement("String childKey = childEntity.$L()", childField.name())
            .beginControlFlow("if (!hasReferencedKey(childKey, parentContainer))")
            .addStatement(
                "noticeContainer.addValidationNotice(new $T($S, $S, $S, $S, childKey,"
                    + " childEntity.csvRowNumber()))",
                ForeignKeyViolationNotice.class,
                childFile.filename(),
                FieldNameConverter.gtfsColumnName(childField.name()),
                parentFile.filename(),
                FieldNameConverter.gtfsColumnName(parentField.name()))
            .endControlFlow()
            .endControlFlow();
    typeSpec.addMethod(validateMethod.build());

    MethodSpec.Builder hasReferencedKeyMethod =
        MethodSpec.methodBuilder("hasReferencedKey")
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .returns(boolean.class)
            .addParameter(String.class, "key")
            .addParameter(parentClasses.tableContainerTypeName(), "parentContainer");
    if (parentFile.hasSingleColumnPrimaryKey() && parentField.primaryKey().isPresent()) {
      hasReferencedKeyMethod.addStatement(
          "return parentContainer.$L(key).isPresent()",
          FieldNameConverter.byKeyMethodName(parentField.name()));
    } else if (parentField.index()) {
      hasReferencedKeyMethod.addStatement(
          "return !parentContainer.$L(key).isEmpty()",
          FieldNameConverter.byKeyMethodName(parentField.name()));
    } else {
      reportWarning(
          "Parent field "
              + FieldNameConverter.gtfsColumnName(parentField.name())
              + " in "
              + parentFile.filename()
              + " must be annotated with @PrimaryKey or @Index");
    }
    typeSpec.addMethod(hasReferencedKeyMethod.build());

    return JavaFile.builder(VALIDATOR_PACKAGE_NAME, typeSpec.build()).build();
  }

  private String validatorName(GtfsFileDescriptor childFile, GtfsFieldDescriptor childField) {
    return childFile.className()
        + StringUtils.capitalize(childField.name())
        + "ForeignKeyValidator";
  }

  private void reportWarning(String warning) {
    System.err.println(warning);
  }
}
