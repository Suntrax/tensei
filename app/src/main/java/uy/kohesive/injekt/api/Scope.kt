package uy.kohesive.injekt.api

import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap

open class InjektScope : InjektFactory {
    private val instances = ConcurrentHashMap<Type, Any>()

    @Suppress("UNCHECKED_CAST")
    override fun <R : Any> getInstance(forType: Type): R {
        val instance = instances[forType]
            ?: throw InjektionException("No registered instance for type $forType")
        return instance as R
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R : Any> getInstanceOrNull(forType: Type): R? {
        return instances[forType] as? R
    }

    fun <T : Any> register(type: Type, instance: T) {
        instances[type] = instance
    }
}
