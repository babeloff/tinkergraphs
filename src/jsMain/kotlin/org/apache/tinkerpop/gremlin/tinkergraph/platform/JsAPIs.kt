package org.apache.tinkerpop.gremlin.tinkergraph.platform

import kotlin.js.Promise

/**
 * External declarations for JavaScript APIs used in storage implementations.
 * These provide type-safe access to browser and Node.js APIs from Kotlin/JS.
 */

// ===== Browser Storage APIs =====

@JsName("localStorage")
external object LocalStorage {
    fun getItem(key: String): String?
    fun setItem(key: String, value: String)
    fun removeItem(key: String)
    fun clear()
    fun key(index: Int): String?
    val length: Int
}

@JsName("sessionStorage")
external object SessionStorage {
    fun getItem(key: String): String?
    fun setItem(key: String, value: String)
    fun removeItem(key: String)
    fun clear()
    fun key(index: Int): String?
    val length: Int
}

// ===== IndexedDB APIs =====

external interface IDBRequest {
    val result: dynamic
    val error: dynamic
    val readyState: String
    var onsuccess: ((Event) -> Unit)?
    var onerror: ((Event) -> Unit)?
}

external interface IDBOpenDBRequest : IDBRequest {
    var onupgradeneeded: ((IDBVersionChangeEvent) -> Unit)?
    var onblocked: ((Event) -> Unit)?
}

external interface IDBDatabase {
    val name: String
    val version: Double
    val objectStoreNames: dynamic

    fun createObjectStore(name: String, options: dynamic = definedExternally): IDBObjectStore
    fun deleteObjectStore(name: String)
    fun transaction(storeNames: dynamic, mode: String = definedExternally): IDBTransaction
    fun close()

    var onclose: ((Event) -> Unit)?
    var onerror: ((Event) -> Unit)?
    var onversionchange: ((IDBVersionChangeEvent) -> Unit)?
}

external interface IDBTransaction {
    val db: IDBDatabase
    val mode: String
    val objectStoreNames: dynamic

    fun objectStore(name: String): IDBObjectStore
    fun abort()

    var oncomplete: ((Event) -> Unit)?
    var onerror: ((Event) -> Unit)?
    var onabort: ((Event) -> Unit)?
}

external interface IDBObjectStore {
    val name: String
    val keyPath: dynamic
    val indexNames: dynamic
    val autoIncrement: Boolean

    fun add(value: dynamic, key: dynamic = definedExternally): IDBRequest
    fun put(value: dynamic, key: dynamic = definedExternally): IDBRequest
    fun get(key: dynamic): IDBRequest
    fun delete(key: dynamic): IDBRequest
    fun clear(): IDBRequest
    fun count(key: dynamic = definedExternally): IDBRequest
    fun openCursor(range: dynamic = definedExternally, direction: String = definedExternally): IDBRequest
    fun createIndex(name: String, keyPath: dynamic, options: dynamic = definedExternally): IDBIndex
    fun deleteIndex(name: String)
    fun index(name: String): IDBIndex
}

external interface IDBIndex {
    val name: String
    val objectStore: IDBObjectStore
    val keyPath: dynamic
    val unique: Boolean
    val multiEntry: Boolean

    fun get(key: dynamic): IDBRequest
    fun getKey(key: dynamic): IDBRequest
    fun count(key: dynamic = definedExternally): IDBRequest
    fun openCursor(range: dynamic = definedExternally, direction: String = definedExternally): IDBRequest
    fun openKeyCursor(range: dynamic = definedExternally, direction: String = definedExternally): IDBRequest
}

external interface IDBCursor {
    val source: dynamic
    val direction: String
    val key: dynamic
    val primaryKey: dynamic
    val value: dynamic

    fun update(value: dynamic): IDBRequest
    fun delete(): IDBRequest
    fun advance(count: Int)
    fun `continue`(key: dynamic = definedExternally)
    fun continuePrimaryKey(key: dynamic, primaryKey: dynamic)
}

external interface IDBVersionChangeEvent : Event {
    val oldVersion: Double
    val newVersion: Double?
}

@JsName("indexedDB")
external val indexedDB: IDBFactory

external interface IDBFactory {
    fun open(name: String, version: Double = definedExternally): IDBOpenDBRequest
    fun deleteDatabase(name: String): IDBOpenDBRequest
    fun cmp(first: dynamic, second: dynamic): Int
}

// ===== Node.js File System APIs =====

external interface NodeJSBuffer {
    fun toString(encoding: String = definedExternally): String
}

external interface NodeJSStats {
    fun isFile(): Boolean
    fun isDirectory(): Boolean
    val size: Double
    val mtime: dynamic
    val ctime: dynamic
}

external interface NodeJSFileSystem {
    fun readFileSync(path: String, encoding: String = definedExternally): dynamic
    fun writeFileSync(path: String, data: String, options: dynamic = definedExternally)
    fun existsSync(path: String): Boolean
    fun mkdirSync(path: String, options: dynamic = definedExternally)
    fun readdirSync(path: String): Array<String>
    fun statSync(path: String): NodeJSStats
    fun unlinkSync(path: String)
    fun rmdirSync(path: String, options: dynamic = definedExternally)
    fun copyFileSync(src: String, dest: String)
    fun renameSync(oldPath: String, newPath: String)

    fun readFile(path: String, encoding: String, callback: (Error?, String?) -> Unit)
    fun writeFile(path: String, data: String, callback: (Error?) -> Unit)
    fun mkdir(path: String, options: dynamic, callback: (Error?) -> Unit)
    fun readdir(path: String, callback: (Error?, Array<String>?) -> Unit)
    fun stat(path: String, callback: (Error?, NodeJSStats?) -> Unit)
    fun unlink(path: String, callback: (Error?) -> Unit)
    fun rmdir(path: String, options: dynamic, callback: (Error?) -> Unit)
}

external interface NodeJSPath {
    fun join(vararg paths: String): String
    fun resolve(vararg paths: String): String
    fun dirname(path: String): String
    fun basename(path: String, ext: String = definedExternally): String
    fun extname(path: String): String
    val sep: String
}

external interface NodeJSProcess {
    val versions: dynamic
    val platform: String
    val env: dynamic
    fun cwd(): String
}

// ===== Common Browser APIs =====

external interface Event {
    val type: String
    val target: dynamic
    fun preventDefault()
    fun stopPropagation()
}

external interface Error {
    val name: String
    val message: String
    val stack: String?
}

@JsName("JSON")
external object JSON {
    fun parse(text: String): dynamic
    fun stringify(value: dynamic, replacer: dynamic = definedExternally, space: dynamic = definedExternally): String
}

@JsName("Date")
external class JSDate {
    constructor()
    constructor(milliseconds: Double)
    constructor(dateString: String)

    fun getTime(): Double
    fun toISOString(): String

    companion object {
        fun now(): Double
    }
}

// ===== Promise Extensions =====

external interface PromiseConstructor {
    fun <T> resolve(value: T): Promise<T>
    fun <T> reject(reason: dynamic): Promise<T>
    fun <T> all(promises: Array<Promise<T>>): Promise<Array<T>>
    fun <T> race(promises: Array<Promise<T>>): Promise<T>
}

@JsName("Promise")
external val PromiseStatic: PromiseConstructor

// ===== Utility Functions =====

/**
 * Check if running in Node.js environment
 */
fun isNodeJS(): Boolean = try {
    js("typeof process !== 'undefined' && process.versions && process.versions.node").unsafeCast<Boolean>()
} catch (e: Exception) {
    false
}

/**
 * Check if running in browser environment
 */
fun isBrowser(): Boolean = try {
    js("typeof window !== 'undefined'").unsafeCast<Boolean>()
} catch (e: Exception) {
    false
}

/**
 * Get Node.js require function if available
 */
fun getNodeRequire(): ((String) -> dynamic)? = try {
    if (isNodeJS()) {
        js("require").unsafeCast<(String) -> dynamic>()
    } else null
} catch (e: Exception) {
    null
}

/**
 * Get Node.js fs module if available
 */
fun getNodeFS(): NodeJSFileSystem? = try {
    getNodeRequire()?.invoke("fs")?.unsafeCast<NodeJSFileSystem>()
} catch (e: Exception) {
    null
}

/**
 * Get Node.js path module if available
 */
fun getNodePath(): NodeJSPath? = try {
    getNodeRequire()?.invoke("path")?.unsafeCast<NodeJSPath>()
} catch (e: Exception) {
    null
}

/**
 * Get Node.js process if available
 */
fun getNodeProcess(): NodeJSProcess? = try {
    if (isNodeJS()) {
        js("process").unsafeCast<NodeJSProcess>()
    } else null
} catch (e: Exception) {
    null
}

/**
 * Check if LocalStorage is available
 */
fun isLocalStorageAvailable(): Boolean = try {
    js("typeof Storage !== 'undefined' && typeof localStorage !== 'undefined'").unsafeCast<Boolean>()
} catch (e: Exception) {
    false
}

/**
 * Check if IndexedDB is available
 */
fun isIndexedDBAvailable(): Boolean = try {
    js("typeof indexedDB !== 'undefined'").unsafeCast<Boolean>()
} catch (e: Exception) {
    false
}
