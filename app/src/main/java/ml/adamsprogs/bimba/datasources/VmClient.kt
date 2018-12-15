package ml.adamsprogs.bimba.datasources

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ml.adamsprogs.bimba.NetworkStateReceiver
import ml.adamsprogs.bimba.models.Plate
import ml.adamsprogs.bimba.models.StopSegment
import ml.adamsprogs.bimba.models.suggestions.StopSuggestion
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import java.io.IOException
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class VmClient {
    companion object {
        private var vmClient: VmClient? = null

        fun getVmClient(): VmClient {
            if (vmClient == null)
                vmClient = VmClient()
            return vmClient!!
        }
    }

    suspend fun getSheds(name: String): Map<String, Set<String>> {
        val (_, response) = makeRequest("getBollardsByStopPoint", """{"name": "$name"}""")
        if (!response.has("success"))
            return emptyMap()
        val rootObject = response["success"].asJsonObject["bollards"].asJsonArray
        val result = HashMap<String, Set<String>>()
        rootObject.forEach { element ->
            val code = element.asJsonObject["bollard"].asJsonObject["tag"].asString
            result[code] = element.asJsonObject["directions"].asJsonArray.map {
                """${it.asJsonObject["lineName"].asString} → ${it.asJsonObject["direction"].asString}"""
            }.toSet()
        }
        return result
    }

    /*
    suspend fun getPlatesByStopPoint(code: String): Set<Plate.ID>? {
        val getTimesResponse = makeRequest("getTimes", """{"symbol": "$code"}""")
        val name = getTimesResponse["success"].asJsonObject["bollard"].asJsonObject["name"].asString

        val bollards = getBollardsByStopPoint(name)
        return bollards.filter {
            it.key == code
        }.values.flatMap {
            it.map {
                val (line, headsign) = it.split(" → ")
                Plate.ID(AgencyAndId(line), AgencyAndId(code), headsign)
            }
        }.toSet()
    }*/

    suspend fun getStops(pattern: String): List<StopSuggestion> {
        val (_, response) = withContext(Dispatchers.Default) {
            makeRequest("getStopPoints", """{"pattern": "$pattern"}""")
        }

        if (!response.has("success"))
            return emptyList()

        val points = response["success"].asJsonArray.map { it.asJsonObject }

        val names = HashSet<String>()

        points.forEach {
            val name = it["name"].asString
            names.add(name)
        }

        return names.map { StopSuggestion(it, "") }
    }

    suspend fun makeRequest(method: String, data: String): Pair<Int, JsonObject> {
        if (!NetworkStateReceiver.isNetworkAvailable())
            return Pair(0, JsonObject())

        val client = OkHttpClient()
        val url = "http://www.peka.poznan.pl/vm/method.vm?ts=${Calendar.getInstance().timeInMillis}"
        val body = RequestBody.create(MediaType.parse("application/x-www-form-urlencoded; charset=UTF-8"),
                "method=$method&p0=$data")
        val request = okhttp3.Request.Builder()
                .url(url)
                .post(body)
                .build()


        var responseBody: String? = null
        var responseCode = 0
        try {
            withContext(Dispatchers.Default) {
                client.newCall(request).execute().let {
                    responseCode = it.code()
                    responseBody = it.body()?.string()
                }
            }
        } catch (e: IOException) {
            return Pair(0, JsonObject())
        }

        return try {
            Pair(responseCode, Gson().fromJson(responseBody, JsonObject::class.java))
        } catch (e: JsonSyntaxException) {
            Pair(responseCode, JsonObject())
        }
    }

    suspend fun getName(symbol: String): String? {
        val (_, timesResponse) = withContext(Dispatchers.Default) {
            makeRequest("getTimes", """{"symbol": "$symbol"}""")
        }
        if (!timesResponse.has("success"))
            return null

        return timesResponse["success"].asJsonObject["bollard"].asJsonObject["name"].asString
    }

    suspend fun getDirections(symbol: String): StopSegment? {
        val name = getName(symbol)
        val (_, directionsResponse) = makeRequest("getBollardsByStopPoint", """{"name": "$name"}""")

        if (!directionsResponse.has("success"))
            return null

        return StopSegment(symbol,
                directionsResponse["success"].asJsonObject["bollards"].asJsonArray.filter {
                    it.asJsonObject["bollard"].asJsonObject["tag"].asString == symbol
                }[0].asJsonObject["directions"].asJsonArray.map {
                    it.asJsonObject.let { direction ->
                        Plate.ID(direction["lineName"].asString, symbol, direction["direction"].asString)
                    }
                }.toSet())
    }
}
