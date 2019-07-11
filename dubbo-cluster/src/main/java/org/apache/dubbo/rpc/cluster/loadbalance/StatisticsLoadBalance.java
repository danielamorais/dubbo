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
package org.apache.dubbo.rpc.cluster.loadbalance;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.URLBuilder;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.cluster.CpuUsageFactory;
import org.apache.dubbo.rpc.cluster.loadbalance.statistics.CpuUsageListener;
import org.apache.dubbo.rpc.cluster.loadbalance.statistics.CpuUsageService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.dubbo.common.constants.CommonConstants.INTERFACE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.METHODS_KEY;
import static org.apache.dubbo.remoting.Constants.CHECK_KEY;
import static org.apache.dubbo.rpc.Constants.REFERENCE_FILTER_KEY;

public class StatisticsLoadBalance extends AbstractLoadBalance {

    private Map<String, Float> cpuUsage = new ConcurrentHashMap<>();
    private CpuUsageFactory cpuUsageFactory;

    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        int index = (int)(invokers.size() * Math.random());
        Invoker<T> invoker = invokers.get(index);
        if (invoker.getUrl() != null) {
            print();
            cpuUsage.computeIfAbsent(invoker.getUrl().getAddress(), value -> {
                URLBuilder urlBuilder = URLBuilder.from(invoker.getUrl());
                urlBuilder.setPath(CpuUsageService.class.getName());
                urlBuilder.addParameter(INTERFACE_KEY, CpuUsageService.class.getName());
                urlBuilder.addParameter("bean.name", CpuUsageService.class.getName());
                urlBuilder.addParameter(METHODS_KEY, "addListener,removeListener");
                urlBuilder.addParameters(CHECK_KEY, String.valueOf(false), REFERENCE_FILTER_KEY, "");
                urlBuilder.addParameter("addListener.1.callback", true);
                CpuUsageService cpuUsageService = cpuUsageFactory.createCpuUsageService(invoker.getUrl());

                cpuUsageService.addListener("statisticsloadbalance", new CpuUsageListenerImpl());
                return 0.0f;
            });
        }
        return invoker;
    }

    private void print() {
        for (Map.Entry<String, Float> entry : cpuUsage.entrySet()) {
            System.err.println(entry.getKey() + "/" + entry.getValue());
        }
    }

    public void setCpuUsageFactory(CpuUsageFactory cpuUsageFactory) {
        this.cpuUsageFactory = cpuUsageFactory;
    }

    public class CpuUsageListenerImpl implements CpuUsageListener {
        @Override
        public void cpuChanged(String ip, Float cpu) {
            cpuUsage.put(ip, cpu);
        }
    }
}
