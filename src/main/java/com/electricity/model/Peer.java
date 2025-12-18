package com.electricity.model;

public class Peer {
    private int id;
    private String host;
    private int port;
    private boolean isLeader;

    public Peer(int id, String host, int port, boolean isLeader) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.isLeader = isLeader;
    }

    public int getId() {
        return id;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean isLeader() {
        return isLeader;
    }
}
