/*
 * Copyright © 2017 bupt.dtj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.bupt.delaydetect.impl;

import org.bupt.delaydetect.impl.util.InitialFlowWriter;
import org.bupt.delaydetect.impl.util.InventoryReader;
import org.bupt.delaydetect.impl.util.PacketDispatcher;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.NotificationProviderService;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.echo.service.rev150305.SalEchoService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.delaydetect.config.rev181107.DelaydetectConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.delaydetect.rev150105.DelaydetectService;
import org.opendaylight.yangtools.concepts.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DelaydetectProvider {

    private static final Logger LOG = LoggerFactory.getLogger(DelaydetectProvider.class);
    private final DataBroker dataBroker;
    private final DelaydetectConfig delaydetectConfig;
    private final NotificationProviderService notificationProviderService;
    private final PacketProcessingService packetProcessingService;
    private final RpcProviderRegistry rpcProviderRegistry;
    private final SalFlowService salFlowService;
    private final SalEchoService salEchoService;
    private BindingAwareBroker.RpcRegistration<DelaydetectService> rpcRegistration;

    private Map<String, Long> delayMap = new ConcurrentHashMap<>();
    private Map<String, Long> echoDelayMap = new ConcurrentHashMap<>();
    private Registration delayRegistration = null, topoNodeListenerReg = null;
    private static final int CPUS = Runtime.getRuntime().availableProcessors();
    private final ScheduledExecutorService service = Executors.newScheduledThreadPool(CPUS);

    public DelaydetectProvider(final DataBroker dataBroker, DelaydetectConfig config, NotificationProviderService notificationProviderService, PacketProcessingService packetProcessingService, RpcProviderRegistry rpcProviderRegistry, SalFlowService salFlowService, SalEchoService salEchoService) {
        this.dataBroker = dataBroker;
        this.delaydetectConfig = config;
        this.notificationProviderService = notificationProviderService;
        this.packetProcessingService = packetProcessingService;
        this.rpcProviderRegistry = rpcProviderRegistry;
        this.salFlowService = salFlowService;
        this.salEchoService = salEchoService;
    }

    /**
     * Method called when the blueprint container is created.
     */
    public void init() {
        InitialFlowWriter flowWriter = new InitialFlowWriter(salFlowService);
        topoNodeListenerReg = flowWriter.registerAsDataChangeListener(dataBroker);
        PacketDispatcher packetDispatcher = new PacketDispatcher();
        packetDispatcher.setPacketProcessingService(packetProcessingService);
        InventoryReader inventoryReader = new InventoryReader(dataBroker);
        inventoryReader.setRefreshData(true);
        inventoryReader.readInventory();
        packetDispatcher.setInventoryReader(inventoryReader);

        DelaySender delaySender = new DelaySender(dataBroker, delaydetectConfig, packetProcessingService, salEchoService, packetDispatcher, inventoryReader, echoDelayMap);
        service.scheduleAtFixedRate(delaySender, delaydetectConfig.getQuerryDelay() * 100, delaydetectConfig.getQuerryDelay() * 100, TimeUnit.MILLISECONDS);
        DelayListener delayListener = new DelayListener(delaydetectConfig, delayMap, echoDelayMap);
        delayRegistration = notificationProviderService.registerNotificationListener(delayListener);
        DelayServiceImpl delayService = new DelayServiceImpl(delayMap);
        rpcRegistration = rpcProviderRegistry.addRpcImplementation(DelaydetectService.class, delayService);
        LOG.info("DelaydetectProvider Session Initiated");
    }

    /**
     * Method called when the blueprint container is destroyed.
     */
    public void close() {
        try {
            if (delayRegistration != null) {
                delayRegistration.close();
            }
            if (topoNodeListenerReg != null) {
                topoNodeListenerReg.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        LOG.info("DelaydetectProvider Closed");
    }
}