package ru.tinkoff.kora.kora.app.annotation.processor.declaration;

import ru.tinkoff.kora.annotation.processor.common.*;
import ru.tinkoff.kora.kora.app.annotation.processor.ProcessingContext;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionResult;

import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public sealed interface ComponentDeclaration {
    TypeMirror type();

    Element source();

    Set<String> tags();

    default boolean isTemplate() {
        return TypeParameterUtils.hasTypeParameter(this.type());
    }

    default boolean isDefault() {
        return false;
    }

    boolean isInterceptor();

    String declarationString();

    record FromModuleComponent(TypeMirror type, ModuleDeclaration module, Set<String> tags, ExecutableElement method, List<TypeMirror> methodParameterTypes,
                               List<TypeMirror> typeVariables, boolean isInterceptor) implements ComponentDeclaration {
        @Override
        public Element source() {
            return this.method;
        }

        @Override
        public boolean isDefault() {
            return AnnotationUtils.findAnnotation(this.method, CommonClassNames.defaultComponent) != null;
        }

        @Override
        public String declarationString() {
            return module.element().getQualifiedName() + "." + method.getSimpleName();
        }
    }

    record AnnotatedComponent(TypeMirror type, TypeElement typeElement, Set<String> tags, ExecutableElement constructor, List<TypeMirror> methodParameterTypes,
                              List<TypeMirror> typeVariables, boolean isInterceptor) implements ComponentDeclaration {
        @Override
        public Element source() {
            return this.constructor;
        }

        @Override
        public String declarationString() {
            return typeElement.getQualifiedName().toString();
        }
    }

    record DiscoveredAsDependencyComponent(TypeMirror type, TypeElement typeElement, ExecutableElement constructor, Set<String> tags) implements ComponentDeclaration {
        public DiscoveredAsDependencyComponent {
            assert typeElement.getTypeParameters().isEmpty();
        }

        @Override
        public Element source() {
            return this.constructor;
        }

        @Override
        public boolean isTemplate() {
            return false;
        }

        @Override
        public boolean isInterceptor() {
            return false;
        }

        @Override
        public String declarationString() {
            return typeElement.getQualifiedName().toString();
        }
    }

    record FromExtensionComponent(TypeMirror type, ExecutableElement sourceMethod, List<TypeMirror> methodParameterTypes) implements ComponentDeclaration {
        @Override
        public Element source() {
            return this.sourceMethod;
        }

        @Override
        public Set<String> tags() {
            return Set.of();
        }

        @Override
        public boolean isInterceptor() {
            return false;
        }

        @Override
        public String declarationString() {
            return sourceMethod.getEnclosingElement().toString() + "." + sourceMethod.getSimpleName();
        }
    }

    record PromisedProxyComponent(TypeElement typeElement, TypeMirror type, com.squareup.javapoet.ClassName className) implements ComponentDeclaration {
        public PromisedProxyComponent(TypeElement typeElement, com.squareup.javapoet.ClassName className) {
            this(typeElement, typeElement.asType(), className);
        }

        public PromisedProxyComponent withType(TypeMirror type) {
            return new PromisedProxyComponent(typeElement, type, className);
        }


        @Override
        public Element source() {
            return this.typeElement;
        }

        @Override
        public Set<String> tags() {
            return Set.of(CommonClassNames.promisedProxy.canonicalName());
        }

        @Override
        public boolean isInterceptor() {
            return false;
        }

        @Override
        public String declarationString() {
            return "<Proxy>";
        }
    }

    record OptionalComponent(TypeMirror type, Set<String> tags) implements ComponentDeclaration {
        @Override
        public Element source() {
            return null;
        }

        @Override
        public boolean isInterceptor() {
            return false;
        }

        @Override
        public String declarationString() {
            return "<EmptyOptional>";
        }
    }

    static ComponentDeclaration fromModule(ProcessingContext ctx, ModuleDeclaration module, ExecutableElement method) {
        var type = method.getReturnType();
        if (TypeParameterUtils.hasRawTypes(type)) {
            throw new ProcessingErrorException("Components with raw types can break dependency resolution in unpredictable way so they are forbidden", method);
        }
        var tags = TagUtils.parseTagValue(method);
        var parameterTypes = method.getParameters().stream().map(VariableElement::asType).toList();
        var typeParameters = method.getTypeParameters().stream().map(TypeParameterElement::asType).toList();
        var isInterceptor = ctx.serviceTypeHelper.isInterceptor(type);
        return new FromModuleComponent(type, module, tags, method, parameterTypes, typeParameters, isInterceptor);
    }

    static ComponentDeclaration fromAnnotated(ProcessingContext ctx, TypeElement typeElement) {
        var constructors = CommonUtils.findConstructors(typeElement, m -> m.contains(Modifier.PUBLIC));
        if (constructors.size() != 1) {
            throw new ProcessingErrorException("Components with raw types can break dependency resolution in unpredictable way so they are forbidden", typeElement);
        }
        var constructor = constructors.get(0);
        var type = typeElement.asType();
        if (TypeParameterUtils.hasRawTypes(type)) {
            ctx.messager.printMessage(Diagnostic.Kind.WARNING, "Components with raw types can break dependency resolution in unpredictable way", typeElement);
        }
        var tags = TagUtils.parseTagValue(typeElement);
        var parameterTypes = constructor.getParameters().stream().map(VariableElement::asType).toList();
        var typeParameters = typeElement.getTypeParameters().stream().map(TypeParameterElement::asType).toList();
        var isInterceptor = ctx.serviceTypeHelper.isInterceptor(type);
        return new AnnotatedComponent(type, typeElement, tags, constructor, parameterTypes, typeParameters, isInterceptor);
    }

    static ComponentDeclaration fromDependency(ProcessingContext ctx, TypeElement typeElement) {
        var constructors = CommonUtils.findConstructors(typeElement, m -> m.contains(Modifier.PUBLIC));
        if (constructors.size() != 1) {
            throw new ProcessingErrorException("Can't create component from discovered as dependency class: class should have exactly one public constructor", typeElement);
        }
        var constructor = constructors.get(0);
        var type = typeElement.asType();
        if (TypeParameterUtils.hasRawTypes(type)) {
            throw new ProcessingErrorException("Components with raw types can break dependency resolution in unpredictable way so they are forbidden", typeElement);
        }
        var tags = TagUtils.parseTagValue(typeElement);
        return new DiscoveredAsDependencyComponent(type, typeElement, constructor, tags);
    }

    static ComponentDeclaration fromExtension(ProcessingContext ctx, ExtensionResult.GeneratedResult generatedResult) {
        var sourceMethod = generatedResult.sourceElement();
        if (sourceMethod.getKind() == ElementKind.CONSTRUCTOR) {
            var parameterTypes = sourceMethod.getParameters().stream().map(VariableElement::asType).toList();
            var typeElement = (TypeElement) sourceMethod.getEnclosingElement();
            var type = typeElement.asType();
            if (TypeParameterUtils.hasRawTypes(type)) {
                throw new ProcessingErrorException("Components with raw types can break dependency resolution in unpredictable way so they are forbidden", sourceMethod);
            }
            return new FromExtensionComponent(type, sourceMethod, parameterTypes);
        } else {
            var type = generatedResult.targetType().getReturnType();
            var parameterTypes = generatedResult.targetType().getParameterTypes();
            if (TypeParameterUtils.hasRawTypes(type)) {
                throw new ProcessingErrorException("Components with raw types can break dependency resolution in unpredictable way so they are forbidden", sourceMethod);
            }
            return new FromExtensionComponent(type, sourceMethod, new ArrayList<>(parameterTypes));
        }
    }
}
