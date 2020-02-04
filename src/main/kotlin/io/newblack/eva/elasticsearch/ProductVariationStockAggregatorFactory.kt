package io.newblack.eva.elasticsearch

import com.carrotsearch.hppc.IntObjectHashMap
import com.carrotsearch.hppc.LongObjectHashMap
import org.elasticsearch.index.query.QueryShardContext
import org.elasticsearch.search.aggregations.Aggregator
import org.elasticsearch.search.aggregations.AggregatorFactories
import org.elasticsearch.search.aggregations.AggregatorFactory
import org.elasticsearch.search.aggregations.InternalAggregation
import org.elasticsearch.search.aggregations.NonCollectingAggregator
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregator
import org.elasticsearch.search.aggregations.support.ValuesSource
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregatorFactory
import org.elasticsearch.search.aggregations.support.ValuesSourceConfig
import org.elasticsearch.search.internal.SearchContext

import java.io.IOException
import java.util.ArrayList

/**
 * The factory of aggregators.
 * ValuesSourceAggregatorFactory extends [AggregatorFactory]
 */
internal class ProductVariationStockAggregatorFactory @Throws(IOException::class)
constructor(name: String,
            context: QueryShardContext?,
            parent: AggregatorFactory?,
            subFactoriesBuilder: AggregatorFactories.Builder?,
            metaData: Map<String, Any>?,
            private val size: Int?,
            private val stockLookup: LongObjectHashMap<IntArray>,
            private val organizationUnitIDs: IntArray,
            private val variationLookup: IntObjectHashMap<String>
) : AggregatorFactory(name, context, parent, subFactoriesBuilder, metaData) {

    override fun createInternal(searchContext: SearchContext?, parent: Aggregator?, collectsFromSingleBucket: Boolean, pipelineAggregators: MutableList<PipelineAggregator>?, metaData: MutableMap<String, Any>?): Aggregator {
        return ProductVariationStockAggregator(
                name, factories, searchContext,
                parent, pipelineAggregators ?: arrayListOf(), metaData, this.size, this.stockLookup, this.organizationUnitIDs, this.variationLookup)
    }
}

