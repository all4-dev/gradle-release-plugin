package dev.all4.gradle.release

/**
 * DSL marker for the publish plugin configuration.
 * Prevents implicit access to outer receiver scopes in nested DSL blocks.
 */
@DslMarker
@Target(AnnotationTarget.CLASS)
public annotation class PublishDsl
