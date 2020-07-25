package com.github.simkuenzi.webplay.record;

import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;

public class Recorder {

    public Recording open(int port, String startPath) throws Exception {
        System.out.printf("Recording on http://localhost:%d%s%n", port, startPath);
        return new Recording(ServerSocketChannel.open().bind(new InetSocketAddress(port)));
    }
}
