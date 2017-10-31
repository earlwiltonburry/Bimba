package ml.adamsprogs.bimba

import android.content.Context
import android.content.SharedPreferences
import ml.adamsprogs.bimba.models.Plate

class CacheManager private constructor(context: Context) {
    companion object {
        private var manager: CacheManager? = null
        fun getCacheManager(context: Context): CacheManager {
            return if (manager == null) {
                manager = CacheManager(context)
                manager!!
            } else
                manager!!
        }

        val MAX_SIZE = 40
    }

    private var cachePreferences: SharedPreferences = context.getSharedPreferences("ml.adamsprogs.bimba.cachePreferences.cache", Context.MODE_PRIVATE)
    private var cacheHitsPreferences: SharedPreferences = context.getSharedPreferences("ml.adamsprogs.bimba.cachePreferences.cacheHits", Context.MODE_PRIVATE)

    private var cache: HashMap<String, Plate> = HashMap()
    private var cacheHits: HashMap<String, Int> = HashMap()

    fun keys(): List<Plate> {
        return cache.map { Plate(it.key.split("@")[0], it.key.split("@")[1], null) }
    }

    fun hasAll(plates: HashSet<Plate>): Boolean {
        plates
                .filterNot { has(it) }
                .forEach { return false }
        return true
    }

    fun hasAny(plates: HashSet<Plate>): Boolean {
        plates
                .filter { has(it) }
                .forEach { return true }
        return false
    }

    fun has(plate: Plate): Boolean {
        return cache.containsKey(key(plate))
    }

    fun push(plates: HashSet<Plate>) {
        val removeNumber = cache.size + plates.size - MAX_SIZE
        val editor = cachePreferences.edit()
        val editorCacheHits = cacheHitsPreferences.edit()
        cacheHits.map { "${it.value}|${it.key}" }.sortedBy { it }.slice(0 until removeNumber).forEach {
            val key = it.split("|")[1]
            cache.remove(key)
            editor.remove(key)
        }
        for (plate in plates) {
            val key = key(plate)
            cache[key] = plate
            cacheHits[key] = 0
            editor.putString(key, cache[key].toString())
            editorCacheHits.putInt(key, 0)
        }
        editor.apply()
        editorCacheHits.apply()
    }

    fun push(plate: Plate) {
        val editorCache = cachePreferences.edit()
        val editorCacheHits = cacheHitsPreferences.edit()
        if (cacheHits.size == MAX_SIZE) {
            val key = cacheHits.minBy { it.value }?.key
            cache.remove(key)
            editorCache.remove(key)
            cacheHits.remove(key)
            editorCacheHits.remove(key)
        }
        val key = key(plate)
        cache[key] = plate
        cacheHits[key] = 0
        editorCache.putString(key, plate.toString())
        editorCacheHits.putInt(key, 0)
        editorCache.apply()
        editorCacheHits.apply()
    }

    fun get(plates: HashSet<Plate>): HashSet<Plate> {
        val result = HashSet<Plate>()
        for (plate in plates) {
            val value = get(plate)
            if (value == null)
                result.add(plate)
            else
                result.add(value)
        }
        return result
    }

    fun get(plate: Plate): Plate? {
        if (!has(plate))
            return null
        val key = key(plate)
        val hits = cacheHits[key]
        if (hits != null)
            cacheHits[key] = hits + 1
        return cache[key]
    }

    fun recreate(stopDeparturesByPlates: HashSet<Plate>) {
        stopDeparturesByPlates.forEach { cache[key(it)] = it }
    }

    init {
        cache = cacheFromString(cachePreferences.all)
        @Suppress("UNCHECKED_CAST")
        cacheHits = cacheHitsPreferences.all as HashMap<String, Int>
    }

    private fun cacheFromString(preferences: Map<String, *>): HashMap<String, Plate> {
        val result = HashMap<String, Plate>()
        for ((key, value) in preferences.entries) {
            result[key] = Plate.fromString(value as String)
        }
        return result
    }

    private fun key(plate: Plate) = "${plate.line}@${plate.stop}"
}