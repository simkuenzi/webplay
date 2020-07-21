package com.github.simkuenzi.webplay;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.file.StandardWatchEventKinds.*;

public class Recorder {

    public static void main(String[] args) throws Exception {
        Recorder recorder = new Recorder();
        recorder.stopFile = Path.of("stop");
        recorder.record();
    }

    private Path outputFile;
    private int port;
    private int portOfApp;
    private String includedContentTypes;
    private Path stopFile;

    private volatile boolean running;


    public void record() throws Exception {
        running = true;
        Thread recordThread = recordThread();
        recordThread.start();

        WatchService watchService = FileSystems.getDefault().newWatchService();
        stopFile.toAbsolutePath().getParent().register(watchService, ENTRY_MODIFY);

        while (running) {
            final WatchKey wk = watchService.take();
            for (WatchEvent<?> event : wk.pollEvents()) {
                final Path changed = (Path) event.context();
                if (Files.isSameFile(changed, stopFile)) {
                    running = false;
                } else {
                    wk.reset();
                }
            }
        }

        System.out.println("Interrupting...");
        recordThread.interrupt();
        System.out.println("Joining...");
        recordThread.join();
    }

    private Thread recordThread() {
        return new Thread(null, () -> {
            try {
                ServerSocketChannel serverSocket = ServerSocketChannel.open();
                serverSocket.bind(new InetSocketAddress(9013));
                SocketChannel clientSocket;
                try {
                    clientSocket = serverSocket.accept();
                } catch (ClosedByInterruptException e) {
                    // End without having done anything
                    return;
                }

                try (Writer out = new FileWriter("scenario.xml")) {
                    RequestBuilder requestBuilder = new XmlTestScenario(out);


//                    ServerSocket serverSocket = new ServerSocket(9011);

                    Request request = null;

//                    Socket clientSocket = serverSocket.accept();

                    running = true;
                    while (running) {
                        try {
                            SocketChannel appSocket = SocketChannel.open(new InetSocketAddress("localhost", 9000));
//                            Socket appSocket = new Socket(InetAddress.getLocalHost(), 9000);
                            System.out.println("Client -> App");
                            request = transfer(clientSocket, appSocket, requestBuilder,
                                    RequestBuilder::request);
                            System.out.println("App -> Client");
                            requestBuilder = transfer(appSocket, clientSocket, request,
                                    (builder, urlPath, method, headers, payload) -> {
                                        Assertion a = null;
                                        AssertionBuilder ab = builder;
                                        for (Element input : Jsoup.parse(payload).select("input")) {
                                            a = ab.assertion(input.val(), String.format("input[name=%s]", input.attr("name")));
                                            ab = a;
                                        }
                                        return a != null ? a : builder;
                                    });

                        } catch (InterruptedException | ClosedByInterruptException e) {
                            // Let the thread end...
                            System.out.println("Interrupted");
                        }
                    }

                    if (request != null) {
                        request.end();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            System.out.println("End Thread");
        }, "Recorder");
    }

    public void stop() {

    }


    private static final Pattern REQUEST_LINE_PATTERN = Pattern.compile("(\\H+)\\h*(\\H+).*");
    private static final Pattern HEADER_PATTERN = Pattern.compile("(\\H+)\\h*:\\h*(.+)");
    private static final int HEADER_END_SEQ = 0x0d0a0d0a;

    interface BuildAction<I, O> {
        O build(I builder, String urlPath, String method, Map<String, String> headers, String payload) throws Exception;
    }

    private static void transfer(InputStream in, OutputStream out) throws Exception {
        ByteArrayOutputStream headerBuffer = new ByteArrayOutputStream();
        int endSeq = 0;
        do {
            int b = in.read();
            endSeq = (endSeq << 8) + (byte) b;
            headerBuffer.write(b);
            out.write(b);
        } while (endSeq != HEADER_END_SEQ);

        BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(headerBuffer.toByteArray())));

        String method;
        String urlPath;
        String requestLine = reader.readLine();
        Matcher requestLineMatcher = REQUEST_LINE_PATTERN.matcher(requestLine);
        if (requestLineMatcher.matches()) {
            method = requestLineMatcher.group(1);
            urlPath = requestLineMatcher.group(2);
        } else {
            throw new Exception(String.format("Request line %s is not understood.", requestLine));
        }

        Map<String, String> headers = new HashMap<>();
        String headerLine;
        while ((headerLine = reader.readLine()) != null) {
            System.out.println(headerLine);
            if (!headerLine.isEmpty()) {
                Matcher matcher = HEADER_PATTERN.matcher(headerLine);
                if (matcher.matches()) {
                    headers.put(matcher.group(1), matcher.group(2));
                } else {
                    throw new Exception(String.format("Header line %s is not understood.", headerLine));
                }
            }
        }


        int contentLength = headers.entrySet().stream()
                .filter(e -> e.getKey().toLowerCase().equals("content-length"))
                .findFirst()
                .map(e -> Integer.parseInt(e.getValue()))
                .orElse(0);

        String payloadText;
        if (contentLength > 0) {
            System.out.println("Read payload of length " + contentLength);
            byte[] payload = new byte[contentLength];
            in.read(payload);
            payloadText = new String(payload);
            out.write(payload);
        } else {
            payloadText = "";
        }

        boolean isHtml = headers.entrySet().stream()
                .filter(e -> e.getKey().toLowerCase().equals("content-length"))
                .findFirst()
                .map(e -> e.getValue().startsWith("text/html"))
                .orElse(false);

    }

    private static <I, O> O transfer(SocketChannel in, SocketChannel out, I requestBuilder, BuildAction<I, O> buildAction) throws Exception {
        ByteArrayOutputStream headerBuffer = new ByteArrayOutputStream();
        int endSeq = 0;
        ByteBuffer singleByte = ByteBuffer.allocate(1);
        do {
            in.read(singleByte);
            endSeq = (endSeq << 8) + singleByte.get(0);
            headerBuffer.write(singleByte.get(0));
            out.write(ByteBuffer.wrap(singleByte.array()));
            singleByte.clear();
        } while (endSeq != HEADER_END_SEQ);

        BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(headerBuffer.toByteArray())));

        String method;
        String urlPath;
        String requestLine = reader.readLine();
        Matcher requestLineMatcher = REQUEST_LINE_PATTERN.matcher(requestLine);
        if (requestLineMatcher.matches()) {
            method = requestLineMatcher.group(1);
            urlPath = requestLineMatcher.group(2);
        } else {
            throw new Exception(String.format("Request line %s is not understood.", requestLine));
        }

        Map<String, String> headers = new HashMap<>();
        String headerLine;
        while ((headerLine = reader.readLine()) != null) {
            System.out.println(headerLine);
            if (!headerLine.isEmpty()) {
                Matcher matcher = HEADER_PATTERN.matcher(headerLine);
                if (matcher.matches()) {
                    headers.put(matcher.group(1), matcher.group(2));
                } else {
                    throw new Exception(String.format("Header line %s is not understood.", headerLine));
                }
            }
        }


        int contentLength = headers.entrySet().stream()
                .filter(e -> e.getKey().toLowerCase().equals("content-length"))
                .findFirst()
                .map(e -> Integer.parseInt(e.getValue()))
                .orElse(0);

        String payloadText;
        if (contentLength > 0) {
            System.out.println("Read payload of length " + contentLength);
            ByteBuffer payload = ByteBuffer.allocate(contentLength);
            in.read(payload);
            payloadText = new String(payload.array());
            out.write(ByteBuffer.wrap(payload.array()));
        } else {
            payloadText = "";
        }

        boolean isHtml = headers.entrySet().stream()
                .filter(e -> e.getKey().toLowerCase().equals("content-type"))
                .findFirst()
                .map(e -> e.getValue().startsWith("text/html"))
                .orElse(false);

        return buildAction.build(requestBuilder, urlPath, method, headers, isHtml ? payloadText : "");
    }
}
