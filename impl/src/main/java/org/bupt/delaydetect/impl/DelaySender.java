/*
 * Copyright © 2017 bupt.dtj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.bupt.delaydetect.impl;

import org.bupt.delaydetect.impl.util.IPv4;
import org.opendaylight.controller.liblldp.*;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.l2switch.arphandler.core.PacketDispatcher;
import org.opendaylight.l2switch.arphandler.inventory.InventoryReader;
import org.opendaylight.openflowplugin.api.OFConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.ControllerActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.output.action._case.OutputActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.echo.service.rev150305.SalEchoService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.echo.service.rev150305.SendEchoInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.echo.service.rev150305.SendEchoInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.echo.service.rev150305.SendEchoOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.OutputPortValues;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.delaydetect.config.rev181107.DelaydetectConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ipv4.rev140528.KnownIpProtocols;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInputBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Future;

public class DelaySender implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(DelaySender.class);
    private final DataBroker dataBroker;
    private final DelaydetectConfig delaydetectConfig;
    private final PacketProcessingService packetProcessingService;
    private final SalEchoService salEchoService;
    private InitialFlowWriter initialFlowWriter;

    public DelaySender(DataBroker dataBroker, DelaydetectConfig delaydetectConfig, PacketProcessingService packetProcessingService, SalEchoService salEchoService) {
        this.dataBroker = dataBroker;
        this.delaydetectConfig = delaydetectConfig;
        this.packetProcessingService = packetProcessingService;
        this.salEchoService = salEchoService;
    }

    public void setInitialFlowWriter(InitialFlowWriter initialFlowWriter) {
        this.initialFlowWriter = initialFlowWriter;
    }

    @Override
    public void run() {

        PacketDispatcher packetDispatcher = new PacketDispatcher();
        packetDispatcher.setPacketProcessingService(packetProcessingService);
        InventoryReader inventoryReader = new InventoryReader(dataBroker);
        inventoryReader.setRefreshData(true);
        inventoryReader.readInventory();
        HashMap<String, NodeConnectorRef> nodeConnectorMap = inventoryReader.getControllerSwitchConnectors();
        for (String nodeId : nodeConnectorMap.keySet()) {
            InstanceIdentifier<Node> nodeInstanceId = InstanceIdentifier.builder(Nodes.class)
                    .child(Node.class, new NodeKey(new NodeId(nodeId))).build();
            initialFlowWriter.addInitialFlows(nodeInstanceId);
        }

        while(delaydetectConfig.isIsActive()) {
            IPv4 iPv4 = new IPv4();
            iPv4.setTtl((byte) 1).setProtocol((byte) KnownIpProtocols.Experimentation1.getIntValue());
            IPv4 echo = new IPv4();
            echo.setTtl((byte) 1).setProtocol((byte) KnownIpProtocols.Experimentation2.getIntValue());
            try {
                //generate a ipv4 packet
                iPv4.setSourceAddress(InetAddress.getByName("0.0.0.1"))
                        .setDestinationAddress(InetAddress.getByName("0.0.0.2"))
                        .setOptions(BitBufferHelper.toByteArray(System.nanoTime()));
                echo.setSourceAddress(InetAddress.getByName("0.0.0.3"))
                        .setDestinationAddress(InetAddress.getByName("0.0.0.4"))
                        .setOptions(BitBufferHelper.toByteArray(System.nanoTime()));

                //generate a ethernet packet
                Ethernet ethernet = new Ethernet();
                EthernetAddress srcMac = new EthernetAddress(new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xee});
                EthernetAddress destMac = new EthernetAddress(new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xef});
                ethernet.setSourceMACAddress(srcMac.getValue())
                        .setDestinationMACAddress(destMac.getValue())
                        .setEtherType(EtherTypes.IPv4.shortValue())
                        .setPayload(iPv4);
                Ethernet echoEthernet = new Ethernet();
                echoEthernet.setSourceMACAddress(new EthernetAddress(new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xec}).getValue())
                        .setDestinationMACAddress(new EthernetAddress(new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xed}).getValue())
                        .setEtherType(EtherTypes.IPv4.shortValue())
                        .setPayload(echo);

                //flood packet
                packetDispatcher.setInventoryReader(inventoryReader);
                // TODO HashMap<String, NodeConnectorRef> nodeConnectorMap = inventoryReader.getControllerSwitchConnectors();

                for (String nodeId : nodeConnectorMap.keySet()) {
                    packetDispatcher.floodPacket(nodeId, ethernet.serialize(), nodeConnectorMap.get(nodeId), null);

                    OutputActionBuilder output = new OutputActionBuilder();
                    output.setMaxLength(OFConstants.OFPCML_NO_BUFFER);
                    Uri value = new Uri(OutputPortValues.CONTROLLER.toString());
                    output.setOutputNodeConnector(value);

                    List<Action> actionList = new ArrayList<>();
                    actionList.add(new ActionBuilder()
                            .setAction(new OutputActionCaseBuilder().setOutputAction(output.build()).build())
                            .build());
                    InstanceIdentifier<Node> nodeInstanceId = InstanceIdentifier.builder(Nodes.class)
                            .child(Node.class, new NodeKey(new NodeId(nodeId))).build();
                    TransmitPacketInput input = new TransmitPacketInputBuilder()
                            .setIngress(nodeConnectorMap.get(nodeId))
                            .setEgress(nodeConnectorMap.get(nodeId))
                            .setNode(new NodeRef(nodeInstanceId))
                            .setPayload(echoEthernet.serialize())
                            .setAction(actionList)
                            .build();
                    packetProcessingService.transmitPacket(input);
                    /*InstanceIdentifier<Node> nodeInstanceId = InstanceIdentifier.builder(Nodes.class)
                            .child(Node.class, new NodeKey(new NodeId(nodeId))).build();
                    SendEchoInput sendEchoInput = new SendEchoInputBuilder()
                            .setData(BitBufferHelper.toByteArray(System.nanoTime()))
                            .setNode(new NodeRef(nodeInstanceId)).build();
                    long Time1 = System.nanoTime();
                    Future<RpcResult<SendEchoOutput>> result = salEchoService.sendEcho(sendEchoInput);
                    while(!result.isDone()){ }
                    long Time2 = System.nanoTime();
                    long delay0 = Time2 - Time1;*/

                    LOG.info(nodeId + ": delay success. ");
                }
            } catch (ConstructionException | UnknownHostException | PacketException e) {
                e.printStackTrace();
            }

            try {
                Thread.sleep(delaydetectConfig.getQuerryDelay() * 100);
            } catch (InterruptedException e) {
                e.printStackTrace();

            }
        }

    }

}
