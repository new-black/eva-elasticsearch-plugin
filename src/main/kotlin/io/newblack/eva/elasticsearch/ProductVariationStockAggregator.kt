package io.newblack.eva.elasticsearch

import com.carrotsearch.hppc.IntIntHashMap
import com.carrotsearch.hppc.IntObjectHashMap
import com.carrotsearch.hppc.LongObjectHashMap
import org.apache.lucene.index.LeafReaderContext
import org.elasticsearch.index.fielddata.ScriptDocValues
import org.elasticsearch.search.aggregations.Aggregator
import org.elasticsearch.search.aggregations.AggregatorFactories
import org.elasticsearch.search.aggregations.InternalAggregation
import org.elasticsearch.search.aggregations.LeafBucketCollector
import org.elasticsearch.search.aggregations.LeafBucketCollectorBase
import org.elasticsearch.search.aggregations.bucket.BucketsAggregator
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregator
import org.elasticsearch.search.internal.SearchContext

import java.io.IOException
import java.util.ArrayList

class ProductVariationStockAggregator @Throws(IOException::class)
constructor(
        name: String,
        factories: AggregatorFactories,
        context: SearchContext?,
        parent: Aggregator?,
        pipelineAggregators: List<PipelineAggregator>,
        metaData: Map<String, Any>?,
        private val size: Int?,
        private val stockLookup: LongObjectHashMap<IntArray>,
        private val organizationUnitIDs: IntArray,
        private val variationLookup: IntObjectHashMap<String>
) : BucketsAggregator(name, factories, context, parent, pipelineAggregators, metaData) {

    private var stockBuckets = IntIntHashMap()

    /**
     * The collector collects the docs, including or not some score (depending of the including of a Scorer) in the
     * collect() process.
     *
     * The LeafBucketCollector is a "Per-leaf bucket collector". It collects docs for the account of buckets.
     */
    @Throws(IOException::class)
    public override fun getLeafCollector(ctx: LeafReaderContext, sub: LeafBucketCollector): LeafBucketCollector {

        val searchContext = context

        val lookup = searchContext.lookup()

        val leafSearchLookup = lookup.getLeafSearchLookup(ctx)

        return object : LeafBucketCollectorBase(sub, null) {

            @Throws(IOException::class)
            override fun collect(doc: Int, owningBucketOrdinal: Long) {

                leafSearchLookup.setDocument(doc)

                val productID = (leafSearchLookup.doc()["product_id"] as ScriptDocValues.Longs)[0].toInt()

                for (o in organizationUnitIDs) {
                    val key = productID.toLong() shl 32 or (o.toLong() and 0xffffffffL)

                    var s = stockLookup[key]

                    if (s != null) {
                        for (v in s) {
                            stockBuckets.putOrAdd(v, 1, 1)
                        }
                    }
                }
            }
        }
    }

    @Throws(IOException::class)
    override fun buildAggregation(owningBucketOrdinal: Long): InternalAggregation {
        assert(owningBucketOrdinal == 0L)

        val buckets = ArrayList<ProductVariationStockAggregation.InternalBucket>()

        for (x in stockBuckets) {

            this.variationLookup[x.key]?.let {
                var bucket = ProductVariationStockAggregation.InternalBucket(x.value.toLong(), it)
                buckets.add(bucket)
            }
        }

        buckets.sortByDescending { it._docCount }

        return ProductVariationStockAggregation(name, buckets.take(this.size
                ?: 10), pipelineAggregators(), metaData())
    }

    override fun buildEmptyAggregation(): InternalAggregation {
        return ProductVariationStockAggregation(name, null, pipelineAggregators(), metaData())
    }

    override fun doClose() {
    }
}
