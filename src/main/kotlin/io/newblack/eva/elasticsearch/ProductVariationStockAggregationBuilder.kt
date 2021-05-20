//package io.newblack.eva.elasticsearch
//
//import com.carrotsearch.hppc.LongObjectHashMap
//import io.newblack.elastic.ReloadStockScheduler
//import org.elasticsearch.common.ParseField
//import org.elasticsearch.common.io.stream.StreamInput
//import org.elasticsearch.common.io.stream.StreamOutput
//import org.elasticsearch.common.xcontent.ObjectParser
//import org.elasticsearch.common.xcontent.ToXContent
//import org.elasticsearch.common.xcontent.XContentBuilder
//import org.elasticsearch.common.xcontent.XContentParser
//import org.elasticsearch.search.aggregations.AbstractAggregationBuilder
//import org.elasticsearch.search.aggregations.AggregationBuilder
//import org.elasticsearch.search.aggregations.AggregatorFactories
//import org.elasticsearch.search.aggregations.AggregatorFactory
//import org.elasticsearch.search.aggregations.AggregatorFactories.Builder
//import org.elasticsearch.search.internal.SearchContext
//
//import java.io.IOException
//import java.util.Objects
//
//
///**
// * The builder of the aggregatorFactory. Also implements the parsing of the request.
// */
//
//class ProductVariationStockAggregationBuilder : AbstractAggregationBuilder<ProductVariationStockAggregationBuilder>, MultiBucketAggregationBuilder {
//
//    override fun doWriteTo(out: StreamOutput?) {
//    }
//
//    override fun internalXContent(builder: XContentBuilder, params: ToXContent.Params?): XContentBuilder {
//        builder.startObject()
//
//        return builder.endObject()
//    }
//
//    override fun doBuild(context: QueryShardContext?, parent: AggregatorFactory?, subfactoriesBuilder: Builder?): AggregatorFactory {
//        val stockMap = ReloadStockScheduler.variationStockData[languageID]?.get(variationField) ?: LongObjectHashMap()
//
//        return ProductVariationStockAggregatorFactory(
//                name,
//                context, parent, subfactoriesBuilder, metaData, this.size, stockMap, this.organizationUnitIDs, ReloadStockScheduler.variationMap)
//    }
//
//    constructor(name : String) : super(name)
//
//    constructor(input: StreamInput) : super(input)
//
//    private constructor(clone: ProductVariationStockAggregationBuilder, factoriesBuilder: Builder,
//                        metaData: Map<String, Any>) : super(clone, factoriesBuilder, metaData) {
//    }
//
//    override fun shallowCopy(factoriesBuilder: AggregatorFactories.Builder, metaData: Map<String, Any>): AggregationBuilder {
//        return ProductVariationStockAggregationBuilder(this, factoriesBuilder, metaData)
//    }
//
//    private var size: Int? = null
//
//    /**
//     * Sets the size - indicating how many term buckets should be returned
//     * (defaults to 10)
//     */
//    fun setSize(size: Int): ProductVariationStockAggregationBuilder {
//        if (size <= 0) {
//            throw IllegalArgumentException("[size] must be greater than 0. Found [$size] in [$name]")
//        }
//
//        this.size = size
//
//        return this
//    }
//
//    private lateinit var organizationUnitIDs: IntArray
//
//    fun setOrganizationUnitIDs(orgIDs : IntArray) : ProductVariationStockAggregationBuilder {
//        this.organizationUnitIDs = orgIDs
//        return this
//    }
//
//    private lateinit var languageID: String
//
//    fun setLanguageID(languageID: String) {
//        this.languageID = languageID
//    }
//
//    private lateinit var variationField: String
//
//    private fun setVariationField(field: String) {
//        this.variationField = field
//    }
//
//    override fun getType(): String {
//        return NAME
//    }
//
//    companion object {
//        const val NAME = "variation_stock"
//
//        private val SIZE_FIELD = ParseField("size")
//        private val OU_FIELD = ParseField("organization_unit_ids")
//        private val LANGUAGEID_FIELD = ParseField("language_id")
//        private val VARIATION_FIELD = ParseField("variation_field")
//
//        private val PARSER: ObjectParser<ProductVariationStockAggregationBuilder, Void> = ObjectParser(NAME)
//
//        init {
//            PARSER.declareInt({ obj, size -> obj.setSize(size) }, SIZE_FIELD)
//            PARSER.declareIntArray({ obj, orgIDs -> obj.setOrganizationUnitIDs(orgIDs.toIntArray()) }, OU_FIELD)
//            PARSER.declareString({ obj, languageID -> obj.setLanguageID(languageID)}, LANGUAGEID_FIELD)
//            PARSER.declareString({ obj, field -> obj.setVariationField(field)}, VARIATION_FIELD)
//        }
//
//        @Throws(IOException::class)
//        fun parse(aggregationName: String, parser: XContentParser): AggregationBuilder {
//            return PARSER.parse(parser, ProductVariationStockAggregationBuilder(aggregationName), null)
//        }
//    }
//
//}
//
