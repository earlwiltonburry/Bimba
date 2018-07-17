package ml.adamsprogs.bimba.datasources

import com.google.gson.*
import kotlinx.coroutines.experimental.*
import ml.adamsprogs.bimba.models.Plate
import ml.adamsprogs.bimba.models.gtfs.AgencyAndId
import ml.adamsprogs.bimba.models.suggestions.*
import okhttp3.*
import java.io.IOException
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class VmStopsClient {
    companion object {
        private var vmStopsClient: VmStopsClient? = null

        fun getVmStopClient(): VmStopsClient {
            if (vmStopsClient == null)
                vmStopsClient = VmStopsClient()
            return vmStopsClient!!
        }
    }

    /*suspend fun getBollardsByStopPoint(name: String): Map<String, Set<String>> {
        val response = makeRequest("getBollardsByStopPoint", """{"name": "$name"}""")
        println("asked for $name and got $response")
        val rootObject = response["success"].asJsonObject["bollards"].asJsonArray
        val result = HashMap<String, Set<String>>()
        rootObject.forEach {
            val code = it.asJsonObject["bollard"].asJsonObject["tag"].asString
            result[code] = it.asJsonObject["directions"].asJsonArray.map {
                """${it.asJsonObject["lineName"].asString} → ${it.asJsonObject["direction"].asString}"""
            }.toSet()
        }
        return result
    }

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
        val response = withContext(DefaultDispatcher) {
            makeRequest("getStopPoints", """{"pattern": "$pattern"}""")
        }

        val points = response["success"].asJsonArray.map { it.asJsonObject }

        val names = HashSet<String>()

        points.forEach {
            val name = it["name"].asString
            names.add(name)
        }

        return names.map { StopSuggestion(it, emptySet(), "", "") }
    }

    private suspend fun makeRequest(method: String, data: String): JsonObject {
        val client = OkHttpClient()
        val url = "http://www.peka.poznan.pl/vm/method.vm?ts=${Calendar.getInstance().timeInMillis}"
        val body = RequestBody.create(MediaType.parse("application/x-www-form-urlencoded; charset=UTF-8"),
                "method=$method&p0=$data")
        val request = okhttp3.Request.Builder()
                .url(url)
                .post(body)
                .build()
        println("makeRequest: $request")


        val responseBody: String?
        try {
            responseBody = withContext(CommonPool) {
                client.newCall(request).execute().body()?.string()
            }
        } catch (e: IOException) {
            return JsonObject()
        }


        return Gson().fromJson(responseBody, JsonObject::class.java)
    }
}
