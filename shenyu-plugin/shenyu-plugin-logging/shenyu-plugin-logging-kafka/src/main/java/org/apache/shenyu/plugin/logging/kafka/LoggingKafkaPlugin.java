/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shenyu.plugin.logging.kafka;

import org.apache.shenyu.common.dto.RuleData;
import org.apache.shenyu.common.dto.SelectorData;
import org.apache.shenyu.plugin.api.ShenyuPluginChain;
import org.apache.shenyu.plugin.base.AbstractShenyuPlugin;
import org.apache.shenyu.plugin.base.utils.HostAddressUtils;
import org.apache.shenyu.plugin.logging.kafka.body.LoggingServerHttpRequest;
import org.apache.shenyu.plugin.logging.kafka.body.LoggingServerHttpResponse;
import org.apache.shenyu.plugin.logging.kafka.entity.ShenyuRequestLog;
import org.apache.shenyu.plugin.logging.kafka.utils.LogCollectConfigUtils;
import org.apache.shenyu.plugin.logging.kafka.utils.LogCollectUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.apache.shenyu.common.enums.PluginEnum.LOGGING_KAFKA;

/**
 * Integrated kafka collect log.
 */
public class LoggingKafkaPlugin extends AbstractShenyuPlugin {

    private static final String USER_AGENT = "User-Agent";

    private static final String HOST = "Host";

    @Override
    protected Mono<Void> doExecute(final ServerWebExchange exchange, final ShenyuPluginChain chain,
                                   final SelectorData selector, final RuleData rule) {
        ServerHttpRequest request = exchange.getRequest();
        // control sampling
        if (!LogCollectConfigUtils.isSampled(exchange.getRequest())) {
            return chain.execute(exchange);
        }

        ShenyuRequestLog requestInfo = new ShenyuRequestLog();
        requestInfo.setRequestUri(request.getURI().toString());
        requestInfo.setMethod(request.getMethodValue());
        requestInfo.setRequestHeader(LogCollectUtils.getHeaders(request.getHeaders()));
        requestInfo.setQueryParams(request.getURI().getQuery());
        requestInfo.setClientIp(HostAddressUtils.acquireIp(exchange));
        requestInfo.setUserAgent(request.getHeaders().getFirst(USER_AGENT));
        requestInfo.setHost(request.getHeaders().getFirst(HOST));
        requestInfo.setPath(request.getURI().getPath());

        LoggingServerHttpRequest loggingServerHttpRequest = new LoggingServerHttpRequest(request, requestInfo);
        LoggingServerHttpResponse loggingServerHttpResponse = new LoggingServerHttpResponse(exchange.getResponse(),
            requestInfo, DefaultLogCollector.getInstance());
        ServerWebExchange webExchange = exchange.mutate().request(loggingServerHttpRequest)
            .response(loggingServerHttpResponse).build();
        loggingServerHttpResponse.setExchange(webExchange);

        return chain.execute(webExchange).doOnError(loggingServerHttpResponse::logError);
    }

    /**
     * get plugin order.
     *
     * @return order
     */
    @Override
    public int getOrder() {
        return LOGGING_KAFKA.getCode();
    }

    /**
     * get plugin name.
     *
     * @return plugin name
     */
    @Override
    public String named() {
        return LOGGING_KAFKA.getName();
    }
}
