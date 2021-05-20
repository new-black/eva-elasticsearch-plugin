package io.newblack.eva.elasticsearch

import org.apache.http.HttpHeaders
import org.apache.http.HttpStatus
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpHead
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.synonym.SolrSynonymParser
import org.apache.lucene.analysis.synonym.SynonymMap
import org.apache.lucene.analysis.synonym.WordnetSynonymParser
import org.elasticsearch.SpecialPermission
import org.elasticsearch.common.logging.Loggers
import java.lang.Exception
import java.security.AccessController
import java.security.PrivilegedAction

class SynonymResource(
        private val expand: Boolean,
        private val analyzer: Analyzer,
        private val format: String,
        private val location: String,
        private val createClient: () -> CloseableHttpClient
) {

    companion object {
        private val EMPTY_SYNONYM_MAP = SynonymMap.Builder().build()
    }

    private val logger = Loggers.getLogger(SynonymResource::class.java, "flexible-synonyms")

    private var lastModified: String? = null
    private var eTags: String? = null

    fun load(): SynonymMap {
        val request = HttpGet(location)

        createClient().use { client ->

            SpecialPermission.check()

            try {
                return AccessController.doPrivileged(object : PrivilegedAction<SynonymMap> {
                    override fun run(): SynonymMap {
                        client.execute(request).use {
                            logger.debug("response status for GET: {}", it.statusLine)

                            if (it.statusLine.statusCode == HttpStatus.SC_OK) {
                                // Update last modified and etag for next request
                                lastModified = it.getLastHeader(HttpHeaders.LAST_MODIFIED)?.value
                                eTags = it.getLastHeader(HttpHeaders.ETAG)?.value

                                logger.debug("updating last modified with: {}", lastModified)
                                logger.debug("updating etag with: {}", eTags)

                                if (it.entity.contentLength == 0L) {
                                    return EMPTY_SYNONYM_MAP
                                }

                                val parser = createParser(format, expand, analyzer)
                                parser.parse(it.entity.content.reader())
                                return parser.build()
                            }

                            return EMPTY_SYNONYM_MAP
                        }
                    }

                })
            } catch (ex: Exception) {
                logger.warn("Caught exception loading synonyms: ${ex.message}")
                return EMPTY_SYNONYM_MAP
            }
        }
    }

    fun needsReload(): Boolean {
        SpecialPermission.check()

        try {

            return AccessController.doPrivileged(PrivilegedAction {
                logger.debug("checking if reload is required for: {}", location)

                val request = HttpHead(location)
                        .apply {
                            lastModified?.let {
                                setHeader(HttpHeaders.IF_MODIFIED_SINCE, it)
                            }
                            eTags?.let {
                                setHeader(HttpHeaders.IF_NONE_MATCH, it)
                            }
                        }

                var reloadRequired = false

                createClient().use { client ->
                    client.execute(request).use {
                        logger.debug("response status for HEAD: {}", it.statusLine)

                        if (it.statusLine.statusCode == HttpStatus.SC_OK) {
                            // Update last modified and etag for next request
                            lastModified = it.getLastHeader(HttpHeaders.LAST_MODIFIED)?.value
                            eTags = it.getLastHeader(HttpHeaders.ETAG)?.value

                            logger.debug("updating last modified with: {}", lastModified)
                            logger.debug("updating etag with: {}", eTags)

                            reloadRequired = true
                        }
                    }
                }

                reloadRequired
            })
        } catch (ex: Exception) {
            logger.warn("Caught exception checking if synonyms need reloading: ${ex.message}")
            return false
        }
    }

}

fun createParser(format: String, expand: Boolean, analyzer: Analyzer): SynonymMap.Parser {
    if ("wordnet".equals(format, true)) {
        return WordnetSynonymParser(true, expand, analyzer)
    }

    return SolrSynonymParser(true, expand, analyzer)
}