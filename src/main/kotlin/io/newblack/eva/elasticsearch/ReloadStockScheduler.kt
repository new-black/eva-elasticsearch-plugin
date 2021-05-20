package io.newblack.elastic

import com.carrotsearch.hppc.IntObjectHashMap
import com.carrotsearch.hppc.LongIntHashMap
import com.carrotsearch.hppc.LongObjectHashMap
import io.newblack.eva.elasticsearch.ProductVariationData
import org.apache.http.HttpStatus
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.client.HttpClients
import org.elasticsearch.SpecialPermission
import org.elasticsearch.common.component.AbstractLifecycleComponent
import org.elasticsearch.common.logging.Loggers
import org.elasticsearch.common.settings.Settings
import java.lang.Exception
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.AccessController
import java.security.PrivilegedAction
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class ReloadStockScheduler(
        settings: Settings,
        private val scheduler: ScheduledExecutorService
) : AbstractLifecycleComponent() {

    private var schedule: ScheduledFuture<*>? = null

    companion object {
        val logger = Loggers.getLogger(ReloadStockScheduler::class.java, "eva")

        var stockMap = LongIntHashMap()
        var variationStockData : HashMap<String, HashMap<String, LongObjectHashMap<IntArray>>> = hashMapOf()
        var variationMap: IntObjectHashMap<String> = IntObjectHashMap()
        var variationReverseMap: HashMap<String, Int> = hashMapOf()

        fun doReload() {
            var url = System.getenv("EVA_STOCK_URL") ?: "http://eva-backend-service:8080/middleware/pim/stock"

            SpecialPermission.check()

            AccessController.doPrivileged(PrivilegedAction<Unit> {
                logger.info("Reloading Stock from $url")

                val config = RequestConfig.custom()
                        .setConnectTimeout(10 * 1000)
                        .setSocketTimeout(90 * 1000)
                        .setConnectionRequestTimeout(5 * 1000).build()

                val httpClientFactory = HttpClients.custom().setDefaultRequestConfig(config)

                reloadStock(httpClientFactory, url)
                //reloadVariationStock(httpClientFactory, "$url/variations")

                logger.info("Reloaded Stock: ${stockMap.size()} records")
            })
        }

        private fun reloadStock(httpClientFactory: HttpClientBuilder, url: String) {
            try {
                httpClientFactory.build().use {

                    var request = HttpGet(url)

                    it.execute(request).use { httpResponse ->

                        logger.info("Received response with Status ${httpResponse.statusLine.statusCode}")

                        if (httpResponse.statusLine.statusCode == HttpStatus.SC_OK) {

                            var data = httpResponse.entity.content.readBytes()

                            logger.info("Response size ${data.size}")

                            val buffer = ByteBuffer.wrap(data)

                            buffer.order(ByteOrder.LITTLE_ENDIAN)

                            val map = LongIntHashMap(data.size / 12)

                            for (i in data.indices step 12) {

                                val productID = buffer.int
                                val orgID = buffer.int
                                val onHand = buffer.int

                                val key = productID.toLong() shl 32 or (orgID.toLong() and 0xffffffffL)

                                map.put(key, onHand)
                            }

                            stockMap = map

                        } else {
                            logger.warn("Not reloading stock as response code is ${httpResponse.statusLine.statusCode}")
                        }
                    }

                }
            } catch (ex: Exception) {
                logger.warn("Caught exception reloading stock ${ex.message}")
            }
        }
    }

    override fun doStart() {
        var reloadTimeString = System.getenv("STOCK_REFRESH_INTERVAL_MINUTES")

        var reloadTime = reloadTimeString?.toLongOrNull() ?: 60L

        schedule = scheduler.scheduleAtFixedRate({
            doReload()
        }, 0L, reloadTime, TimeUnit.MINUTES)
    }


//    private fun reloadVariationStock(httpClientFactory: HttpClientBuilder, url: String) {
//        try {
//            httpClientFactory.build().use {
//
//                var request = HttpGet(url)
//
//                it.execute(request).use { httpResponse ->
//
//                    logger.info("Received response with Status ${httpResponse.statusLine.statusCode}")
//
//                    if (httpResponse.statusLine.statusCode == HttpStatus.SC_OK) {
//
//                        var data = httpResponse.entity.content.readBytes()
//
//                        logger.info("Response size ${data.size}")
//
//                        var parsed : ProductVariationData.ProductVariationStockLanguageData? = null
//
//                        logger.info("Parsing data")
//
//                        try {
//                            logger.info(data)
//                            parsed = ProductVariationData.ProductVariationStockLanguageData.parseFrom(data)
//                        } catch (ex: Error) {
//                            logger.info("Caught ex", ex)
//                        }
//                        logger.info("Parsed data")
//
//                        var hm1 = HashMap<String, HashMap<String, LongObjectHashMap<IntArray>>>()
//
//                        for (l in parsed!!.languageDataMap) {
//
//                            val hm2 = HashMap<String, LongObjectHashMap<IntArray>>()
//
//                            hm1[l.key] = hm2
//
//                            for (f in l.value.fieldDataMap) {
//
//                                var hm3 = LongObjectHashMap<IntArray>()
//
//                                hm2[f.key] = hm3
//
//                                for (p in f.value.dataList) {
//
//                                    val key = p.productID.toLong() shl 32 or (p.organizationUnitID.toLong() and 0xffffffffL)
//
//                                    hm3.put(key, p.variationsWithStockList.toIntArray())
//                                }
//                            }
//                        }
//
//                        ReloadStockScheduler.variationStockData = hm1
//
//                        val variationsMap : IntObjectHashMap<String> = IntObjectHashMap(parsed.variationsMapCount)
//                        var variationReverseMap = HashMap<String, Int>()
//
//                        for (v in parsed.variationsMapMap) {
//                            variationsMap.put(v.key, v.value)
//                            variationReverseMap[v.value] = v.key
//                        }
//
//                        ReloadStockScheduler.variationMap = variationsMap
//                        ReloadStockScheduler.variationReverseMap = variationReverseMap
//
//                        logger.info("Built maps")
//
//                    } else {
//                        logger.warn("Not reloading stock as response code is ${httpResponse.statusLine.statusCode}")
//                    }
//                }
//
//            }
//        } catch (ex: Exception) {
//            logger.warn("Caught exception reloading stock ${ex.message}")
//        }
//    }


    override fun doStop() {
        schedule?.cancel(false)
    }

    override fun doClose() {}

}