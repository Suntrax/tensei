@file:Suppress("NOTHING_TO_INLINE")

package uy.kohesive.injekt

import android.app.Application
import android.content.Context
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.api.InjektScope
import uy.kohesive.injekt.api.fullType

@Volatile
var Injekt: InjektScope = InjektScope()

inline fun <reified T : Any> injectLazy(): Lazy<T> {
    return lazy { Injekt.getInstance(fullType<T>().type) }
}

inline fun <reified T : Any> injectValue(): Lazy<T> {
    return lazyOf(Injekt.getInstance(fullType<T>().type))
}

fun registerApplication(app: Application) {
    Injekt.register(app.javaClass, app)
    Injekt.register(Context::class.java, app)
    Injekt.register(Json::class.java, Json { ignoreUnknownKeys = true; explicitNulls = false })
}
