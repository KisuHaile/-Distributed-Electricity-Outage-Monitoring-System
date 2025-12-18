package com.electricity.service;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.InetSocketAddress;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.electricity.model.Peer;

public class DiscoveryService implements Runnable {
    private static final String MULTICAST_GROUP = "239.0.0.1";
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
        System.out.println(
                "[Discovery] Starting Discovery Service (Multicast: " + MULTICAST_GROUP + ":" + MULTICAST_PORT + ")");
        // Start listener thread
        new Thread(this::listen, "DiscoveryListener").start();

        // Start announcer thread
        new Thread(this::announceLoop, "DiscoveryAnnouncer").start();
    }

    private void listen() {
        try (MulticastSocket socket = new MulticastSocket(MULTICAST_PORT)) {
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            InetSocketAddress groupAddress = new InetSocketAddress(group, MULTICAST_PORT);

            // Try to join on all interfaces or at least the default
            try {
                socket.joinGroup(groupAddress, null);
            } catch (Exception e) {
                socket.joinGroup(group); // Fallback
            }

            System.out.println("[Discovery] Listening for peers...");
            byte[] buf = new byte[256];
            while (running) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String msg = new String(packet.getData(), 0, packet.getLength());
                processMessage(msg, packet.getAddress().getHostAddress());
            }
            socket.leaveGroup(group);
        } catch (IOException e) {
            System.err.println("[Discovery] Listener Error: " + e.getMessage());
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

                try (MulticastSocket s = new MulticastSocket()) {
                    s.setTimeToLive(2); // Local subnet
                    DatagramPacket packet = new DatagramPacket(buf, buf.length, group, MULTICAST_PORT);
                    s.send(packet);
                } catch (Exception e) {
                    System.err.println("[Discovery] Announce Send Error: " + e.getMessage());
                }

                Thread.sleep(5000); // Announce every 5 seconds
            }
        } catch (Exception e) {
            if (running)
                System.err.println("[Discovery] Announcer Error: " + e.getMessage());
        }
    }

    private void processMessage(String msg, String senderIp) {
        try {
            String[] parts = msg.split("\\|");
            if (parts.length >= 3 && "HELLO".equals(parts[0])) {
                int id = Integer.parseInt(parts[1]);
                int port = Integer.parseInt(parts[2]);

                if (id != myServerId) {
                    boolean isLeader = parts.length > 3 && Boolean.parseBoolean(parts[3]);
                    System.out.println("[Discovery] Found Peer Server #" + id + " (Leader: " + isLeader + ") at "
                            + senderIp + ":" + port);
                    Peer p = new Peer(id, senderIp, port, isLeader);
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
