package io.newblack.elastic

import io.newblack.eva.elasticsearch.*
import org.elasticsearch.client.Client
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver
import org.elasticsearch.cluster.node.DiscoveryNodes
import org.elasticsearch.cluster.service.ClusterService
import org.elasticsearch.common.io.stream.NamedWriteableRegistry
import org.elasticsearch.common.logging.Loggers
import org.elasticsearch.common.settings.ClusterSettings
import org.elasticsearch.common.settings.IndexScopedSettings
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.settings.SettingsFilter
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.env.Environment
import org.elasticsearch.env.NodeEnvironment
import org.elasticsearch.script.*
import org.elasticsearch.threadpool.ThreadPool
import org.elasticsearch.watcher.ResourceWatcherService
import org.elasticsearch.index.Index
import org.elasticsearch.index.IndexModule
import org.elasticsearch.index.IndexSettings
import org.elasticsearch.index.analysis.TokenFilterFactory
import org.elasticsearch.index.shard.IndexEventListener
import org.elasticsearch.indices.analysis.AnalysisModule
import org.elasticsearch.indices.cluster.IndicesClusterStateService
import org.elasticsearch.plugins.*
import org.elasticsearch.repositories.RepositoriesService
import org.elasticsearch.rest.RestController
import org.elasticsearch.rest.RestHandler
import java.util.ArrayList
import java.util.function.Supplier

class EVAPlugin : Plugin(), ScriptPlugin, SearchPlugin, AnalysisPlugin, ActionPlugin {

    private val logger = Loggers.getLogger(EVAPlugin::class.java, "eva")

    private lateinit var scheduler: ReloadStockScheduler

    private lateinit var watcher: SynonymWatcher

    override fun createComponents(client: Client?, clusterService: ClusterService?, threadPool: ThreadPool?, resourceWatcherService: ResourceWatcherService?, scriptService: ScriptService?, xContentRegistry: NamedXContentRegistry?, environment: Environment?, nodeEnvironment: NodeEnvironment?, namedWriteableRegistry: NamedWriteableRegistry?, indexNameExpressionResolver: IndexNameExpressionResolver?, repositoriesServiceSupplier: Supplier<RepositoriesService>?): MutableCollection<Any> {
        scheduler = ReloadStockScheduler(clusterService!!.settings, threadPool!!.scheduler())

        watcher = SynonymWatcher(threadPool.scheduler())

        return mutableListOf(scheduler, watcher)
    }

    override fun getScriptEngine(settings: Settings?, contexts: MutableCollection<ScriptContext<*>>?): ScriptEngine {
        return EVAScriptEngine()
    }

//    override fun getAggregations(): ArrayList<SearchPlugin.AggregationSpec> {
//        val r = ArrayList<SearchPlugin.AggregationSpec>()
//
//        r.add(
//                SearchPlugin.AggregationSpec(
//                        ProductVariationStockAggregationBuilder.NAME,
//                ::ProductVariationStockAggregationBuilder,
//                ProductVariationStockAggregationBuilder.Companion::parse)
//                .addResultReader(::ProductVariationStockAggregation)
//        )
//
//        return r
//    }

    override fun onIndexModule(indexModule: IndexModule) {
        indexModule.addIndexEventListener(object : IndexEventListener {
            override fun afterIndexRemoved(
                    index: Index,
                    indexSettings: IndexSettings,
                    reason: IndicesClusterStateService.AllocatedIndices.IndexRemovalReason
            ) {
                logger.info("after index removed called {} because {}", index.name, reason.name)

                watcher.stopWatching(index)
            }
        })
    }

    override fun getRestHandlers(settings: Settings?, restController: RestController?, clusterSettings: ClusterSettings?, indexScopedSettings: IndexScopedSettings?, settingsFilter: SettingsFilter?, indexNameExpressionResolver: IndexNameExpressionResolver?, nodesInCluster: Supplier<DiscoveryNodes>?): MutableList<RestHandler> {
        return mutableListOf(ReloadStockAction())
    }

    override fun getTokenFilters() = mutableMapOf(
            "flexible_synonym" to AnalysisModule.AnalysisProvider<TokenFilterFactory> { indexSettings, _, name, settings ->
                FlexibleSynonymTokenFilterFactory(indexSettings, name, settings, SynonymResourceFactory(), watcher)
            }
    )
}
