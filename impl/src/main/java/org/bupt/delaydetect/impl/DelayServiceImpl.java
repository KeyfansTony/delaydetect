/*
 * Copyright © 2017 bupt.dtj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.bupt.delaydetect.impl;

import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.delaydetect.rev150105.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.delaydetect.rev150105.getglobaldelay.output.DelayList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.delaydetect.rev150105.getglobaldelay.output.DelayListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.delaydetect.rev150105.getglobaldelay.output.DelayListKey;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

public class DelayServiceImpl implements DelaydetectService {
    private Map<String, Long> delayMap;

    public DelayServiceImpl(Map<String, Long> delayMap) {
        this.delayMap = delayMap;
    }

    @Override
    public Future<RpcResult<GetDelayOutput>> getDelay(GetDelayInput input){
        String nodeConnector = input.getNodeConnector();
        GetDelayOutputBuilder getDelayOutputBuilder = new GetDelayOutputBuilder();
        getDelayOutputBuilder.setDelay(delayMap.get(nodeConnector));
        return RpcResultBuilder.success(getDelayOutputBuilder.build()).buildFuture();
    }

    @Override
    public Future<RpcResult<GetGlobalDelayOutput>> getGlobalDelay(){
        GetGlobalDelayOutputBuilder getGlobalDelayOutputBuilder = new GetGlobalDelayOutputBuilder();
        List<DelayList> delayLists = new ArrayList<>();
        for (String ncid : delayMap.keySet()) {
            DelayListBuilder delayListBuilder = new DelayListBuilder();
            delayListBuilder.setKey(new DelayListKey(ncid));
            delayListBuilder.setDelay(delayMap.get(ncid));
            delayLists.add(delayListBuilder.build());
        }
        getGlobalDelayOutputBuilder.setDelayList(delayLists);
        return RpcResultBuilder.success(getGlobalDelayOutputBuilder.build()).buildFuture();
    }
}
