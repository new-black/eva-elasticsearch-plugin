package io.newblack.eva.elasticsearch

import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.search.aggregations.Aggregation
import org.elasticsearch.search.aggregations.InternalAggregation
import org.elasticsearch.search.aggregations.InternalAggregations
import org.elasticsearch.search.aggregations.InternalMultiBucketAggregation
import org.elasticsearch.search.aggregations.KeyComparable
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregator

import java.io.IOException
import java.util.ArrayList
import java.util.Objects
import java.util.TreeMap

/**
 * An internal implementation of [InternalMultiBucketAggregation] which extends [Aggregation].
 * Mainly, returns the builder and makes the reduce of buckets.
 */
class ProductVariationStockAggregation : InternalMultiBucketAggregation<ProductVariationStockAggregation, ProductVariationStockAggregation.InternalBucket> {

    override fun reduceBucket(buckets: MutableList<InternalBucket>?, context: ReduceContext?): InternalBucket {
        var reduced: InternalBucket? = null

        if(buckets != null) {
            val aggregationsList = ArrayList<InternalAggregations>(buckets.size)

            for (bucket in buckets) {
                if (reduced == null) {
                    reduced = bucket
                } else {
                    reduced._docCount += bucket.docCount
                }
                bucket.aggregations?.let { aggregationsList.add(it) }
            }
        }

        return reduced!!
    }


    private var buckets: List<InternalBucket>? = null

    class InternalBucket : InternalMultiBucketAggregation.InternalBucket, KeyComparable<InternalBucket> {

        var _docCount: Long = 0
        var basename: String

        constructor(docCount: Long,  basename: String) {
            this._docCount = docCount
            this.basename = basename
        }

        /**
         * Read from a stream.
         */
        @Throws(IOException::class)
        constructor(`in`: StreamInput) {
            _docCount = `in`.readLong()
            basename = `in`.readString()
        }

        /**
         * Write to a stream.
         */
        @Throws(IOException::class)
        override fun writeTo(out: StreamOutput) {
            out.writeLong(docCount)
            aggregations?.writeTo(out)
            out.writeString(basename)
        }

        override fun getKey(): String {
            return basename
        }

        override fun getKeyAsString(): String {
            return basename
        }

        override fun compareKey(other: InternalBucket): Int {
            return basename.compareTo(other.basename)
        }

        override fun getDocCount(): Long {
            return _docCount
        }

        override fun getAggregations(): InternalAggregations? {
            return null
        }

        internal fun reduce(buckets: List<InternalBucket>, context: InternalAggregation.ReduceContext): InternalBucket? {
            val aggregationsList = ArrayList<InternalAggregations>(buckets.size)
            var reduced: InternalBucket? = null
            for (bucket in buckets) {
                if (reduced == null) {
                    reduced = bucket
                } else {
                    reduced._docCount += bucket.docCount
                }
                bucket.aggregations?.let { aggregationsList.add(it) }
            }

            return reduced
        }

        @Throws(IOException::class)
        override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
            builder.startObject()
            builder.field(Aggregation.CommonFields.DOC_COUNT.preferredName, docCount)
            aggregations?.toXContentInternal(builder, params)
            builder.endObject()
            return builder
        }
    }


    constructor(
            name: String,
            buckets: List<InternalBucket>?,
            pipelineAggregators: List<PipelineAggregator>,
            metaData: Map<String, Any>?
    ) : super(name, pipelineAggregators, metaData) {
        this.buckets = buckets
    }

    /**
     * Read from a stream.
     */
    @Throws(IOException::class)
    constructor(`in`: StreamInput) : super(`in`) {
        val bucketsSize = `in`.readInt()
        var newBuckets = ArrayList<InternalBucket>(bucketsSize)

        for (i in 0 until bucketsSize) {
            newBuckets.add(InternalBucket(`in`))
        }

        this.buckets = newBuckets
    }

    /**
     * Write to a stream.
     */
    @Throws(IOException::class)
    override fun doWriteTo(out: StreamOutput) {
        out.writeInt(buckets!!.size)
        for (bucket in buckets!!) {
            bucket.writeTo(out)
        }
    }

    override fun getWriteableName(): String {
        return ProductVariationStockAggregationBuilder.NAME
    }

    override fun create(buckets: MutableList<InternalBucket>): ProductVariationStockAggregation {
        return ProductVariationStockAggregation(this.name, buckets, this.pipelineAggregators(), this.metaData)
    }

    override fun createBucket(aggregations: InternalAggregations, prototype: InternalBucket): InternalBucket {
        return InternalBucket(prototype.docCount, prototype.basename)
    }

    override fun getBuckets(): List<InternalBucket>? {
        return buckets
    }

    /**
     * Reduces the given aggregations to a single one and returns it.
     */
    override fun doReduce(aggregations: List<InternalAggregation>, reduceContext: InternalAggregation.ReduceContext): ProductVariationStockAggregation {
        var buckets: MutableMap<String, List<InternalBucket>>? = null

        // extract buckets from aggregations
        for (aggregation in aggregations) {
            val productVariationStockAggregation = aggregation as ProductVariationStockAggregation
            if (buckets == null) {
                buckets = TreeMap()
            }

            for (bucket in productVariationStockAggregation.buckets!!) {
                var existingBuckets: MutableList<InternalBucket>? = buckets[bucket.basename] as MutableList<InternalBucket>?
                if (existingBuckets == null) {
                    existingBuckets = ArrayList(aggregations.size)
                    buckets[bucket.basename] = existingBuckets
                }
                existingBuckets.add(bucket)
            }
        }

        // reduce and sort buckets depending of ordering rules
        val size = if (!reduceContext.isFinalReduce) buckets!!.size else buckets!!.size
        val ordered = ArrayList<InternalBucket>(size)
        for (sameTermBuckets in buckets.values) {

            val b = sameTermBuckets[0].reduce(sameTermBuckets, reduceContext)

            if (b != null) {
                ordered.add(b)
            }
        }

        ordered.sortByDescending { it._docCount }

        return ProductVariationStockAggregation(getName(), ordered, pipelineAggregators(), getMetaData())
    }

    @Throws(IOException::class)
    override fun doXContentBody(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        val bucketIterator = buckets!!.iterator()
        builder.startArray(Aggregation.CommonFields.BUCKETS.preferredName)

        var currentBucket: InternalBucket? = null

        while (bucketIterator.hasNext()) {
            currentBucket = bucketIterator.next()

            builder.startObject()
            builder.field(Aggregation.CommonFields.KEY.preferredName, currentBucket.basename)
            builder.field(Aggregation.CommonFields.DOC_COUNT.preferredName, currentBucket.docCount)
            currentBucket.aggregations?.toXContentInternal(builder, params)

            builder.endObject()
        }

        builder.endArray()

        return builder
    }

    override fun hashCode(): Int {
        return Objects.hash(buckets)
    }

    override fun equals(other: Any?): Boolean {
        val that = other as ProductVariationStockAggregation
        return buckets == that.buckets
    }
}
