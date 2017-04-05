/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.conjure.gen.python.types;

import com.google.common.base.CaseFormat;
import com.palantir.conjure.defs.ConjureImports;
import com.palantir.conjure.defs.TypesDefinition;
import com.palantir.conjure.defs.types.BaseObjectTypeDefinition;
import com.palantir.conjure.defs.types.EnumTypeDefinition;
import com.palantir.conjure.defs.types.ObjectTypeDefinition;
import com.palantir.conjure.gen.python.PackageNameProcessor;
import com.palantir.conjure.gen.python.poet.PythonBean;
import com.palantir.conjure.gen.python.poet.PythonBean.PythonField;
import com.palantir.conjure.gen.python.poet.PythonClass;
import com.palantir.conjure.gen.python.poet.PythonEnum;
import com.palantir.conjure.gen.python.poet.PythonEnum.PythonEnumValue;
import com.palantir.conjure.gen.python.poet.PythonImport;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class DefaultBeanGenerator implements BeanGenerator {

    @Override
    public PythonClass generateObject(TypesDefinition types,
            ConjureImports importedTypes,
            PackageNameProcessor packageNameProcessor,
            String typeName,
            BaseObjectTypeDefinition typeDef) {
        if (typeDef instanceof ObjectTypeDefinition) {
            return generateObject(types, importedTypes, packageNameProcessor, typeName, (ObjectTypeDefinition) typeDef);
        } else if (typeDef instanceof EnumTypeDefinition) {
            return generateObject(types, importedTypes, packageNameProcessor, typeName, (EnumTypeDefinition) typeDef);
        } else {
            throw new UnsupportedOperationException("cannot generate type for type def: " + typeDef);
        }
    }

    private PythonEnum generateObject(TypesDefinition types,
            ConjureImports importedTypes,
            PackageNameProcessor packageNameProcessor,
            String typeName,
            EnumTypeDefinition typeDef) {

        String packageName = packageNameProcessor.getPackageName(typeDef.packageName());

        return PythonEnum.builder()
                .packageName(packageName)
                .className(typeName)
                .docs(typeDef.docs())
                .values(typeDef.values().stream()
                        .map(value -> PythonEnumValue.of(value.value(), value.docs()))
                        .collect(Collectors.toList()))
                .build();
    }

    private PythonBean generateObject(TypesDefinition types,
            ConjureImports importedTypes,
            PackageNameProcessor packageNameProcessor,
            String typeName,
            ObjectTypeDefinition typeDef) {

        TypeMapper mapper = new TypeMapper(new DefaultTypeNameVisitor(types));
        TypeMapper myPyMapper = new TypeMapper(new MyPyTypeNameVisitor(types));
        ReferencedTypeNameVisitor referencedTypeNameVisitor = new ReferencedTypeNameVisitor(
                types, importedTypes, packageNameProcessor);

        String packageName = packageNameProcessor.getPackageName(typeDef.packageName());

        Set<PythonImport> imports = typeDef.fields().entrySet()
                .stream()
                .flatMap(entry -> entry.getValue().type().visit(referencedTypeNameVisitor).stream())
                .filter(entry -> !entry.packageName().equals(packageName)) // don't need to import if in this file
                .map(referencedClassName -> PythonImport.of(referencedClassName, Optional.empty()))
                .collect(Collectors.toSet());

        return PythonBean.builder()
            .packageName(packageName)
            .addAllRequiredImports(PythonBean.DEFAULT_IMPORTS)
            .addAllRequiredImports(imports)
            .className(typeName)
            .docs(typeDef.docs())
            .fields(typeDef.fields()
                    .entrySet()
                    .stream()
                    .map(entry -> PythonField.builder()
                            .attributeName(pythonAttributeName(entry.getKey()))
                            .jsonIdentifier(entry.getKey())
                            .docs(entry.getValue().docs())
                            .pythonType(mapper.getTypeName(entry.getValue().type()))
                            .myPyType(myPyMapper.getTypeName(entry.getValue().type()))
                            .build())
                    .collect(Collectors.toList()))
            .build();
    }

    private static String pythonAttributeName(String jsonFieldName) {
        return CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_UNDERSCORE, jsonFieldName);
    }
}