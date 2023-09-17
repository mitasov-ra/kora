package ru.tinkoff.kora.http.client.symbol.processor.extension

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import ru.tinkoff.kora.http.client.symbol.processor.HttpClientClassNames.httpClientAnnotation
import ru.tinkoff.kora.http.client.symbol.processor.clientName
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.CommonAopUtils.hasAopAnnotations
import ru.tinkoff.kora.ksp.common.getOuterClassesAsPrefix

class HttpClientKoraExtension : KoraExtension {
    override fun getDependencyGenerator(resolver: Resolver, type: KSType, tags: Set<String>): (() -> ExtensionResult)? {
        if (tags.isNotEmpty()) return null
        val declaration = type.declaration
        if (declaration !is KSClassDeclaration || declaration.classKind != ClassKind.INTERFACE) {
            return null
        }
        if (declaration.findAnnotation(httpClientAnnotation) == null) {
            return null
        }
        return lambda@{
            val implName = declaration.clientName()
            val packageName: String = declaration.packageName.asString()
            val maybeGenerated = resolver.getClassDeclarationByName("$packageName.$implName")
            if (maybeGenerated == null) {
                return@lambda ExtensionResult.RequiresCompilingResult
            }
            if (!hasAopAnnotations(maybeGenerated)) {
                return@lambda maybeGenerated.getConstructors().map { ExtensionResult.fromConstructor(it, maybeGenerated) }.first()
            }
            val aopProxy = maybeGenerated.getOuterClassesAsPrefix() + maybeGenerated.simpleName.getShortName() + "__AopProxy"
            val aopProxyElement = resolver.getClassDeclarationByName("$packageName.$aopProxy")
            if (aopProxyElement == null) {
                return@lambda ExtensionResult.RequiresCompilingResult
            }
            return@lambda aopProxyElement.getConstructors().map { ExtensionResult.fromConstructor(it, aopProxyElement) }.first()
        }
    }
}

