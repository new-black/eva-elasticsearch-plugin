package io.newblack.eva.elasticsearch

import org.elasticsearch.common.component.AbstractLifecycleComponent
import org.elasticsearch.common.logging.Loggers
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.index.Index
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

data class FilterWithResource(val filter: DynamicSynonymFilter, val resource: SynonymResource)

class SynonymWatcher(
        private val scheduler: ScheduledExecutorService
) : AbstractLifecycleComponent() {
    private val logger = Loggers.getLogger(SynonymWatcher::class.java, "flexible-synonyms")

    private val filters: MutableMap<String, MutableCollection<FilterWithResource>> = ConcurrentHashMap()

    private var schedule: ScheduledFuture<*>? = null

    fun startWatching(index: Index, filter: DynamicSynonymFilter, resource: SynonymResource) {
        logger.info("start watching filter/resource for index {}", index.name)
        filters.getOrPut(index.uuid, ::mutableListOf).add(FilterWithResource(filter, resource))
    }

    fun stopWatching(index: Index) {
        val current = filters.remove(index.uuid)

        if(current != null) {
            logger.info("stop watching all filters/resources for index {}", index.name)
        }
    }

    override fun doStart() {
        schedule = scheduler.scheduleAtFixedRate({
            logger.debug("updating resources of {} filters..", filters.count())

            filters.forEach { it ->
                it.value.forEach {
                    val (filter, resource) = it

                    if (resource.needsReload()) {
                        logger.debug("reload is required")
                        filter.update(resource.load())
                    } else {
                        logger.debug("reload is not required")
                    }
                }
            }

            logger.debug("resources have been updated")
        }, 15L, 15L, TimeUnit.SECONDS)
    }

    override fun doStop() {
        schedule?.cancel(false)
    }

    override fun doClose() {}

}
