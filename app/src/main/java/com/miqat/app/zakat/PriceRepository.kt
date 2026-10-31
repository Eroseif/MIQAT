package com.miqat.app.zakat

import com.miqat.app.data.CacheDao
import com.miqat.app.data.CacheEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

data class MarketPrices(
    val goldUsdPerOunce: Double,
    val silverUsdPerOunce: Double,
    val metalsAt: Long,
    val rates: Map<String, Double>,
    val ratesAt: Long
)

/**
 * Live gold and silver spot prices (gold-api.com, USD per troy ounce, no key) and exchange rates
 * (open.er-api.com / ExchangeRate-API, updated daily, attribution required). Cached so it works offline.
 */
class PriceRepository(private val dao: CacheDao, private val http: OkHttpClient) {

    class Result(val prices: MarketPrices?, val stale: Boolean)

    suspend fun load(force: Boolean = false): Result = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        var stale = false

        var gold = Double.NaN
        var silver = Double.NaN
        var metalsAt = 0L
        val mCached = dao.get(KEY_METALS)
        fun readMetals(c: CacheEntry) {
            val o = JSONObject(c.json)
            gold = o.getDouble("g")
            silver = o.getDouble("s")
            metalsAt = c.fetchedAt
        }
        if (!force && mCached != null && now - mCached.fetchedAt < METALS_TTL_MS) {
            readMetals(mCached)
        } else {
            try {
                gold = fetchSpot("XAU")
                silver = fetchSpot("XAG")
                metalsAt = now
                dao.put(CacheEntry(KEY_METALS, JSONObject().put("g", gold).put("s", silver).toString(), now))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (mCached != null) {
                    readMetals(mCached)
                    stale = true
                }
            }
        }

        var rates: Map<String, Double>? = null
        var ratesAt = 0L
        val fCached = dao.get(KEY_FX)
        fun readRates(c: CacheEntry) {
            rates = parseRates(JSONObject(c.json))
            ratesAt = c.fetchedAt
        }
        if (!force && fCached != null && now - fCached.fetchedAt < FX_TTL_MS) {
            readRates(fCached)
        } else {
            try {
                val o = fetchRates()
                rates = parseRates(o)
                ratesAt = now
                dao.put(CacheEntry(KEY_FX, o.toString(), now))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (fCached != null) {
                    readRates(fCached)
                    stale = true
                }
            }
        }

        val r = rates
        if (gold.isNaN() || silver.isNaN() || r == null) Result(null, stale)
        else Result(MarketPrices(gold, silver, metalsAt, r, ratesAt), stale)
    }

    private fun fetchSpot(symbol: String): Double {
        val req = Request.Builder().url("https://api.gold-api.com/price/$symbol").header("Accept", "application/json").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Gold price HTTP ${r.code}")
            val price = JSONObject(r.body?.string() ?: throw IOException("Empty response")).optDouble("price", Double.NaN)
            if (price.isNaN() || price <= 0.0) throw IOException("Bad price")
            return price
        }
    }

    /** Returns the "rates" object (USD base). */
    private fun fetchRates(): JSONObject {
        val req = Request.Builder().url("https://open.er-api.com/v6/latest/USD").header("Accept", "application/json").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Rates HTTP ${r.code}")
            val root = JSONObject(r.body?.string() ?: throw IOException("Empty response"))
            if (root.optString("result") != "success") throw IOException("Rates error")
            return root.getJSONObject("rates")
        }
    }

    private fun parseRates(o: JSONObject): Map<String, Double> {
        val out = HashMap<String, Double>()
        val it = o.keys()
        while (it.hasNext()) {
            val k = it.next()
            val v = o.optDouble(k, Double.NaN)
            if (!v.isNaN() && v > 0.0) out[k] = v
        }
        return out
    }

    companion object {
        const val KEY_METALS = "zakat:metals"
        const val KEY_FX = "zakat:fx"
        const val METALS_TTL_MS = 5L * 60 * 1000
        const val FX_TTL_MS = 6L * 60 * 60 * 1000
    }
}
