/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazon.opendistroforelasticsearch.indexstatemanagement.resthandler

import com.amazon.opendistroforelasticsearch.indexstatemanagement.IndexStateManagementPlugin.Companion.INDEX_STATE_MANAGEMENT_INDEX
import com.amazon.opendistroforelasticsearch.indexstatemanagement.IndexStateManagementPlugin.Companion.POLICY_BASE_URI
import com.amazon.opendistroforelasticsearch.indexstatemanagement.model.Policy
import com.amazon.opendistroforelasticsearch.indexstatemanagement.model.Policy.Companion.POLICY_TYPE
import com.amazon.opendistroforelasticsearch.indexstatemanagement.util.XCONTENT_WITHOUT_TYPE
import com.amazon.opendistroforelasticsearch.indexstatemanagement.util._ID
import com.amazon.opendistroforelasticsearch.indexstatemanagement.util._PRIMARY_TERM
import com.amazon.opendistroforelasticsearch.indexstatemanagement.util._SEQ_NO
import com.amazon.opendistroforelasticsearch.indexstatemanagement.util._VERSION
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler
import org.elasticsearch.common.xcontent.XContentHelper
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.rest.BaseRestHandler
import org.elasticsearch.rest.RestHandler.Route
import org.elasticsearch.rest.BytesRestResponse
import org.elasticsearch.rest.RestChannel
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.RestRequest.Method.GET
import org.elasticsearch.rest.RestRequest.Method.HEAD
import org.elasticsearch.rest.RestResponse
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.rest.action.RestActions
import org.elasticsearch.rest.action.RestResponseListener
import org.elasticsearch.search.fetch.subphase.FetchSourceContext

class RestGetPolicyAction : BaseRestHandler() {

    override fun routes(): List<Route> {
        return listOf(
                Route(GET, "$POLICY_BASE_URI/{policyID}"),
                Route(HEAD, "$POLICY_BASE_URI/{policyID}")
        )
    }

    override fun getName(): String {
        return "get_policy_action"
    }

    override fun prepareRequest(request: RestRequest, client: NodeClient): RestChannelConsumer {
        val policyId = request.param("policyID")
        if (policyId == null || policyId.isEmpty()) {
            throw IllegalArgumentException("Missing policy ID")
        }
        val getRequest = GetRequest(INDEX_STATE_MANAGEMENT_INDEX, policyId)
                .version(RestActions.parseVersion(request))

        if (request.method() == HEAD) {
            getRequest.fetchSourceContext(FetchSourceContext.DO_NOT_FETCH_SOURCE)
        }
        return RestChannelConsumer { channel -> client.get(getRequest, getPolicyResponse(channel)) }
    }

    private fun getPolicyResponse(channel: RestChannel): RestResponseListener<GetResponse> {
        return object : RestResponseListener<GetResponse>(channel) {
            @Throws(Exception::class)
            override fun buildResponse(response: GetResponse): RestResponse {
                if (!response.isExists) {
                    return BytesRestResponse(RestStatus.NOT_FOUND, channel.newBuilder())
                }

                val builder = channel.newBuilder()
                        .startObject()
                        .field(_ID, response.id)
                        .field(_VERSION, response.version)
                        .field(_SEQ_NO, response.seqNo)
                        .field(_PRIMARY_TERM, response.primaryTerm)
                if (!response.isSourceEmpty) {
                    XContentHelper.createParser(
                        channel.request().xContentRegistry,
                        LoggingDeprecationHandler.INSTANCE,
                        response.sourceAsBytesRef,
                        XContentType.JSON
                    ).use { xcp ->
                        val policy = Policy.parseWithType(xcp, response.id, response.seqNo, response.primaryTerm)
                        builder.field(POLICY_TYPE, policy, XCONTENT_WITHOUT_TYPE)
                    }
                }
                builder.endObject()
                return BytesRestResponse(RestStatus.OK, builder)
            }
        }
    }
}
