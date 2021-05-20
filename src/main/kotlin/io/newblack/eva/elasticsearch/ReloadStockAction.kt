package io.newblack.eva.elasticsearch

import io.newblack.elastic.ReloadStockScheduler
import org.apache.logging.log4j.core.appender.rolling.action.AbstractPathAction
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.rest.*
import org.elasticsearch.rest.action.cat.AbstractCatAction

class ReloadStockAction : BaseRestHandler() {

    override fun routes(): MutableList<RestHandler.Route> {
        return mutableListOf(RestHandler.Route(RestRequest.Method.POST, "/eva/stock/reload"))
    }

    override fun getName(): String {
        return "eva_stock"
    }

    override fun prepareRequest(request: RestRequest?, client: NodeClient?): RestChannelConsumer {
        ReloadStockScheduler.doReload()
        val c = request?.content()
        return ReloadConsumer()
    }

    class ReloadConsumer : RestChannelConsumer {
        override fun accept(t: RestChannel?) {
            t?.sendResponse(BytesRestResponse(RestStatus.OK, ""))
        }
    }
}