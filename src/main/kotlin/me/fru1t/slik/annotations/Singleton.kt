package me.fru1t.slik.annotations

/**
 * Marks a class as a singleton. Slik will only create a single instance of this class per scope
 * per [Named].
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Singleton
