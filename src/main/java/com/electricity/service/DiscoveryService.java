package com.electricity.service;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.electricity.model.Peer;

public class DiscoveryService implements Runnable {
    private static final String MULTICAST_GROUP = "230.0.0.1";
    private static final int MULTICAST_PORT = 4446;
    private int myServerId;
    private int myTcpPort;
    private boolean running = true;
    private Consumer<Peer> onPeerDiscovered;
    private Supplier<Boolean> isLeaderSupplier;

    public DiscoveryService(int serverId, int tcpPort, Consumer<Peer> onPeerDiscovered,
            Supplier<Boolean> isLeaderSupplier) {
        this.myServerId = serverId;
        this.myTcpPort = tcpPort;
        this.onPeerDiscovered = onPeerDiscovered;
        this.isLeaderSupplier = isLeaderSupplier;
    }

    @Override
    public void run() {
        // Start listener thread
        new Thread(this::listen).start();

        // Start announcer thread
        new Thread(this::announceLoop).start();
    }

    private void listen() {
        try (MulticastSocket socket = new MulticastSocket(MULTICAST_PORT)) {
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            socket.joinGroup(group);

            byte[] buf = new byte[256];
            while (running) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String msg = new String(packet.getData(), 0, packet.getLength());
                processMessage(msg, packet.getAddress().getHostAddress());
            }
            socket.leaveGroup(group);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void announceLoop() {
        try {
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            while (running) {
                // Msg: HELLO|ServerId|TcpPort|IsLeader
                boolean isLeader = (isLeaderSupplier != null && isLeaderSupplier.get());
                String msg = "HELLO|" + myServerId + "|" + myTcpPort + "|" + isLeader;
                byte[] buf = msg.getBytes();

                // Send standard UDP multicast
                // Note: using MulticastSocket or DatagramSocket to send
                try (MulticastSocket s = new MulticastSocket()) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length, group, MULTICAST_PORT);
                    s.send(packet);
                }

                Thread.sleep(5000); // Announce every 5 seconds
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processMessage(String msg, String senderIp) {
        // Msg: HELLO|ServerId|TcpPort
        try {
            String[] parts = msg.split("\\|");
            if (parts.length >= 3 && "HELLO".equals(parts[0])) {
                int id = Integer.parseInt(parts[1]);
                int port = Integer.parseInt(parts[2]);

                if (id != myServerId) {
                    Peer p = new Peer(id, senderIp, port);
                    onPeerDiscovered.accept(p);
                }
            }
        } catch (Exception e) {
            // ignore bad packets
        }
    }

    public void stop() {
        running = false;
    }
}
